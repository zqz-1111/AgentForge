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
 * Enterprise RRF (Reciprocal Rank Fusion) Ranker.
 * Merges multi-channel retrieval rankings (Dense Vector + BM25 Sparse Keyword)
 * using the standard formula:
 *
 *   RRF_Score(d) = Σ [ 1 / (k + rank_i(d)) ]
 *
 * where k = 60 (standard smoothing constant).
 * Eliminates score scale discrepancies between cosine distance [0, 1] and BM25 scores [0, +inf).
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
        private String matchType; // DENSE_ONLY, SPARSE_ONLY, HYBRID
    }

    /**
     * Fuse Dense and Sparse ranked lists using Reciprocal Rank Fusion.
     *
     * @param denseResults  Ranked list from dense vector cosine similarity (rank 1 = best)
     * @param sparseResults Ranked list from BM25 sparse keyword matching (rank 1 = best)
     * @param topK          Max items to return
     * @return Fused and re-ranked list of scored documents
     */
    public List<ScoredDocument> fuse(List<String> denseResults, List<String> sparseResults, int topK) {
        return fuseWithK(denseResults, sparseResults, topK, DEFAULT_RRF_K);
    }

    /**
     * Fuse with custom smoothing factor k.
     */
    public List<ScoredDocument> fuseWithK(List<String> denseResults, List<String> sparseResults, int topK, double k) {
        Map<String, ScoredDocument> candidateMap = new LinkedHashMap<>();

        // 1. Process Dense Vector Rankings
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

        // 2. Process Sparse Keyword Rankings
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

        // 3. Classify Match Type & Sort
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
