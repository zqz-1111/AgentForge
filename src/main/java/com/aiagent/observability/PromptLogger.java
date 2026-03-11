package com.aiagent.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs prompts sent to the LLM.
 * Truncates to 500 chars to avoid flooding logs with large prompts.
 * Full prompts should be sent to a dedicated audit store in production.
 */
@Slf4j
@Component
public class PromptLogger {

    private static final int MAX_LOG_LENGTH = 500;

    public void log(String prompt) {
        String truncated = prompt.length() > MAX_LOG_LENGTH
                ? prompt.substring(0, MAX_LOG_LENGTH) + "...[truncated]"
                : prompt;
        log.debug("LLM prompt: {}", truncated);
    }
}
