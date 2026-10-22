package com.aiagent.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class StreamingResponseReader {
    private final ModelRouter modelRouter;
    private final LLMClient llmClient;
    @Value("${ai.openai.timeout-seconds:30}")
    private long timeoutSeconds;

    public String read(String query, String context, Consumer<String> output) {
        StreamingChatLanguageModel model = modelRouter.routeStreaming(query);
        if (model == null) return llmClient.generate(context); // Honest blocking fallback.
        FinalAnswerAccumulator accumulator = new FinalAnswerAccumulator(output);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Object gate = new Object();
        boolean[] active = {true};
        try {
            model.generate(context, new StreamingResponseHandler<AiMessage>() {
                @Override public void onNext(String token) {
                    synchronized (gate) {
                        if (active[0]) accumulator.append(token);
                    }
                }
                @Override public void onComplete(Response<AiMessage> result) {
                    synchronized (gate) { active[0] = false; done.countDown(); }
                }
                @Override public void onError(Throwable error) {
                    synchronized (gate) {
                        if (active[0]) failure.set(error);
                        active[0] = false;
                        done.countDown();
                    }
                }
            });
            if (!done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Model stream timed out");
            }
            if (failure.get() != null) throw new IllegalStateException("Model stream failed", failure.get());
            return accumulator.response();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Model stream interrupted", ex);
        } finally {
            // SDK 0.31 does not expose request cancellation here; ignore late callbacks.
            synchronized (gate) { active[0] = false; }
        }
    }
}
