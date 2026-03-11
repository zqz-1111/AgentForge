package com.aiagent.agent.runtime;

import com.aiagent.memory.ConversationMemory;
import com.aiagent.rag.RetrievalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the initial prompt context by combining:
 * - Conversation history from memory
 * - Relevant documents from RAG
 * - The user query
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
