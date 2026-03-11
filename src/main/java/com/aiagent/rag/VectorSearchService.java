package com.aiagent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;

    @Value("${ai.rag.top-k:5}")
    private int topK;

    public List<String> search(Embedding queryEmbedding) {
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.findRelevant(queryEmbedding, topK);

        return matches.stream()
                .map(match -> match.embedded().text())
                .toList();
    }

    public void store(Embedding embedding, TextSegment segment) {
        embeddingStore.add(embedding, segment);
    }
}
