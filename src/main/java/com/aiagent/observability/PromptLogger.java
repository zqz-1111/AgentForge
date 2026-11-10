package com.aiagent.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 记录发给大语言模型的提示词。
 * 截断到 500 个字符，避免长提示词淹没日志。
 * 生产环境应把完整提示词发送到专用审计存储。
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
