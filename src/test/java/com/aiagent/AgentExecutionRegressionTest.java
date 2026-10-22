package com.aiagent;

import com.aiagent.agent.runtime.ReActLoopExecutor;
import com.aiagent.agent.runtime.StreamingReActExecutor;
import com.aiagent.dto.StreamEvent;
import com.aiagent.guardrails.ResponseValidator;
import com.aiagent.llm.LLMClient;
import com.aiagent.llm.StreamingResponseReader;
import com.aiagent.observability.AgentTraceLogger;
import com.aiagent.observability.TokenMetrics;
import com.aiagent.tools.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentExecutionRegressionTest {
    StreamingReActExecutor executor(StreamingResponseReader reader, int limit) {
        var executor = new StreamingReActExecutor(reader, mock(ResponseValidator.class), mock(ToolExecutor.class),
                mock(AgentTraceLogger.class), mock(TokenMetrics.class));
        ReflectionTestUtils.setField(executor, "maxOutputLength", limit);
        return executor;
    }

    @Test void streamingAnswerIsNotRepeatedOrCountedTwice() {
        StreamingResponseReader reader = mock(StreamingResponseReader.class);
        String response = "Thought: fixture\nFinal Answer: hello world";
        doAnswer(inv -> {
            Consumer<String> output = inv.getArgument(2);
            output.accept("hello "); output.accept("world"); return response;
        }).when(reader).read(anyString(), anyString(), any());
        List<StreamEvent> events = new ArrayList<>();
        assertEquals("hello world", executor(reader, 5000).executeWithEvents("q", "c", 5, events::add));
        assertEquals("hello world", events.stream().filter(e -> e.getType() == StreamEvent.EventType.TOKEN)
                .map(StreamEvent::getContent).reduce("", String::concat));
        var done = events.stream().filter(e -> e.getType() == StreamEvent.EventType.DONE).toList();
        assertEquals(1, done.size());
        assertEquals(response.length() / 4, done.getFirst().getTotalTokens());
    }

    @Test void failedStreamNeverEmitsSuccessTerminal() {
        StreamingResponseReader reader = mock(StreamingResponseReader.class);
        when(reader.read(anyString(), anyString(), any())).thenThrow(new IllegalStateException("provider failed"));
        List<StreamEvent> events = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> executor(reader, 5000).executeWithEvents("q", "c", 5, events::add));
        assertFalse(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.DONE));
    }

    @Test void oversizedDeltaIsRejectedBeforeEmission() {
        StreamingResponseReader reader = mock(StreamingResponseReader.class);
        doAnswer(inv -> { Consumer<String> output = inv.getArgument(2); output.accept("too long"); return ""; })
                .when(reader).read(anyString(), anyString(), any());
        List<StreamEvent> events = new ArrayList<>();
        assertThrows(com.aiagent.exception.GuardrailException.class,
                () -> executor(reader, 3).executeWithEvents("q", "c", 5, events::add));
        assertFalse(events.stream().anyMatch(e -> e.getType() == StreamEvent.EventType.TOKEN || e.getType() == StreamEvent.EventType.DONE));
    }

    @Test void blockingLoopReportsActualIterationsAndRetainsToolDecision() {
        LLMClient client = mock(LLMClient.class);
        when(client.generate(anyString())).thenReturn("Thought: compute\nAction: calculate\nAction Input: 2+2", "Final Answer: 4");
        ToolExecutor tools = mock(ToolExecutor.class);
        when(tools.execute("calculate", "2+2")).thenReturn("4");
        var loop = new ReActLoopExecutor(client, tools, mock(AgentTraceLogger.class), mock(TokenMetrics.class));
        var result = loop.executeWithResult("q", 5);
        assertEquals("4", result.answer()); assertEquals(2, result.iterationsUsed());
        verify(client).generate(contains("Action Input: 2+2\nObservation: 4"));
    }
}
