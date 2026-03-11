package com.aiagent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentRequest {

    @NotBlank(message = "Query must not be blank")
    @Size(max = 2000, message = "Query exceeds maximum allowed length")
    private String query;

    private String sessionId;  // optional: for conversation memory
}
