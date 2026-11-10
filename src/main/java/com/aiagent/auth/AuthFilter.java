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
 * 鉴权过滤器，校验 API 密钥并实施限流。
 * 用 Redis 原子固定窗口计数器按密钥限流。
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

        // 从 Bearer 令牌中提取 API 密钥
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing or invalid Authorization header. Use: Bearer sk-xxx\"}");
            return;
        }

        String apiKeyValue = authHeader.substring(BEARER_PREFIX.length()).trim();

        // 查询密钥记录
        Optional<ApiKey> apiKeyOpt = apiKeyRepo.findByKeyAndActiveTrue(apiKeyValue);
        if (apiKeyOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or inactive API key\"}");
            return;
        }

        ApiKey apiKey = apiKeyOpt.get();

        // 固定窗口从首次请求开始，计数和设置过期时间在同一次原子操作中完成。
        if (!checkRateLimit(apiKey)) {
            response.setStatus(429); // HTTP 429，请求过于频繁
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded. Max " + apiKey.getRateLimit() + " requests per minute.\"}");
            return;
        }

        // 把 userId 写入请求属性，供后续处理使用
        request.setAttribute("userId", apiKey.getUserId());
        request.setAttribute("apiKeyId", apiKey.getId());

        filterChain.doFilter(request, response);
    }

    /**
     * 用 Redis 原子固定窗口计数器检查限流。
     * 未超出限额时返回 true，超出时返回 false。
     */
    private boolean checkRateLimit(ApiKey apiKey) {
        String redisKey = RATE_LIMIT_KEY_PREFIX + apiKey.getId();
        Long count = redisTemplate.execute(RATE_SCRIPT, List.of(redisKey));
        return count != null && count <= apiKey.getRateLimit();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 公开接口跳过鉴权
        return (path.equals("/auth/register") && request.getMethod().equals("POST"))
                || path.equals("/actuator/health")
                || path.startsWith("/actuator/health/")
                || path.equals("/error");
    }
}
