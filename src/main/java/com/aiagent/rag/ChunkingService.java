package com.aiagent.rag;

import com.aiagent.rag.entity.DocumentMetadata;
import com.aiagent.rag.repository.DocumentMetadataRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Splits raw documents into chunks, generates embeddings for VectorSearch,
 * and indexes tokens for BM25 Keyword Search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final Bm25KeywordSearchService bm25KeywordSearchService;
    private final DocumentMetadataRepository metadataRepo;

    @Value("${ai.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${ai.rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * Ingest a document: chunk, embed into vector store, index into BM25 engine, and persist metadata.
     * Returns the document metadata with assigned ID.
     */
    public DocumentMetadata ingestDocument(String rawText, String filename) {
        // 1. Recursive semantic chunking
        Document document = Document.from(rawText);
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = splitter.split(document);

        // 2. Dual-channel indexing: Dense Vector + Sparse BM25
        for (TextSegment segment : segments) {
            String text = segment.text();

            // A. Dense vector embedding
            Embedding embedding = embeddingService.embed(text);
            vectorSearchService.store(embedding, segment);

            // B. Sparse BM25 keyword indexing
            bm25KeywordSearchService.addDocument(text);
        }

        // 3. Save document management metadata
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilename(filename != null ? filename : "untitled");
        metadata.setChunkCount(segments.size());
        metadata.setSizeBytes(rawText.length());
        metadata.setIngestedAt(Instant.now());

        log.info("Document ingested successfully: '{}' ({} chunks, {} bytes)",
                metadata.getFilename(), segments.size(), metadata.getSizeBytes());

        return metadataRepo.save(metadata);
    }

    /**
     * Backward-compatible overload: ingest without filename.
     */
    public DocumentMetadata ingestDocument(String rawText) {
        return ingestDocument(rawText, null);
    }
}
