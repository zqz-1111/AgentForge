package com.aiagent.rag;

import com.aiagent.rag.entity.DocumentMetadata;
import com.aiagent.rag.repository.DocumentMetadataRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import com.aiagent.exception.GuardrailException;

/**
 * 将原始文档切成片段，为向量检索生成向量，
 * 并为 BM25 关键词检索建立词项索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final DocumentMetadataRepository metadataRepo;

    @Value("${ai.rag.chunk-size:500}")
    private int chunkSize;

    @Value("${ai.rag.chunk-overlap:50}")
    private int chunkOverlap;

    /**
     * 导入文档时切片、写入向量存储、建立 BM25 索引并持久化元数据。
     * 返回包含已分配 ID 的文档元数据。
     */
    @Transactional
    public DocumentMetadata ingestDocument(String rawText, String filename) {
        if (rawText == null || rawText.isBlank()) throw new GuardrailException("Document must not be blank");
        if (rawText.getBytes(StandardCharsets.UTF_8).length > 1_048_576) {
            throw new GuardrailException("Document exceeds 1 MiB");
        }
        if (filename != null && filename.length() > 255) throw new GuardrailException("Filename exceeds 255 characters");
        // 1. 递归语义切分
        Document document = Document.from(rawText);
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        List<TextSegment> segments = splitter.split(document);

        // 发布任何片段前先生成所有向量。
        List<Embedding> embeddings = segments.stream().map(s -> embeddingService.embed(s.text())).toList();
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFilename(filename != null ? filename : "untitled");
        metadata.setChunkCount(segments.size());
        metadata.setSizeBytes(rawText.getBytes(StandardCharsets.UTF_8).length);
        metadata.setIngestedAt(Instant.now());

        log.info("Document ingested successfully: '{}' ({} chunks, {} bytes)",
                metadata.getFilename(), segments.size(), metadata.getSizeBytes());

        DocumentMetadata saved = metadataRepo.saveAndFlush(metadata);
        for (int i = 0; i < segments.size(); i++) {
            vectorSearchService.store(embeddings.get(i), segments.get(i), saved.getId());
        }
        return saved;
    }

    /**
     * 兼容旧调用的重载方法，导入时无需提供文件名。
     */
    @Transactional
    public DocumentMetadata ingestDocument(String rawText) {
        return ingestDocument(rawText, null);
    }

    @Transactional
    public boolean deleteDocument(String id) {
        if (!metadataRepo.existsById(id)) return false;
        vectorSearchService.deleteDocument(id);
        metadataRepo.deleteById(id);
        return true;
    }
}
