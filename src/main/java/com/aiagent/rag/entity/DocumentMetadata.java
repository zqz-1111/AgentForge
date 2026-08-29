package com.aiagent.rag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * Metadata for ingested documents in the knowledge base.
 * Tracks document lifecycle for management and deletion.
 */
@Entity
@Table(name = "document_metadata")
@Data
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private int chunkCount;

    @Column(nullable = false)
    private Instant ingestedAt;

    private long sizeBytes;
}
