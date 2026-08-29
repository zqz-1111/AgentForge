package com.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * High-performance Okapi BM25 Sparse Keyword Search Service.
 * Implements standard BM25 formula:
 *
 *   Score(D, Q) = Σ IDF(q) * [ (f(q, D) * (k1 + 1)) / (f(q, D) + k1 * (1 - b + b * (|D| / avgdl))) ]
 *
 * where k1 = 1.5, b = 0.75.
 * Provides exact keyword, error code, and code identifier matching.
 */
@Slf4j
@Service
public class Bm25KeywordSearchService {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    // In-memory document repository for fast retrieval
    private final List<String> documents = new CopyOnWriteArrayList<>();

    public void addDocument(String doc) {
        if (doc != null && !doc.isBlank()) {
            documents.add(doc);
        }
    }

    public void addDocuments(Collection<String> docs) {
        if (docs != null) {
            for (String d : docs) {
                addDocument(d);
            }
        }
    }

    public void clear() {
        documents.clear();
    }

    /**
     * Search documents using Okapi BM25.
     *
     * @param query Search query text
     * @param topK  Maximum results to return
     * @return List of matched document texts ranked by BM25 score descending
     */
    public List<String> search(String query, int topK) {
        if (query == null || query.isBlank() || documents.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        int N = documents.size();
        List<List<String>> tokenizedDocs = new ArrayList<>(N);
        int totalDocLength = 0;

        for (String doc : documents) {
            List<String> tokens = tokenize(doc);
            tokenizedDocs.add(tokens);
            totalDocLength += tokens.size();
        }

        double avgdl = (double) totalDocLength / Math.max(1, N);

        // 1. Calculate Document Frequency (DF) for query tokens
        Map<String, Integer> docFreq = new HashMap<>();
        for (String qToken : queryTokens) {
            int count = 0;
            for (List<String> dTokens : tokenizedDocs) {
                if (dTokens.contains(qToken)) {
                    count++;
                }
            }
            docFreq.put(qToken, count);
        }

        // 2. Score each document with BM25
        List<ScoredDoc> scores = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            List<String> dTokens = tokenizedDocs.get(i);
            int docLen = dTokens.size();
            double score = 0.0;

            // Term frequency in current doc
            Map<String, Integer> termFreq = new HashMap<>();
            for (String t : dTokens) {
                termFreq.put(t, termFreq.getOrDefault(t, 0) + 1);
            }

            for (String qToken : queryTokens) {
                int df = docFreq.getOrDefault(qToken, 0);
                if (df == 0) continue;

                // Standard Lucene/Okapi IDF formula
                double idf = Math.log(1.0 + (N - df + 0.5) / (df + 0.5));
                if (idf < 0) idf = 0.01; // Avoid negative IDF for very frequent terms

                int tf = termFreq.getOrDefault(qToken, 0);
                if (tf > 0) {
                    double numerator = tf * (K1 + 1.0);
                    double denominator = tf + K1 * (1.0 - B + B * (docLen / avgdl));
                    score += idf * (numerator / denominator);
                }
            }

            if (score > 0.001) {
                scores.add(new ScoredDoc(documents.get(i), score));
            }
        }

        // 3. Sort by score descending and return Top-K
        return scores.stream()
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .limit(topK)
                .map(ScoredDoc::doc)
                .collect(Collectors.toList());
    }

    /**
     * Mixed character + whitespace tokenization supporting English words, code identifiers, and Chinese chars.
     */
    private List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();

        List<String> tokens = new ArrayList<>();
        String normalized = text.toLowerCase().replaceAll("[\\p{Punct}&&[^_]]+", " ");
        String[] words = normalized.split("\\s+");

        for (String w : words) {
            if (w.isBlank()) continue;
            tokens.add(w);
            // If word contains Chinese characters, split into individual/bi-gram characters
            if (containsChinese(w)) {
                for (int i = 0; i < w.length(); i++) {
                    tokens.add(String.valueOf(w.charAt(i)));
                }
            }
        }
        return tokens;
    }

    private boolean containsChinese(String s) {
        for (char c : s.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private record ScoredDoc(String doc, double score) {}
}
