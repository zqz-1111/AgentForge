package com.aiagent.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 倒数排名融合（RRF）排序器。
 * 合并稠密向量检索和 BM25 稀疏关键词检索的排名，
 * 使用以下标准公式。
 *
 *   公式：RRF_Score(d) = Σ [ 1 / (k + rank_i(d)) ]
 *
 * 其中 k = 60，是常用的平滑常数。
 * 用名次融合，避免直接比较余弦距离和 BM25 分数的量纲。
 */
@Slf4j
@Component
public class RrfFusionRanker {

    public static final double DEFAULT_RRF_K = 60.0;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoredDocument {
        private String document;
        private double rrfScore;
        private Integer vectorRank;
        private Integer keywordRank;
        private String matchType; // 匹配类型：DENSE_ONLY（仅向量）、SPARSE_ONLY（仅关键词）、HYBRID（两路均匹配）
    }

    /**
     * 使用倒数排名融合合并稠密与稀疏检索的结果。
     *
     * @param denseResults 按稠密向量余弦相似度排序的结果（第 1 名最佳）
     * @param sparseResults 按 BM25 稀疏关键词匹配排序的结果（第 1 名最佳）
     * @param topK 最多返回的条数
     * @return 融合并重新排序后的带分数文档列表
     */
    public List<ScoredDocument> fuse(List<String> denseResults, List<String> sparseResults, int topK) {
        return fuseWithK(denseResults, sparseResults, topK, DEFAULT_RRF_K);
    }

    /**
     * 使用自定义平滑系数 k 执行融合。
     */
    public List<ScoredDocument> fuseWithK(List<String> denseResults, List<String> sparseResults, int topK, double k) {
        Map<String, ScoredDocument> candidateMap = new LinkedHashMap<>();

        // 1. 处理稠密向量检索的排名
        if (denseResults != null) {
            for (int i = 0; i < denseResults.size(); i++) {
                String doc = denseResults.get(i);
                int rank = i + 1;
                double score = 1.0 / (k + rank);

                ScoredDocument entry = candidateMap.computeIfAbsent(doc, d -> ScoredDocument.builder()
                        .document(d)
                        .rrfScore(0.0)
                        .build());
                entry.setVectorRank(rank);
                entry.setRrfScore(entry.getRrfScore() + score);
            }
        }

        // 2. 处理稀疏关键词检索的排名
        if (sparseResults != null) {
            for (int i = 0; i < sparseResults.size(); i++) {
                String doc = sparseResults.get(i);
                int rank = i + 1;
                double score = 1.0 / (k + rank);

                ScoredDocument entry = candidateMap.computeIfAbsent(doc, d -> ScoredDocument.builder()
                        .document(d)
                        .rrfScore(0.0)
                        .build());
                entry.setKeywordRank(rank);
                entry.setRrfScore(entry.getRrfScore() + score);
            }
        }

        // 3. 标注匹配类型并排序
        for (ScoredDocument doc : candidateMap.values()) {
            if (doc.getVectorRank() != null && doc.getKeywordRank() != null) {
                doc.setMatchType("HYBRID");
            } else if (doc.getVectorRank() != null) {
                doc.setMatchType("DENSE_ONLY");
            } else {
                doc.setMatchType("SPARSE_ONLY");
            }
        }

        List<ScoredDocument> sorted = candidateMap.values().stream()
                .sorted(Comparator.comparingDouble(ScoredDocument::getRrfScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        log.debug("RRF Fusion: merged {} dense and {} sparse candidates into top-{} results",
                denseResults != null ? denseResults.size() : 0,
                sparseResults != null ? sparseResults.size() : 0,
                sorted.size());

        return sorted;
    }
}
