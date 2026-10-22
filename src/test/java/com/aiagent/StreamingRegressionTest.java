package com.aiagent;

import com.aiagent.llm.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StreamingRegressionTest {
    @Test void markerSplitAcrossEveryCharacterDoesNotLeakControlText() {
        List<String> output = new ArrayList<>();
        FinalAnswerAccumulator accumulator = new FinalAnswerAccumulator(output::add);
        String text = "Thought: private control\nFinal Answer: 中文答案🙂";
        text.codePoints().forEach(cp -> accumulator.append(new String(Character.toChars(cp))));
        assertEquals("中文答案🙂", String.join("", output));
        assertEquals(text, accumulator.response());
    }

    @Test void actionRemainsBuffered() {
        List<String> output = new ArrayList<>();
        FinalAnswerAccumulator accumulator = new FinalAnswerAccumulator(output::add);
        accumulator.append("Thought: calculate\nAction: calculate\nAction Input: 2+2");
        assertTrue(output.isEmpty());
        assertFalse(accumulator.hasFinalAnswer());
    }

    @Test void tokenIsDeliveredBeforeModelCompletionAndBlockingClientIsUnused() {
        ModelRouter router = mock(ModelRouter.class);
        LLMClient client = mock(LLMClient.class);
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        when(router.routeStreaming(anyString())).thenReturn(model);
        List<String> output = new ArrayList<>();
        doAnswer(inv -> {
            StreamingResponseHandler<AiMessage> handler = inv.getArgument(1);
            handler.onNext("Final An");
            handler.onNext("swer: hello");
            assertEquals(List.of("hello"), output); // Before onComplete.
            handler.onNext(" world");
            handler.onComplete(Response.from(AiMessage.from("Final Answer: hello world")));
            return null;
        }).when(model).generate(anyString(), any(StreamingResponseHandler.class));
        StreamingResponseReader reader = new StreamingResponseReader(router, client);
        ReflectionTestUtils.setField(reader, "timeoutSeconds", 2L);
        assertEquals("Final Answer: hello world", reader.read("q", "context", output::add));
        verifyNoInteractions(client);
    }

    @Test void providerFailurePropagatesInsteadOfSuccessfulCompletion() {
        ModelRouter router = mock(ModelRouter.class);
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        when(router.routeStreaming(anyString())).thenReturn(model);
        doAnswer(inv -> {
            StreamingResponseHandler<AiMessage> handler = inv.getArgument(1);
            handler.onError(new IllegalStateException("fixture failure"));
            return null;
        }).when(model).generate(anyString(), any(StreamingResponseHandler.class));
        StreamingResponseReader reader = new StreamingResponseReader(router, mock(LLMClient.class));
        ReflectionTestUtils.setField(reader, "timeoutSeconds", 1L);
        assertThrows(IllegalStateException.class, () -> reader.read("q", "c", t -> {}));
    }

    @Test void timeoutRejectsLateTokens() {
        ModelRouter router = mock(ModelRouter.class);
        StreamingChatLanguageModel model = mock(StreamingChatLanguageModel.class);
        when(router.routeStreaming(anyString())).thenReturn(model);
        List<StreamingResponseHandler<AiMessage>> callbacks = new ArrayList<>();
        doAnswer(inv -> { callbacks.add(inv.getArgument(1)); return null; })
                .when(model).generate(anyString(), any(StreamingResponseHandler.class));
        StreamingResponseReader reader = new StreamingResponseReader(router, mock(LLMClient.class));
        ReflectionTestUtils.setField(reader, "timeoutSeconds", 0L);
        List<String> output = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> reader.read("q", "c", output::add));
        callbacks.getFirst().onNext("Final Answer: too late");
        assertTrue(output.isEmpty());
    }

    @Test void blockingFallbackDoesNotPretendToStream() {
        ModelRouter router = mock(ModelRouter.class);
        LLMClient client = mock(LLMClient.class);
        when(client.generate("context")).thenReturn("Final Answer: fallback");
        List<String> output = new ArrayList<>();
        assertEquals("Final Answer: fallback", new StreamingResponseReader(router, client)
                .read("q", "context", output::add));
        assertTrue(output.isEmpty());
    }
}
