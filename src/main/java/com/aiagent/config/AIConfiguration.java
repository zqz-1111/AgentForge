package com.aiagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;
import java.time.Duration;

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

    @Value("${ai.openai.timeout-seconds:30}")
    private long timeoutSeconds;

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
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource datasource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(datasource)
                .table("agent_embeddings")
                .dimension(1024) // text-embedding-v3 output dimension
                .build();
    }
}
