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
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按成本路由模型，支持多个服务商和流式调用。
 *
 * 支持以下两种服务商。
 *   CLOUD：兼容 OpenAI 的接口，例如阿里云百炼、DeepSeek。
 *   OLLAMA：本地 Ollama 部署，例如 llama3、qwen2。
 *
 * 根据问题复杂度路由。
 *   简单或较短的问题使用成本较低的 qwen3.7-flash（原估算约为十分之一）。
 *   复杂或分析类问题使用能力更强的 qwen3.7-max。
 *
 * 提供阻塞式和流式两种模型调用。
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

    @Value("${ai.openai.timeout-seconds:30}")
    private long timeoutSeconds;

    @Value("${ai.openai.max-tokens:2048}")
    private int maxTokens;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    private static final int COMPLEX_QUERY_THRESHOLD = 100;
    private static final String COMPLEX_KEYWORDS = ".*(why|analyze|compare|explain|debug|reason|how|what|design|optimize).*";

    // 缓存阻塞式模型实例
    private final Map<String, ChatLanguageModel> modelCache = new ConcurrentHashMap<>();
    // 缓存流式模型实例
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
            // LangChain4j 0.31 没有单独的 Ollama 流式模型类
            // 沿用同一个模型，流式调用降级为阻塞式调用
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
     * 为问题选择合适的阻塞式模型。
     */
    public ChatLanguageModel route(String query) {
        boolean complex = isComplex(query);
        return complex ? complexModel : simpleModel;
    }

    /**
     * 为问题选择合适的流式模型。
     * 流式调用不可用时返回 null，例如使用 Ollama 时。
     */
    public StreamingChatLanguageModel routeStreaming(String query) {
        boolean complex = isComplex(query);
        return complex ? complexStreamingModel : simpleStreamingModel;
    }

    /**
     * 获取指定问题对应的模型名称。
     */
    public String resolveModelName(String query) {
        return isComplex(query) ? complexModelName : simpleModelName;
    }

    /**
     * 检查流式调用是否可用。
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
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .maxTokens(maxTokens)
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
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .maxTokens(maxTokens)
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
