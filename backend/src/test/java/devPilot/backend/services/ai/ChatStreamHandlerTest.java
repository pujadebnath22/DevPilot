package devPilot.backend.services.ai;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.CitationDto;
import devPilot.backend.entity.ChatMessage;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Drives {@link ChatStreamHandler} through a real Spring MVC async SSE response.
 * The chat model is a Mockito stub (no Gemini calls); this verifies the SSE event
 * protocol the frontend relies on (user_message, token, assistant_message, done, error).
 */
class ChatStreamHandlerTest {

    private final UUID sessionId = UUID.randomUUID();
    private Flux<ChatResponse> modelOutput = Flux.empty();
    private ChatMessageRepository messages;
    private MockMvc mvc;

    @RestController
    class StreamController {
        private final ChatStreamHandler handler;

        StreamController(ChatStreamHandler handler) {
            this.handler = handler;
        }

        @PostMapping("/stream")
        SseEmitter stream() {
            ChatMessageResponse user = new ChatMessageResponse(
                    UUID.randomUUID(), MessageRole.USER, "q", List.of(), Instant.now());
            return handler.stream(sessionId, user,
                    List.of(new CitationDto("src/A.java", null, null, "java")), "sys", "usr");
        }
    }

    @BeforeEach
    void setUp() {
        // Minimal stand-in for Gemini: streams whatever the test configured.
        ChatModel chatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return modelOutput;
            }
        };
        messages = mock(ChatMessageRepository.class);
        when(messages.save(any(ChatMessage.class))).thenAnswer(inv -> {
            ChatMessage m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setCreatedAt(Instant.now());
            return m;
        });
        ChatStreamHandler handler = new ChatStreamHandler(
                chatModel, messages, new CitationMapper(JsonMapper.builder().build()), JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new StreamController(handler)).build();
    }

    private static ChatResponse chunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private String runStream() throws Exception {
        MvcResult started = mvc.perform(post("/stream")).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void streamsTokensThenPersistsAssistantMessageWithCitations() throws Exception {
        modelOutput = Flux.just(chunk("Hello"), chunk(" world\nline2"));

        String body = runStream();

        assertThat(body).contains("event:user_message");
        // Each token is a JSON string on a single data line (newline escaped, leading space kept).
        assertThat(body).contains("event:token\ndata:\"Hello\"\n")
                .contains("event:token\ndata:\" world\\nline2\"\n");
        assertThat(body).contains("event:assistant_message").contains("src/A.java");
        assertThat(body).contains("event:done");
        assertThat(body).doesNotContain("event:error");

        ArgumentCaptor<ChatMessage> saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messages).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(saved.getValue().getContent()).isEqualTo("Hello world\nline2");
        assertThat(saved.getValue().getSessionId()).isEqualTo(sessionId);
    }

    @Test
    void modelFailureEmitsErrorEventAndDoesNotPersistReply() throws Exception {
        modelOutput = Flux.concat(Flux.just(chunk("partial")), Flux.error(new RuntimeException("quota exceeded")));

        String body = runStream();

        assertThat(body).contains("event:error").contains("The AI model failed to generate a response");
        assertThat(body).doesNotContain("quota exceeded");
        assertThat(body).doesNotContain("event:done");
        verify(messages, never()).save(any());
    }
}
