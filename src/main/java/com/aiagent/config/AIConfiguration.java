package com.aiagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires LangChain4j beans.
 *
 * EmbeddingModel : Alibaba Cloud text-embedding-v3 (OpenAI-compatible API)
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

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName("text-embedding-v3")
                .build();
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
                .dimension(1024) // text-embedding-v3 output dimension
                .build();
    }
}
