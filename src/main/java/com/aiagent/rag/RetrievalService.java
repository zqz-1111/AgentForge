package com.aiagent.rag;

import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;

    public List<String> retrieve(String query) {
        Embedding embedding = embeddingService.embed(query);
        return vectorSearchService.search(embedding);
    }
}
