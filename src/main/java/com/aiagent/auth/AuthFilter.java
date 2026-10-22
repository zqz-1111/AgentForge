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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;
import java.util.Optional;

/**
 * Authentication filter: validates API keys and enforces rate limiting.
 * Uses an atomic Redis fixed-window counter for per-key rate limiting.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepo;
    private final StringRedisTemplate redisTemplate;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String RATE_LIMIT_KEY_PREFIX = "agentforge:ratelimit:";
    private static final DefaultRedisScript<Long> RATE_SCRIPT = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); "
            + "if n==1 or redis.call('TTL',KEYS[1])<0 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);

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

        // Fixed window starts on the first request. Increment and TTL are atomic.
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
     * Check rate limit using an atomic Redis fixed-window counter.
     * Returns true if within limit, false if exceeded.
     */
    private boolean checkRateLimit(ApiKey apiKey) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKey.getId();
        Long count = redisTemplate.execute(RATE_SCRIPT, List.of(redisKey));
        return count != null && count <= apiKey.getRateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip auth for public endpoints
        return (path.equals("/auth/register") && request.getMethod().equals("POST"))
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/error");
    }
}
