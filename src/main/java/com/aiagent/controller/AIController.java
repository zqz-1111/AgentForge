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
     * Multi-Agent Collaborative Task Coordination.
     * Invokes SupervisorAgent to orchestrate ResearchAgent and DataAnalystAgent.
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
     * Ingest raw text into the vector store for RAG retrieval.
     * Returns document metadata including assigned ID.
     */
    @PostMapping("/ingest")
    public ResponseEntity<DocumentMetadata> ingest(@RequestBody String document,
                                                   @RequestParam(required = false) String filename) {
        DocumentMetadata metadata = chunkingService.ingestDocument(document, filename);
        return ResponseEntity.ok(metadata);
    }
}
