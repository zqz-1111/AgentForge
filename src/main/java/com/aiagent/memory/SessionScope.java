package com.aiagent.memory;

import com.aiagent.exception.GuardrailException;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class SessionScope {
    public String resolve(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return UUID.randomUUID().toString();
        if (sessionId.length() > 128) throw new GuardrailException("Session ID exceeds 128 characters");
        return sessionId;
    }

    public String storageKey(String userId, String sessionId) {
        if (userId == null || userId.isBlank()) throw new GuardrailException("Authenticated user is required");
        try {
            String input = userId.length() + ":" + userId + sessionId;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
