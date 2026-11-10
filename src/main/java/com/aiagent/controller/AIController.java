package com.aiagent.controller;

import com.aiagent.agent.orchestrator.AgentOrchestrator;
import com.aiagent.agent.orchestrator.SupervisorAgent;
import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.rag.ChunkingService;
import com.aiagent.rag.entity.DocumentMetadata;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final AgentOrchestrator orchestrator;
    private final SupervisorAgent supervisorAgent;
    private final ChunkingService chunkingService;
    private final com.aiagent.guardrails.InputFilter inputFilter;

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> ask(@Valid @RequestBody AgentRequest request, @RequestAttribute String userId) {
        request.setUserId(userId);
        AgentResponse response = orchestrator.runAgent(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 协调多个智能体执行任务。
     * 调用 SupervisorAgent 编排 ResearchAgent 和 DataAnalystAgent。
     */
    @PostMapping("/multi-agent/chat")
    public ResponseEntity<SupervisorAgent.MultiAgentResult> multiAgentChat(@RequestParam String query,
                                                                          @RequestParam(required = false, defaultValue = "") String context) {
        inputFilter.validate(query);
        if (context.length() > 10000) throw new com.aiagent.exception.GuardrailException("Context exceeds 10000 characters");
        SupervisorAgent.MultiAgentResult result = supervisorAgent.coordinate(query, context);
        return ResponseEntity.ok(result);
    }

    /**
     * 把原始文本导入向量存储，供 RAG 检索使用。
     * 返回包含已分配 ID 的文档元数据。
     */
    @PostMapping("/ingest")
    public ResponseEntity<DocumentMetadata> ingest(@RequestBody String document,
                                                   @RequestParam(required = false) String filename) {
        DocumentMetadata metadata = chunkingService.ingestDocument(document, filename);
        return ResponseEntity.ok(metadata);
    }
}
