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
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import com.aiagent.exception.GuardrailException;

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
    private final DocumentMetadataRepository metadataRepo;

    @Value("${ai.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${ai.rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * Ingest a document: chunk, embed into vector store, index into BM25 engine, and persist metadata.
     * Returns the document metadata with assigned ID.
     */
    @Transactional
    public DocumentMetadata ingestDocument(String rawText, String filename) {
        if (rawText == null || rawText.isBlank()) throw new GuardrailException("Document must not be blank");
        if (rawText.getBytes(StandardCharsets.UTF_8).length > 1_048_576) {
            throw new GuardrailException("Document exceeds 1 MiB");
        }
        if (filename != null && filename.length() > 255) throw new GuardrailException("Filename exceeds 255 characters");
        // 1. Recursive semantic chunking
        Document document = Document.from(rawText);
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = splitter.split(document);

        // Generate every embedding before publishing any chunk.
        List<Embedding> embeddings = segments.stream().map(s -> embeddingService.embed(s.text())).toList();
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilename(filename != null ? filename : "untitled");
        metadata.setChunkCount(segments.size());
        metadata.setSizeBytes(rawText.getBytes(StandardCharsets.UTF_8).length);
        metadata.setIngestedAt(Instant.now());

        log.info("Document ingested successfully: '{}' ({} chunks, {} bytes)",
                metadata.getFilename(), segments.size(), metadata.getSizeBytes());

        DocumentMetadata saved = metadataRepo.saveAndFlush(metadata);
        for (int i = 0; i < segments.size(); i++) {
            vectorSearchService.store(embeddings.get(i), segments.get(i), saved.getId());
        }
        return saved;
    }

    /**
     * Backward-compatible overload: ingest without filename.
     */
    @Transactional
    public DocumentMetadata ingestDocument(String rawText) {
        return ingestDocument(rawText, null);
    }

    @Transactional
    public boolean deleteDocument(String id) {
        if (!metadataRepo.existsById(id)) return false;
        vectorSearchService.deleteDocument(id);
        metadataRepo.deleteById(id);
        return true;
    }
}
