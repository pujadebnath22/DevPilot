package devPilot.backend.services.ai;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.CitationDto;
import devPilot.backend.entity.ChatMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.repository.ChatMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Generation step: call the configured chat model (Gemini) via Spring AI and stream tokens to the browser over SSE.
 */
@Component
@Slf4j
public class ChatStreamHandler {

    private final ChatModel chatModel;

    private final ChatMessageRepository chatMessageRepository;
    private final CitationMapper citationMapper;
    private final JsonMapper jsonMapper;

    public ChatStreamHandler(
            @Qualifier("googleGenAiChatModel") ChatModel chatModel,
            ChatMessageRepository chatMessageRepository,
            CitationMapper citationMapper,
            JsonMapper jsonMapper) {
        this.chatModel = chatModel;
        this.chatMessageRepository = chatMessageRepository;
        this.citationMapper = citationMapper;
        this.jsonMapper = jsonMapper;
    }

    public SseEmitter stream(
            UUID sessionId,
            ChatMessageResponse savedUserMessage,
            List<CitationDto> citations,
            String systemPrompt,
            String userPrompt) {

        SseEmitter emitter = new SseEmitter(RagSettings.STREAM_TIMEOUT_MS);
        StringBuilder fullReply = new StringBuilder();
        AtomicReference<Disposable> subscription = new AtomicReference<>();

        // Stop pulling tokens from the model once the client is gone or the stream timed out.
        emitter.onCompletion(() -> dispose(subscription));
        emitter.onError(err -> dispose(subscription));
        emitter.onTimeout(() -> {
            dispose(subscription);
            sendError(emitter, "The response timed out. Please try again.");
            emitter.complete();
        });

        try {
            emitter.send(SseEmitter.event()
                    .name("user_message")
                    .data(savedUserMessage));

            subscription.set(ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content()
                    .subscribe(
                            token -> appendToken(emitter, fullReply, token, subscription),
                            err -> {
                                log.error("Chat stream error for session {}", sessionId, err);
                                sendError(emitter, "The AI model failed to generate a response. Please try again.");
                                emitter.complete();
                            },
                            () -> completeStream(emitter, sessionId, fullReply, citations)));
        } catch (Exception ex) {
            log.error("Could not start chat stream for session {}", sessionId, ex);
            sendError(emitter, "Could not start the response stream.");
            emitter.complete();
        }

        return emitter;
    }

    private void appendToken(
            SseEmitter emitter,
            StringBuilder fullReply,
            String token,
            AtomicReference<Disposable> subscription) {
        fullReply.append(token);
        try {
            // Explicitly JSON-encode the token (a String would otherwise be written raw):
            // the frontend JSON.parses each token, and encoding keeps embedded newlines
            // and leading spaces intact within a single SSE data line.
            emitter.send(SseEmitter.event()
                    .name("token")
                    .data(jsonMapper.writeValueAsString(token)));
        } catch (Exception ex) {
            // Client disconnected: stop consuming the model stream.
            log.debug("Client disconnected from chat stream: {}", ex.getMessage());
            dispose(subscription);
        }
    }

    private static void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", message), MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // Client already gone; nothing else to do.
        }
    }

    private static void dispose(AtomicReference<Disposable> subscription) {
        Disposable d = subscription.get();
        if (d != null && !d.isDisposed()) {
            d.dispose();
        }
    }

    private void completeStream(
            SseEmitter emitter,
            UUID sessionId,
            StringBuilder fullReply,
            List<CitationDto> citations) {
        try {
            ChatMessage assistant = chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(sessionId)
                    .role(MessageRole.ASSISTANT)
                    .content(fullReply.toString())
                    .citations(citationMapper.toJson(citations))
                    .build());

            emitter.send(SseEmitter.event()
                    .name("assistant_message")
                    .data(toMessageResponse(assistant)));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception ex) {
            log.error("Could not persist assistant reply for session {}", sessionId, ex);
            sendError(emitter, "The response could not be saved. Please try again.");
            emitter.complete();
        }
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                citationMapper.fromJson(message.getCitations()),
                message.getCreatedAt());
    }
}
