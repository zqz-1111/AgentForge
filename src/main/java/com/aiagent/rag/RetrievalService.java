package com.aiagent.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 检索的统一入口。
 * 委托 HybridRetrievalService 执行稠密向量检索、BM25 稀疏检索和 RRF 融合。
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final HybridRetrievalService hybridRetrievalService;

    public List<String> retrieve(String query) {
        return hybridRetrievalService.retrieve(query);
    }
    public List<String> retrieve(String query, int topK) {
        return hybridRetrievalService.retrieve(query, topK);
    }
}
