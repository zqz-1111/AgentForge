package com.aiagent.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Routes queries to the appropriate model based on complexity.
 *
 * Simple queries (short, no reasoning keywords) → cheaper/faster model (gpt-3.5-turbo)
 * Complex queries (multi-step, analytical)       → powerful model (gpt-4o)
 *
 * This reduces cost while maintaining quality for complex tasks.
 */
@Slf4j
@Component
public class ModelRouter {

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.model-router.simple-model:gpt-3.5-turbo}")
    private String simpleModel;

    @Value("${ai.model-router.complex-model:gpt-4o}")
    private String complexModel;

    @Value("${ai.openai.temperature:0.2}")
    private double temperature;

    private static final int COMPLEX_QUERY_THRESHOLD = 100;

    public ChatLanguageModel route(String query) {
        String modelName = isComplex(query) ? complexModel : simpleModel;
        log.debug("ModelRouter selected: {} for query length={}", modelName, query.length());

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .build();
    }

    private boolean isComplex(String query) {
        return query.length() > COMPLEX_QUERY_THRESHOLD
                || query.toLowerCase().matches(".*(why|analyze|compare|explain|debug|reason).*");
    }
}
