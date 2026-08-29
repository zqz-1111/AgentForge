package com.aiagent.agent.orchestrator;

import com.aiagent.rag.HybridRetrievalService;
import com.aiagent.tools.websearch.WebSearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Specialized Worker Agent: Research & Knowledge Retrieval.
 * Handles document RAG retrieval and real-time web exploration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResearchAgent implements SubAgent {

    private final HybridRetrievalService hybridRetrievalService;
    private final WebSearchTool webSearchTool;

    @Override
    public String getName() {
        return "ResearchAgent";
    }

    @Override
    public String getDescription() {
        return "Specialized in hybrid knowledge base retrieval (Dense+BM25) and real-time web search.";
    }

    @Override
    public boolean canHandle(String taskDescription) {
        if (taskDescription == null) return false;
        String lower = taskDescription.toLowerCase();
        return lower.contains("search") || lower.contains("find") || lower.contains("查")
                || lower.contains("搜索") || lower.contains("知识库") || lower.contains("资料")
                || lower.contains("最新") || lower.contains("文档");
    }

    @Override
    public String execute(String taskDescription, String context) {
        log.info("[ResearchAgent] Executing research task: {}", taskDescription);
        StringBuilder output = new StringBuilder();

        // 1. Query internal knowledge base via Hybrid Search
        List<String> kbDocs = hybridRetrievalService.retrieve(taskDescription, 3);
        if (!kbDocs.isEmpty()) {
            output.append("### 【知识库召回依据】\n");
            for (int i = 0; i < kbDocs.size(); i++) {
                output.append(i + 1).append(". ").append(kbDocs.get(i)).append("\n");
            }
            output.append("\n");
        }

        // 2. Query web search if needed
        String webResult = webSearchTool.search(taskDescription);
        if (webResult != null && !webResult.isBlank() && !webResult.contains("Error")) {
            output.append("### 【全网实时检索结果】\n").append(webResult).append("\n");
        }

        if (output.length() == 0) {
            return "未检索到与 '" + taskDescription + "' 直接相关的知识库条目或网络资讯。";
        }

        return output.toString();
    }
}
