package com.aiagent.controller;

import com.aiagent.rag.ChunkingService;
import com.aiagent.rag.RetrievalService;
import com.aiagent.rag.entity.DocumentMetadata;
import com.aiagent.rag.repository.DocumentMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理接口。
 * 提供文档增删改查及向量检索。
 */
@RestController
@RequestMapping("/ai/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final DocumentMetadataRepository metadataRepo;
    private final ChunkingService chunkingService;
    private final RetrievalService retrievalService;

    /**
     * 列出已导入的文档及其元数据。
     */
    @GetMapping("/documents")
    public List<DocumentMetadata> listDocuments() {
        return metadataRepo.findAllByOrderByIngestedAtDesc();
    }

    /**
     * 按 ID 删除文档。
     * 在同一个数据库事务中删除文档元数据及关联向量。
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        if (!chunkingService.deleteDocument(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * 用自然语言问题搜索知识库。
     */
    @GetMapping("/search")
    public List<String> search(@RequestParam String query,
                               @RequestParam(defaultValue = "5") int topK) {
        if (topK < 1 || topK > 50) throw new com.aiagent.exception.GuardrailException("topK must be between 1 and 50");
        if (query.isBlank() || query.length() > 2000) throw new com.aiagent.exception.GuardrailException("Query length must be 1 to 2000 characters");
        return retrievalService.retrieve(query, topK);
    }
}
