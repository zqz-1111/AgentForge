package com.aiagent.tools.sql;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tool: Execute read-only SQL queries against PostgreSQL.
 * Only SELECT queries are permitted for safety.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlTool {

    private final JdbcTemplate jdbcTemplate;

    @Tool("Execute a read-only SQL query against the database. Only SELECT queries are allowed. Returns formatted results.")
    public String executeQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            return "Error: SQL query is required";
        }

        String trimmed = sql.trim();

        // Security: only allow SELECT queries
        if (!trimmed.toUpperCase().startsWith("SELECT")) {
            return "Error: only SELECT queries are permitted for safety";
        }

        // Block dangerous keywords
        String upper = trimmed.toUpperCase();
        if (upper.contains("DROP ") || upper.contains("DELETE ") || upper.contains("UPDATE ")
                || upper.contains("INSERT ") || upper.contains("ALTER ") || upper.contains("TRUNCATE ")) {
            return "Error: only read-only SELECT queries are permitted";
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmed);

            if (rows.isEmpty()) {
                return "Query executed successfully. No rows returned.";
            }

            return formatResults(rows);
        } catch (Exception e) {
            log.warn("SQL execution error: {}", e.getMessage());
            return "SQL Error: " + e.getMessage();
        }
    }

    private String formatResults(List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query returned ").append(rows.size()).append(" rows:\n\n");

        // Header
        Map<String, Object> firstRow = rows.get(0);
        List<String> columns = firstRow.keySet().stream().toList();
        sb.append(String.join(" | ", columns)).append("\n");
        sb.append(columns.stream().map(c -> "-".repeat(Math.max(c.length(), 3))).collect(Collectors.joining("-+-"))).append("\n");

        // Rows (limit to 20 for readability)
        int displayCount = Math.min(rows.size(), 20);
        for (int i = 0; i < displayCount; i++) {
            Map<String, Object> row = rows.get(i);
            sb.append(columns.stream()
                    .map(c -> String.valueOf(row.getOrDefault(c, "NULL")))
                    .collect(Collectors.joining(" | "))).append("\n");
        }

        if (rows.size() > 20) {
            sb.append("\n... and ").append(rows.size() - 20).append(" more rows");
        }

        return sb.toString();
    }
}
