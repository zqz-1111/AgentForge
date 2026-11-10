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
 * 混合检索服务。
 * 负责串联以下步骤。
 * 1. 稠密向量检索（由 VectorSearchService 使用向量和余弦距离）
 * 2. 稀疏关键词检索（由 Bm25KeywordSearchService 使用 Okapi BM25）
 * 3. 使用倒数排名融合（RRF）合并多路排名
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final RrfFusionRanker rrfRanker;

    @Value("${ai.rag.top-k:5}")
    private int topK;

    /**
     * 执行双路混合检索，使用 RRF 融合排名。
     *
     * @param query 查询文本
     * @return 最相关的前 K 条文档文本
     */
    public List<String> retrieve(String query) {
        return retrieve(query, topK);
    }

    /**
     * 执行双路混合检索，可指定返回结果的数量。
     *
     * @param query 查询文本
     * @param k 目标返回文档数
     * @return 最相关的前 K 条文档文本
     */
    public List<String> retrieve(String query, int k) {
        List<ScoredDocument> scored = retrieveWithScores(query, k);
        return scored.stream()
                .map(ScoredDocument::getDocument)
                .collect(Collectors.toList());
    }

    /**
     * 检索文档，并附带 RRF 得分和匹配来源。
     */
    public List<ScoredDocument> retrieveWithScores(String query, int k) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 通道 A：稠密向量检索
        List<String> denseResults;
        try {
            Embedding queryEmbedding = embeddingService.embed(query);
            denseResults = vectorSearchService.search(queryEmbedding, k * 2);
        } catch (Exception e) {
            log.warn("Dense vector search failed, falling back to pure BM25: {}", e.getMessage());
            denseResults = Collections.emptyList();
        }

        // 2. 通道 B：BM25 稀疏关键词检索
        List<String> sparseResults;
        try {
            sparseResults = vectorSearchService.keywordSearch(query, k * 2);
        } catch (Exception e) {
            log.warn("BM25 sparse search failed: {}", e.getMessage());
            sparseResults = Collections.emptyList();
        }

        // 3. 倒数排名融合（RRF）
        List<ScoredDocument> fused = rrfRanker.fuse(denseResults, sparseResults, k);
        log.info("Hybrid Retrieval for '{}': dense={}, sparse={}, fused={}",
                query, denseResults.size(), sparseResults.size(), fused.size());

        return fused;
    }
}
