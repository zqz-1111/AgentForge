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

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> ask(@Valid @RequestBody AgentRequest request) {
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
