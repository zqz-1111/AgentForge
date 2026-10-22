package com.aiagent.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisMemoryStore {

    private final StringRedisTemplate redisTemplate;

    @Value("${ai.memory.ttl-minutes:60}")
    private long ttlMinutes;

    @Value("${ai.memory.max-messages:20}")
    private int maxMessages;

    private static final String KEY_PREFIX = "agentforge:conversation:";

    public void push(String sessionId, String message) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.opsForList().rightPush(key, message);
        redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes));

        // Trim to max window to prevent unbounded growth
        redisTemplate.opsForList().trim(key, -maxMessages, -1);
    }

    public List<String> getAll(String sessionId) {
        String key = KEY_PREFIX + sessionId;
        List<String> messages = redisTemplate.opsForList().range(key, 0, -1);
        return messages != null ? messages : List.of();
    }
}
