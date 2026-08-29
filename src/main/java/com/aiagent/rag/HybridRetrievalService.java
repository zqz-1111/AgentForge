package com.aiagent.rag;

import com.aiagent.rag.RrfFusionRanker.ScoredDocument;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enterprise Hybrid Retrieval Service.
 * Orchestrates:
 * 1. Dense Vector Retrieval (Embedding + Cosine Distance via VectorSearchService)
 * 2. Sparse Keyword Retrieval (Okapi BM25 via Bm25KeywordSearchService)
 * 3. Multi-Channel Ranking Fusion (Reciprocal Rank Fusion RRF)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final Bm25KeywordSearchService bm25Service;
    private final RrfFusionRanker rrfRanker;

    @Value("${ai.rag.top-k:5}")
    private int topK;

    /**
     * Perform dual-channel hybrid retrieval with RRF ranking fusion.
     *
     * @param query Search query text
     * @return Top-K relevant document texts
     */
    public List<String> retrieve(String query) {
        return retrieve(query, topK);
    }

    /**
     * Perform dual-channel hybrid retrieval with custom Top-K.
     *
     * @param query Search query text
     * @param k     Target count of documents to return
     * @return Top-K relevant document texts
     */
    public List<String> retrieve(String query, int k) {
        List<ScoredDocument> scored = retrieveWithScores(query, k);
        return scored.stream()
                .map(ScoredDocument::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * Retrieve documents with detailed RRF scores and match provenance.
     */
    public List<ScoredDocument> retrieveWithScores(String query, int k) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Channel A: Dense Vector Retrieval
        List<String> denseResults;
        try {
            Embedding queryEmbedding = embeddingService.embed(query);
            denseResults = vectorSearchService.search(queryEmbedding);
        } catch (Exception e) {
            log.warn("Dense vector search failed, falling back to pure BM25: {}", e.getMessage());
            denseResults = Collections.emptyList();
        }

        // 2. Channel B: Sparse BM25 Keyword Retrieval
        List<String> sparseResults;
        try {
            sparseResults = bm25Service.search(query, k * 2);
        } catch (Exception e) {
            log.warn("BM25 sparse search failed: {}", e.getMessage());
            sparseResults = Collections.emptyList();
        }

        // 3. Reciprocal Rank Fusion (RRF)
        List<ScoredDocument> fused = rrfRanker.fuse(denseResults, sparseResults, k);
        log.info("Hybrid Retrieval for '{}': dense={}, sparse={}, fused={}",
                query, denseResults.size(), sparseResults.size(), fused.size());

        return fused;
    }
}
