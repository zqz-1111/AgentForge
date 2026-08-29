package com.aiagent.tools.fileread;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.*;
import java.util.List;

/**
 * Tool: Read file contents with path sandboxing for security.
 * Prevents path traversal attacks by verifying canonical path.
 */
@Slf4j
@Component
public class FileReadTool {

    @Value("${ai.tools.file-read.sandbox-root:F:/work}")
    private String sandboxRoot;

    @Value("${ai.tools.file-read.max-lines:100}")
    private int maxLines;

    @Tool("Read the contents of a text file. Provide the file path. Returns the first N lines.")
    public String readFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "Error: file path is required";
        }

        try {
            Path path = Path.of(filePath.trim()).toRealPath();
            Path sandbox = Path.of(sandboxRoot).toRealPath();

            // Security: verify path is within sandbox
            if (!path.startsWith(sandbox)) {
                log.warn("Path traversal attempt blocked: {}", filePath);
                return "Error: access denied. Path must be within " + sandboxRoot;
            }

            if (!Files.exists(path)) {
                return "Error: file not found: " + filePath;
            }

            if (!Files.isRegularFile(path)) {
                return "Error: not a regular file: " + filePath;
            }

            List<String> lines = Files.readAllLines(path);
            int totalLines = lines.size();
            int readLines = Math.min(totalLines, maxLines);

            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(filePath).append(" (").append(totalLines).append(" lines total)\n");
            sb.append("Showing first ").append(readLines).append(" lines:\n\n");

            for (int i = 0; i < readLines; i++) {
                sb.append(String.format("%4d: %s\n", i + 1, lines.get(i)));
            }

            if (totalLines > maxLines) {
                sb.append("\n... ").append(totalLines - maxLines).append(" more lines truncated");
            }

            return sb.toString();
        } catch (NoSuchFileException e) {
            return "Error: file not found: " + filePath;
        } catch (AccessDeniedException e) {
            return "Error: access denied: " + filePath;
        } catch (Exception e) {
            log.error("File read error for {}: {}", filePath, e.getMessage());
            return "Error reading file: " + e.getMessage();
        }
    }
}
