package com.aiagent.rag.repository;

import com.aiagent.rag.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 用于增删改查文档元数据的数据仓库。
 */
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {

    List<DocumentMetadata> findAllByOrderByIngestedAtDesc();
}
