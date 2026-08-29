package com.aiagent.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * High-level Retrieval Entrypoint.
 * Automatically delegates to HybridRetrievalService (Dense Vector + BM25 Sparse + RRF).
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final HybridRetrievalService hybridRetrievalService;

    public List<String> retrieve(String query) {
        return hybridRetrievalService.retrieve(query);
    }
}
