package com.aiagent.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentResponse {

    private String answer;
    private String sessionId;
    private int iterationsUsed;
    private long latencyMs;
}
