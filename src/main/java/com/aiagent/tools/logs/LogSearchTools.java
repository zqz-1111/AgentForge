package com.aiagent.tools.logs;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Tool: Search application logs by keyword or trace ID.
 * Replace the stub with a real log aggregation call (e.g., Elasticsearch, CloudWatch).
 */
@Component
public class LogSearchTools {

    @Tool("Search application logs by keyword or trace ID")
    public String searchLogs(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "Keyword must not be empty";
        }
        // Stub — replace with real log search
        return "No log entries found for keyword: " + keyword;
    }
}
