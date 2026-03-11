package com.aiagent.tools.database;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Tool: Run read-only database queries.
 * IMPORTANT: Only allow SELECT statements. Never expose write operations via tools.
 */
@Component
public class DatabaseTools {

    @Tool("Run a read-only SQL query against the database")
    public String query(String sql) {
        if (sql == null || !sql.trim().toUpperCase().startsWith("SELECT")) {
            return "Only SELECT queries are permitted";
        }
        // Stub — replace with JdbcTemplate or JPA call
        return "Query executed: " + sql + " → 0 rows returned";
    }
}
