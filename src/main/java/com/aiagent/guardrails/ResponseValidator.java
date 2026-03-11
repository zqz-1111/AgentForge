package com.aiagent.guardrails;

import com.aiagent.exception.GuardrailException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResponseValidator {

    @Value("${ai.guardrails.max-output-length:5000}")
    private int maxOutputLength;

    /**
     * Validates the LLM response before returning it to the client.
     * Throws GuardrailException on violation — caught by GlobalExceptionHandler.
     */
    public void validate(String response) {
        if (response == null || response.isBlank()) {
            throw new GuardrailException("Agent returned an empty response");
        }
        if (response.length() > maxOutputLength) {
            throw new GuardrailException("Agent response exceeds maximum allowed length");
        }
    }
}
