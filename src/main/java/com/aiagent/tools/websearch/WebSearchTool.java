package com.aiagent.tools.websearch;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 使用 DuckDuckGo 即时答案 API 进行网页搜索的工具。
 * 无需 API 密钥，返回即时答案和相关主题。
 */
@Slf4j
@Component
public class WebSearchTool {

    private final RestClient restClient;

    public WebSearchTool() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.duckduckgo.com")
                .build();
    }

    @Tool("Search the web for information. Returns relevant snippets and related topics.")
    public String search(String query) {
        if (query == null || query.isBlank()) {
            return "Error: search query is required";
        }

        try {
            String response = restClient.get()
                    .uri("/?q={query}&format=json&no_html=1&skip_disambig=1", query.trim())
                    .retrieve()
                    .body(String.class);

            return parseResponse(response, query);
        } catch (Exception e) {
            log.warn("Web search failed for '{}': {}", query, e.getMessage());
            return "Search temporarily unavailable. Please try again later.";
        }
    }

    private String parseResponse(String json, String query) {
        // 用简单方式解析 JSON，无需额外依赖
        StringBuilder result = new StringBuilder();
        result.append("Search results for: ").append(query).append("\n\n");

        // 提取摘要字段 AbstractText
        String abstractText = extractJsonField(json, "AbstractText");
        if (abstractText != null && !abstractText.isEmpty()) {
            result.append("Summary: ").append(abstractText).append("\n\n");
        }

        // 提取摘要来源字段 AbstractSource
        String source = extractJsonField(json, "AbstractSource");
        if (source != null && !source.isEmpty()) {
            result.append("Source: ").append(source).append("\n");
        }

        // 提取摘要链接字段 AbstractURL
        String url = extractJsonField(json, "AbstractURL");
        if (url != null && !url.isEmpty()) {
            result.append("URL: ").append(url).append("\n\n");
        }

        // 提取即时答案字段 Answer
        String answer = extractJsonField(json, "Answer");
        if (answer != null && !answer.isEmpty()) {
            result.append("Answer: ").append(answer).append("\n");
        }

        if (result.length() < 50) {
            result.append("No direct answer found. Try rephrasing your query.");
        }

        return result.toString();
    }

    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        String value = json.substring(start, end);
        return value.isEmpty() ? null : value;
    }
}
