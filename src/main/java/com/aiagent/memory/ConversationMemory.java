package com.aiagent.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Facade for conversation memory.
 * Backed by Redis with TTL-based expiry and a sliding window of recent messages.
 *
 * Key format: conversation:{sessionId}
 */
@Service
@RequiredArgsConstructor
public class ConversationMemory {

    private final RedisMemoryStore store;

    public void store(String sessionId, String message) {
        store.push(sessionId, message);
    }

    public List<String> getHistory(String sessionId) {
        return store.getAll(sessionId);
    }
}
