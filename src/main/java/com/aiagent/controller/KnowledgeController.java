package com.aiagent.controller;

import com.aiagent.rag.ChunkingService;
import com.aiagent.rag.RetrievalService;
import com.aiagent.rag.entity.DocumentMetadata;
import com.aiagent.rag.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Knowledge base management endpoints.
 * Provides CRUD operations for documents and vector search.
 */
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentMetadataRepository metadataRepo;
    private final ChunkingService chunkingService;
    private final RetrievalService retrievalService;

    /**
     * List all ingested documents with metadata.
     */
    @GetMapping("/documents")
    public List<DocumentMetadata> listDocuments() {
        return metadataRepo.findAllByOrderByIngestedAtDesc();
    }

    /**
     * Delete a document by ID.
     * Note: pgvector doesn't support metadata-based deletion natively.
     * The embeddings remain but the metadata record is removed.
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        if (!metadataRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        metadataRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Search the knowledge base with a natural language query.
     */
    @GetMapping("/search")
    public List<String> search(@RequestParam String query,
                               @RequestParam(defaultValue = "5") int topK) {
        return retrievalService.retrieve(query);
    }
}
