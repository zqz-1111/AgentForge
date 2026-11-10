package com.aiagent.guardrails;

import com.aiagent.exception.GuardrailException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResponseValidator {

    @Value("${ai.guardrails.max-output-length:5000}")
    private int maxOutputLength;

    /**
     * 大语言模型的回答返回客户端之前先校验。
     * 校验不通过时抛出 GuardrailException，由 GlobalExceptionHandler 处理。
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
