package com.aiagent.tools.logs;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 按关键词或追踪 ID 搜索应用日志。
 * 可用真实日志聚合调用替换占位实现，例如 Elasticsearch 或 CloudWatch。
 */
@Component
public class LogSearchTools {

    @Tool("Search application logs by keyword or trace ID")
    public String searchLogs(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "Keyword must not be empty";
        }
        // 占位实现，可替换为真实日志查询
        return "No log entries found for keyword: " + keyword;
    }
}
