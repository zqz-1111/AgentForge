package com.aiagent.auth.repository;

import com.aiagent.auth.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

/**
 * Repository for API key lookups.
 */
public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {

    Optional<ApiKey> findByKeyAndActiveTrue(String key);
    List<ApiKey> findByUserId(String userId);
    Optional<ApiKey> findByIdAndUserId(String id, String userId);
}
