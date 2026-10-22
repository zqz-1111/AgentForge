package com.aiagent.auth;

import com.aiagent.auth.entity.ApiKey;
import com.aiagent.auth.repository.ApiKeyRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    @Value("${ai.auth.registration-token:}")
    private String registrationToken;

    /**
     * Register a new API key.
     */
    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Registration-Token", defaultValue = "") String suppliedToken) {
        if (registrationToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Key registration is disabled");
        }
        if (!MessageDigest.isEqual(registrationToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid registration token");
        }
        ApiKey key = new ApiKey();
        key.setKey("sk-" + UUID.randomUUID().toString().replace("-", ""));
        key.setUserId(request.getUserId());
        key.setRateLimit(request.getRateLimit() > 0 ? request.getRateLimit() : 60);
        key.setCreatedAt(Instant.now());
        key.setActive(true);

        ApiKey saved = apiKeyRepo.save(key);

        return ResponseEntity.ok(new RegistrationResponse(saved.getId(), saved.getKey(), saved.getUserId(),
                saved.getCreatedAt(), saved.getRateLimit()));
    }

    /**
     * List all API keys (without revealing key values).
     */
    @GetMapping("/keys")
    public List<ApiKey> listKeys(@RequestAttribute String userId) {
        return apiKeyRepo.findByUserId(userId);
    }

    /**
     * Deactivate an API key.
     */
    @DeleteMapping("/keys/{id}")
    public ResponseEntity<Void> deactivateKey(@PathVariable String id, @RequestAttribute String userId) {
        return apiKeyRepo.findByIdAndUserId(id, userId)
                .map(key -> {
                    key.setActive(false);
                    apiKeyRepo.save(key);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Data
    public static class RegisterRequest {
        @NotBlank @Size(max = 100)
        private String userId;
        @Min(0) @Max(10000)
        private int rateLimit; // requests per minute, default 60
    }

    public record RegistrationResponse(String id, String key, String userId, Instant createdAt, int rateLimit) {}
}
