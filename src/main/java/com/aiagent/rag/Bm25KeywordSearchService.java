package com.aiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 基于 Okapi BM25 的稀疏关键词检索服务。
 * 使用标准 BM25 公式。
 *
 *   公式：Score(D, Q) = Σ IDF(q) * [ (f(q, D) * (k1 + 1)) / (f(q, D) + k1 * (1 - b + b * (|D| / avgdl))) ]
 *
 * 其中 k1 = 1.5，b = 0.75。
 * 支持精确匹配关键词、错误码和代码标识符。
 */
@Slf4j
@Service
public class Bm25KeywordSearchService {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    // 将文档保存在内存中，便于快速检索
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
     * 使用 Okapi BM25 检索文档。
     *
     * @param query 查询文本
     * @param topK 最多返回的结果数
     * @return 按 BM25 得分从高到低排列的匹配文档文本
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

        // 1. 统计查询词的文档频率（DF）
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

        // 2. 用 BM25 给每篇文档打分
        List<ScoredDoc> scores = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            List<String> dTokens = tokenizedDocs.get(i);
            int docLen = dTokens.size();
            double score = 0.0;

            // 词项在当前文档中的出现次数
            Map<String, Integer> termFreq = new HashMap<>();
            for (String t : dTokens) {
                termFreq.put(t, termFreq.getOrDefault(t, 0) + 1);
            }

            for (String qToken : queryTokens) {
                int df = docFreq.getOrDefault(qToken, 0);
                if (df == 0) continue;

                // 标准 Lucene/Okapi IDF 公式
                double idf = Math.log(1.0 + (N - df + 0.5) / (df + 0.5));
                if (idf < 0) idf = 0.01; // 避免高频词的 IDF 变成负数

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

        // 3. 按得分从高到低排序并返回前 K 条
        return scores.stream()
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .limit(topK)
                .map(ScoredDoc::doc)
                .collect(Collectors.toList());
    }

    /**
     * 结合字符和空白分词，支持英文单词、代码标识符和中文字符。
     */
    private List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();

        List<String> tokens = new ArrayList<>();
        String normalized = text.toLowerCase().replaceAll("[\\p{Punct}&&[^_]]+", " ");
        String[] words = normalized.split("\\s+");

        for (String w : words) {
            if (w.isBlank()) continue;
            tokens.add(w);
            // 词中含中文字符时，拆成单字和相邻双字
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
