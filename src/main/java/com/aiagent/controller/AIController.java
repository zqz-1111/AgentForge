package com.aiagent.controller;

import com.aiagent.agent.orchestrator.AgentOrchestrator;
import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.rag.ChunkingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIController {

    private final AgentOrchestrator orchestrator;
    private final ChunkingService chunkingService;

    @PostMapping("/query")
    public ResponseEntity<AgentResponse> ask(@Valid @RequestBody AgentRequest request) {
        AgentResponse response = orchestrator.runAgent(request);
        return ResponseEntity.ok(response);
    }

    // Ingest raw text into the vector store for RAG retrieval
    @PostMapping("/ingest")
    public ResponseEntity<String> ingest(@RequestBody String document) {
        chunkingService.ingestDocument(document);
        return ResponseEntity.ok("Document ingested successfully");
    }
}
