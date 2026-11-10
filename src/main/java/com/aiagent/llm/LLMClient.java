package com.aiagent.llm;

import com.aiagent.observability.PromptLogger;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 封装所有大语言模型调用。
 * 通过 ModelRouter 为每个问题选择合适的模型。
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
