package com.aiagent.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cost-aware model router with multi-provider and streaming support.
 *
 * Supports two providers:
 *   CLOUD  - OpenAI-compatible APIs (Alibaba Cloud DashScope, DeepSeek, etc.)
 *   OLLAMA - Local Ollama deployment (llama3, qwen2, etc.)
 *
 * Routes queries based on complexity:
 *   Simple/short queries  → cheaper model (qwen3.7-flash, ~10x cheaper)
 *   Complex/analytical    → powerful model (qwen3.7-max)
 *
 * Provides both blocking and streaming model access.
 */
@Slf4j
@Component
public class ModelRouter {

    @Value("${ai.model-router.provider:CLOUD}")
    private String provider;

    @Value("${ai.openai.api-key:}")
    private String apiKey;

    @Value("${ai.openai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${ai.model-router.simple-model:qwen3.7-flash}")
    private String simpleModelName;

    @Value("${ai.model-router.complex-model:qwen3.7-max}")
    private String complexModelName;

    @Value("${ai.openai.temperature:0.2}")
    private double temperature;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private static final int COMPLEX_QUERY_THRESHOLD = 100;
    private static final String COMPLEX_KEYWORDS = ".*(why|analyze|compare|explain|debug|reason|how|what|design|optimize).*";

    // Cached blocking model instances
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    // Cached streaming model instances
    private final Map<String, StreamingChatLanguageModel> streamingCache = new ConcurrentHashMap<>();

    private ChatLanguageModel simpleModel;
    private ChatLanguageModel complexModel;
    private StreamingChatLanguageModel simpleStreamingModel;
    private StreamingChatLanguageModel complexStreamingModel;

    @PostConstruct
    void init() {
        if ("OLLAMA".equalsIgnoreCase(provider)) {
            this.simpleModel = getOrCreateOllamaModel(simpleModelName);
            this.complexModel = getOrCreateOllamaModel(complexModelName);
            // Ollama doesn't have a separate streaming model class in LangChain4j 0.31
            // Use the same model - streaming will fall back to blocking
            this.simpleStreamingModel = null;
            this.complexStreamingModel = null;
        } else {
            this.simpleModel = getOrCreateCloudModel(simpleModelName);
            this.complexModel = getOrCreateCloudModel(complexModelName);
            this.simpleStreamingModel = getOrCreateStreamingModel(simpleModelName);
            this.complexStreamingModel = getOrCreateStreamingModel(complexModelName);
        }
        log.info("ModelRouter initialized: provider={}, simple={}, complex={}", provider, simpleModelName, complexModelName);
    }

    /**
     * Route a query to the appropriate blocking model.
     */
    public ChatLanguageModel route(String query) {
        boolean complex = isComplex(query);
        return complex ? complexModel : simpleModel;
    }

    /**
     * Route a query to the appropriate streaming model.
     * Returns null if streaming is not available (e.g., Ollama provider).
     */
    public StreamingChatLanguageModel routeStreaming(String query) {
        boolean complex = isComplex(query);
        return complex ? complexStreamingModel : simpleStreamingModel;
    }

    /**
     * Get the model name for a given query.
     */
    public String resolveModelName(String query) {
        return isComplex(query) ? complexModelName : simpleModelName;
    }

    /**
     * Check if streaming is available.
     */
    public boolean isStreamingAvailable() {
        return simpleStreamingModel != null;
    }

    public boolean isOllama() {
        return "OLLAMA".equalsIgnoreCase(provider);
    }

    private boolean isComplex(String query) {
        return query.length() > COMPLEX_QUERY_THRESHOLD
                || query.toLowerCase().matches(COMPLEX_KEYWORDS);
    }

    private ChatLanguageModel getOrCreateCloudModel(String modelName) {
        return modelCache.computeIfAbsent("cloud:" + modelName, name -> {
            log.info("Creating blocking cloud model: {}", modelName);
            return OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .build();
        });
    }

    private StreamingChatLanguageModel getOrCreateStreamingModel(String modelName) {
        return streamingCache.computeIfAbsent("stream:" + modelName, name -> {
            log.info("Creating streaming cloud model: {}", modelName);
            return OpenAiStreamingChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .build();
        });
    }

    private ChatLanguageModel getOrCreateOllamaModel(String modelName) {
        return modelCache.computeIfAbsent("ollama:" + modelName, name -> {
            log.info("Creating Ollama model: {} at {}", modelName, ollamaBaseUrl);
            return OllamaChatModel.builder()
                    .baseUrl(ollamaBaseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    .build();
        });
    }
}
