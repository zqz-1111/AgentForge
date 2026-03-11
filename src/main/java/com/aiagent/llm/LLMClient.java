package com.aiagent.llm;

import com.aiagent.observability.PromptLogger;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Encapsulates all LLM communication.
 * Uses ModelRouter to select the appropriate model per query.
 */
@Service
@RequiredArgsConstructor
public class LLMClient {

    private final ModelRouter modelRouter;
    private final PromptLogger promptLogger;

    public String generate(String prompt) {
        promptLogger.log(prompt);

        ChatLanguageModel model = modelRouter.route(prompt);
        return model.generate(prompt);
    }
}
