package com.aiagent.agent.runtime;

import com.aiagent.memory.ConversationMemory;
import com.aiagent.rag.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 组装初始提示词上下文，包含以下内容。
 * - 从记忆中读取的对话历史
 * - 通过 RAG 检索到的相关文档
 * - 用户问题
 */
@Component
@RequiredArgsConstructor
public class AgentPlanner {

    private final ConversationMemory conversationMemory;
    private final RetrievalService retrievalService;

    public String buildContext(String sessionId, String query) {
        List<String> history = conversationMemory.getHistory(sessionId);
        List<String> ragDocs  = retrievalService.retrieve(query);

        StringBuilder context = new StringBuilder();

        if (!ragDocs.isEmpty()) {
            context.append("Relevant context:\n");
            ragDocs.forEach(doc -> context.append("- ").append(doc).append("\n"));
            context.append("\n");
        }

        if (!history.isEmpty()) {
            context.append("Conversation history:\n");
            history.forEach(msg -> context.append(msg).append("\n"));
            context.append("\n");
        }

        context.append("User query: ").append(query);
        return context.toString();
    }
}
