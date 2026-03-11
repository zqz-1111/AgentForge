package com.aiagent.guardrails;

import com.aiagent.exception.GuardrailException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InputFilter {

    private final PromptInjectionDetector injectionDetector;

    @Value("${ai.guardrails.max-input-length:2000}")
    private int maxInputLength;

    /**
     * Validates the user input before it reaches the agent.
     * Throws GuardrailException on violation — caught by GlobalExceptionHandler.
     */
    public void validate(String input) {
        if (input == null || input.isBlank()) {
            throw new GuardrailException("Input must not be empty");
        }
        if (input.length() > maxInputLength) {
            throw new GuardrailException("Input exceeds maximum length of " + maxInputLength);
        }
        if (injectionDetector.isInjectionAttempt(input)) {
            throw new GuardrailException("Input contains disallowed content");
        }
    }
}
