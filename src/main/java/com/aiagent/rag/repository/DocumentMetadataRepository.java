package com.aiagent.rag.repository;

import com.aiagent.rag.entity.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for document metadata CRUD operations.
 */
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {

    List<DocumentMetadata> findAllByOrderByIngestedAtDesc();
}
