package com.aiagent.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话记忆的统一入口。
 * 由 Redis 存储并自动过期，通过滑动窗口保留最近的消息。
 *
 * 键格式：conversation:{sessionId}
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
