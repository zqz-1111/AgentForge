package com.aiagent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Splits raw documents into chunks and stores embeddings in the vector store.
 * Called via POST /ai/ingest.
 */
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;

    @Value("${ai.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${ai.rag.chunk-overlap:50}")
    private int chunkOverlap;

    public void ingestDocument(String rawText) {
        Document document = Document.from(rawText);
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = splitter.split(document);

        for (TextSegment segment : segments) {
            Embedding embedding = embeddingService.embed(segment.text());
            vectorSearchService.store(embedding, segment);
        }
    }
}
