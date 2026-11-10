package com.aiagent.rag.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 知识库中已导入文档的元数据。
 * 记录文档生命周期，供管理和删除使用。
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
