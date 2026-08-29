package com.aiagent.auth;

import com.aiagent.auth.entity.ApiKey;
import com.aiagent.auth.repository.ApiKeyRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API Key management endpoints.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ApiKeyRepository apiKeyRepo;

    /**
     * Register a new API key.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiKey> register(@RequestBody RegisterRequest request) {
        ApiKey key = new ApiKey();
        key.setKey("sk-" + UUID.randomUUID().toString().replace("-", ""));
        key.setUserId(request.getUserId());
        key.setRateLimit(request.getRateLimit() > 0 ? request.getRateLimit() : 60);
        key.setCreatedAt(Instant.now());
        key.setActive(true);

        ApiKey saved = apiKeyRepo.save(key);

        // Return the key only once (can't be retrieved later)
        ApiKey response = new ApiKey();
        response.setId(saved.getId());
        response.setKey(saved.getKey()); // Include key in response
        response.setUserId(saved.getUserId());
        response.setCreatedAt(saved.getCreatedAt());
        response.setRateLimit(saved.getRateLimit());

        return ResponseEntity.ok(response);
    }

    /**
     * List all API keys (without revealing key values).
     */
    @GetMapping("/keys")
    public List<ApiKey> listKeys() {
        return apiKeyRepo.findAll();
    }

    /**
     * Deactivate an API key.
     */
    @DeleteMapping("/keys/{id}")
    public ResponseEntity<Void> deactivateKey(@PathVariable String id) {
        return apiKeyRepo.findById(id)
                .map(key -> {
                    key.setActive(false);
                    apiKeyRepo.save(key);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Data
    public static class RegisterRequest {
        private String userId;
        private int rateLimit; // requests per minute, default 60
    }
}
