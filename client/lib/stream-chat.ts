import { getApiBaseUrl, ApiError, type ChatMessage } from "@/lib/api";
import { createSseParser } from "@/lib/sse";

export type StreamChatHandlers = {
  onUserMessage?: (message: ChatMessage) => void;
  onToken?: (token: string) => void;
  onAssistantMessage?: (message: ChatMessage) => void;
  onDone?: () => void;
  signal?: AbortSignal;
};

/**
 * POSTs a chat message and consumes the backend's SSE reply.
 * Events: user_message, token (JSON string), assistant_message, done, error.
 * Rejects with an Error if the server reports an `error` event, the response is
 * not OK, or the stream ends before an assistant message arrives.
 */
export async function streamChatMessage(
  sessionId: string,
  content: string,
  handlers: StreamChatHandlers = {}
): Promise<void> {
  const res = await fetch(
    `${getApiBaseUrl()}/api/chat/sessions/${encodeURIComponent(sessionId)}/messages`,
    {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        Accept: "text/event-stream",
      },
      body: JSON.stringify({ content }),
      signal: handlers.signal,
    }
  );

  if (!res.ok) {
    let message = res.statusText || "Request failed";
    try {
      const data = await res.json();
      message = data.message ?? data.error ?? message;
    } catch {
      // Non-JSON error body; keep the status text.
    }
    throw new ApiError(res.status, message);
  }

  if (!res.body) {
    throw new Error("No response body for SSE stream");
  }

  let serverError: string | null = null;
  let completed = false;

  const parser = createSseParser(({ event, data }) => {
    switch (event) {
      case "token":
        handlers.onToken?.(JSON.parse(data) as string);
        break;
      case "user_message":
        handlers.onUserMessage?.(JSON.parse(data) as ChatMessage);
        break;
      case "assistant_message":
        completed = true;
        handlers.onAssistantMessage?.(JSON.parse(data) as ChatMessage);
        break;
      case "error": {
        try {
          serverError = (JSON.parse(data) as { message?: string }).message ?? data;
        } catch {
          serverError = data;
        }
        break;
      }
      case "done":
        completed = true;
        break;
    }
  });

  const reader = res.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    parser.push(decoder.decode(value, { stream: true }));
  }
  parser.push(decoder.decode());
  parser.flush();

  if (serverError) {
    throw new Error(serverError);
  }
  if (!completed) {
    throw new Error("The response stream ended unexpectedly. Please try again.");
  }

  handlers.onDone?.();
}
