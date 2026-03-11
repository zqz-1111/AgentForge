package com.aiagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires LangChain4j beans.
 *
 * EmbeddingModel : local all-MiniLM-L6-v2 (no API cost for embeddings)
 * EmbeddingStore : pgvector (PostgreSQL extension)
 *
 * ChatModel is created per-request by ModelRouter to support dynamic model selection.
 */
@Configuration
public class AIConfiguration {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // Parse host/port from JDBC URL: jdbc:postgresql://host:port/db
        String withoutPrefix = datasourceUrl.replace("jdbc:postgresql://", "");
        String[] hostPort = withoutPrefix.split("/")[0].split(":");
        String host = hostPort[0];
        int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1]) : 5432;
        String database = withoutPrefix.split("/")[1];

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(dbUsername)
                .password(dbPassword)
                .table("agent_embeddings")
                .dimension(384) // all-MiniLM-L6-v2 output dimension
                .createTableIfNotExists(true)
                .build();
    }
}
