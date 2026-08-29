package com.aiagent.auth;

import com.aiagent.auth.entity.ApiKey;
import com.aiagent.auth.repository.ApiKeyRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

/**
 * Authentication filter: validates API keys and enforces rate limiting.
 * Uses Redis sliding window counter for per-key rate limiting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepo;
    private final StringRedisTemplate redisTemplate;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Extract API key from Bearer token
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid Authorization header. Use: Bearer sk-xxx\"}");
            return;
        }

        String apiKeyValue = authHeader.substring(BEARER_PREFIX.length()).trim();

        // Look up the key
        Optional<ApiKey> apiKeyOpt = apiKeyRepo.findByKeyAndActiveTrue(apiKeyValue);
        if (apiKeyOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or inactive API key\"}");
            return;
        }

        ApiKey apiKey = apiKeyOpt.get();

        // Rate limiting: sliding window counter in Redis
        if (!checkRateLimit(apiKey)) {
            response.setStatus(429); // HTTP 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Max " + apiKey.getRateLimit() + " requests per minute.\"}");
            return;
        }

        // Set userId in request attribute for downstream use
        request.setAttribute("userId", apiKey.getUserId());
        request.setAttribute("apiKeyId", apiKey.getId());

        filterChain.doFilter(request, response);
    }

    /**
     * Check rate limit using Redis sliding window counter.
     * Returns true if within limit, false if exceeded.
     */
    private boolean checkRateLimit(ApiKey apiKey) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKey.getId();
        Long count = redisTemplate.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redisTemplate.expire(redisKey, Duration.ofMinutes(1));
        }
        return count != null && count <= apiKey.getRateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip auth for public endpoints
        return path.startsWith("/auth/")
                || path.startsWith("/actuator/")
                || path.equals("/error");
    }
}
