/**
 * Minimal incremental parser for `text/event-stream` responses read via fetch().
 * (EventSource can't be used because chat messages are sent with POST + JSON body.)
 *
 * Kept dependency-free so it can be unit-tested with `node --test`.
 */
export type SseEvent = {
  event: string;
  data: string;
};

export function createSseParser(onEvent: (event: SseEvent) => void) {
  let buffer = "";

  function dispatch(block: string) {
    if (!block.trim()) return;

    let event = "message";
    const dataLines: string[] = [];

    for (const line of block.split("\n")) {
      if (line.startsWith("event:")) {
        event = line.slice(6).trim();
      } else if (line.startsWith("data:")) {
        // Per the SSE spec only a single leading space is stripped.
        const value = line.slice(5);
        dataLines.push(value.startsWith(" ") ? value.slice(1) : value);
      }
    }

    if (dataLines.length === 0) return;
    onEvent({ event, data: dataLines.join("\n") });
  }

  return {
    push(chunk: string) {
      buffer += chunk.replace(/\r\n?/g, "\n");
      const blocks = buffer.split("\n\n");
      buffer = blocks.pop() ?? "";
      for (const block of blocks) dispatch(block);
    },
    /** Dispatch a trailing event that was not followed by a blank line. */
    flush() {
      const rest = buffer;
      buffer = "";
      dispatch(rest);
    },
  };
}
