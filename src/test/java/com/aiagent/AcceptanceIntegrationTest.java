package com.aiagent;

import com.aiagent.auth.entity.ApiKey;
import com.aiagent.auth.repository.ApiKeyRepository;
import com.aiagent.memory.ConversationMemory;
import com.aiagent.memory.SessionScope;
import com.aiagent.rag.ChunkingService;
import com.aiagent.rag.VectorSearchService;
import com.aiagent.rag.entity.DocumentMetadata;
import com.aiagent.rag.repository.DocumentMetadataRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL + Redis + loopback model protocol fixture; no paid models. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AcceptanceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ApiKeyRepository keys;
    @Autowired DocumentMetadataRepository documents;
    @Autowired ChunkingService chunks;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired SessionScope sessions;
    @Autowired ConversationMemory memory;
    @SpyBean VectorSearchService vectors;
    final List<ApiKey> createdKeys = new ArrayList<>();
    final List<String> createdDocs = new ArrayList<>();
    ApiKey owner;

    @BeforeEach void setUp() { owner = createKey("owner-" + UUID.randomUUID(), 60); }
    ApiKey createKey(String user, int limit) {
        ApiKey key = new ApiKey();
        key.setKey("fixture-" + UUID.randomUUID());
        key.setUserId(user); key.setCreatedAt(Instant.now()); key.setRateLimit(limit);
        key = keys.save(key); createdKeys.add(key); return key;
    }
    String bearer(ApiKey key) { return "Bearer " + key.getKey(); }
    @AfterEach void cleanUp() {
        reset(vectors);
        createdDocs.forEach(chunks::deleteDocument);
        createdKeys.forEach(k -> {
            keys.deleteById(k.getId());
            redis.delete("agentforge:ratelimit:" + k.getId());
        });
    }

    @Test void managementRequiresAuthenticationAndNeverListsSecrets() throws Exception {
        mvc.perform(get("/auth/keys")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/auth/keys/anything")).andExpect(status().isUnauthorized());
        String body = mvc.perform(get("/auth/keys").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = json.readTree(body);
        assertEquals(1, list.size());
        assertFalse(list.get(0).has("key"));
        assertFalse(body.contains(owner.getKey()));
        assertFalse(owner.toString().contains(owner.getKey()));
    }

    @Test void ownersCannotRevokeEachOthersKeys() throws Exception {
        ApiKey other = createKey("other-" + UUID.randomUUID(), 60);
        mvc.perform(delete("/auth/keys/" + other.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        assertTrue(keys.findById(other.getId()).orElseThrow().isActive());
        mvc.perform(delete("/auth/keys/" + owner.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/auth/keys").header("Authorization", bearer(owner)))
                .andExpect(status().isUnauthorized());
    }

    @Test void registrationRequiresProvisioningTokenAndValidInput() throws Exception {
        String body = "{\"userId\":\"fixture-registration\",\"rateLimit\":10}";
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/register").header("X-Registration-Token", "test-registration-only")
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"\",\"rateLimit\":-1}"))
                .andExpect(status().isBadRequest());
        String result = mvc.perform(post("/auth/register").header("X-Registration-Token", "test-registration-only")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode registered = json.readTree(result);
        createdKeys.add(keys.findById(registered.get("id").asText()).orElseThrow());
        assertFalse(registered.get("key").asText().isBlank());
    }

    @Test void fixedWindowIsEnforcedAndHasExpiryInRealRedis() throws Exception {
        ApiKey limited = createKey("limited-" + UUID.randomUUID(), 2);
        for (int i = 0; i < 2; i++) mvc.perform(get("/auth/keys").header("Authorization", bearer(limited)))
                .andExpect(status().isOk());
        mvc.perform(get("/auth/keys").header("Authorization", bearer(limited))).andExpect(status().isTooManyRequests());
        Long ttl = redis.getExpire("agentforge:ratelimit:" + limited.getId());
        assertNotNull(ttl); assertTrue(ttl > 0 && ttl <= 60);
    }

    @Test void documentLifecycleUsesUtf8SizeAndDeletesBothIndexes() throws Exception {
        String text = "生命周期验收 marker_" + UUID.randomUUID();
        DocumentMetadata doc = chunks.ingestDocument(text, "fixture.txt"); createdDocs.add(doc.getId());
        assertEquals(text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, doc.getSizeBytes());
        assertTrue(vectors.keywordSearch(text, 5).contains(text));
        mvc.perform(get("/ai/knowledge/search").param("query", text).param("topK", "1")
                .header("Authorization", bearer(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/ai/knowledge/documents/" + doc.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        assertFalse(documents.existsById(doc.getId()));
        assertFalse(vectors.keywordSearch(text, 5).contains(text));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM agent_embeddings WHERE metadata->>'documentId'=?", Integer.class, doc.getId()));
    }

    @Test void failedSecondChunkRollsBackMetadataAndFirstVector() {
        long docsBefore = documents.count();
        int vectorsBefore = jdbc.queryForObject("SELECT count(*) FROM agent_embeddings", Integer.class);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(inv -> {
            if (calls.incrementAndGet() == 2) throw new IllegalStateException("fixture second-vector failure");
            return inv.callRealMethod();
        }).when(vectors).store(any(), any(), anyString());
        assertThrows(IllegalStateException.class, () -> chunks.ingestDocument("rollback_marker ".repeat(130), "rollback.txt"));
        assertTrue(calls.get() >= 2);
        assertEquals(docsBefore, documents.count());
        assertEquals(vectorsBefore, jdbc.queryForObject("SELECT count(*) FROM agent_embeddings", Integer.class));
    }

    @Test void identicalSessionNamesAreIsolatedByAuthenticatedUser() throws Exception {
        ApiKey other = createKey("other-" + UUID.randomUUID(), 60);
        String session = "same-session-" + UUID.randomUUID();
        for (ApiKey key : List.of(owner, other)) {
            mvc.perform(post("/ai/query").header("Authorization", bearer(key)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("query", "hello " + key.getUserId(), "sessionId", session))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("hello world"));
        }
        String first = sessions.storageKey(owner.getUserId(), session);
        String second = sessions.storageKey(other.getUserId(), session);
        assertNotEquals(first, second);
        assertEquals(2, memory.getHistory(first).size());
        assertEquals(2, memory.getHistory(second).size());
        assertFalse(String.join("", memory.getHistory(first)).contains(other.getUserId()));
        redis.delete(List.of("agentforge:conversation:" + first, "agentforge:conversation:" + second));
    }

    @Test void malformedKnowledgeAndQueryInputsAreRejected() throws Exception {
        mvc.perform(get("/ai/knowledge/search").param("query", "x").param("topK", "0")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mvc.perform(post("/ai/ingest").contentType(MediaType.TEXT_PLAIN).content(" ")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
        mvc.perform(post("/ai/query").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"\"}")
                .header("Authorization", bearer(owner))).andExpect(status().isBadRequest());
    }
}
