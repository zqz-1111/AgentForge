package com.aiagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentRequest {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String userId; // 由完成鉴权的控制器设置，JSON 请求体不提供该值。

    @NotBlank(message = "Query must not be blank")
    @Size(max = 2000, message = "Query exceeds maximum allowed length")
    private String query;

    private String sessionId;  // 可选，用于关联对话记忆
}
