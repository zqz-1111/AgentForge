package com.aiagent.llm;

import java.util.function.Consumer;

/** Buffers ReAct control text and emits answer deltas once its marker arrives. */
public final class FinalAnswerAccumulator {
    private static final String MARKER = "Final Answer:";
    private final StringBuilder response = new StringBuilder();
    private final Consumer<String> output;
    private int emittedThrough = -1;
    private boolean answerStarted;

    public FinalAnswerAccumulator(Consumer<String> output) {
        this.output = output;
    }

    public synchronized void append(String delta) {
        if (delta == null || delta.isEmpty()) return;
        response.append(delta);
        if (emittedThrough < 0) {
            int marker = response.indexOf(MARKER);
            if (marker < 0) return;
            emittedThrough = marker + MARKER.length();
        }
        String next = response.substring(emittedThrough);
        emittedThrough = response.length();
        if (!answerStarted) next = next.stripLeading();
        if (!next.isEmpty()) {
            answerStarted = true;
            output.accept(next);
        }
    }

    public synchronized String response() { return response.toString(); }
    public synchronized boolean hasFinalAnswer() { return emittedThrough >= 0; }
}
