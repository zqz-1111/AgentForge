package com.aiagent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.rag.top-k:5}")
    private int topK;

    public List<String> search(Embedding queryEmbedding) {
        return search(queryEmbedding, topK);
    }

    public List<String> search(Embedding queryEmbedding, int k) {
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.findRelevant(queryEmbedding, k);

        return matches.stream()
                .map(match -> match.embedded().text())
                .toList();
    }

    public void store(Embedding embedding, TextSegment segment, String documentId) {
        try {
            // Use Spring's connection: vector rows and document metadata share one transaction.
            jdbcTemplate.update("INSERT INTO agent_embeddings(embedding_id, embedding, text, metadata) "
                    + "VALUES (?::uuid, ?::vector, ?, ?::json)", UUID.randomUUID().toString(),
                    java.util.Arrays.toString(embedding.vector()), segment.text(),
                    objectMapper.writeValueAsString(Map.of("documentId", documentId)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public List<String> keywordSearch(String query, int k) {
        // Durable source, request-local snapshot: survives restarts and concurrent deletions.
        Bm25KeywordSearchService snapshot = new Bm25KeywordSearchService();
        snapshot.addDocuments(jdbcTemplate.queryForList(
                "SELECT text FROM agent_embeddings WHERE text IS NOT NULL ORDER BY embedding_id", String.class));
        return snapshot.search(query, k);
    }

    public void deleteDocument(String documentId) {
        jdbcTemplate.update("DELETE FROM agent_embeddings WHERE metadata->>'documentId' = ?", documentId);
    }
}
