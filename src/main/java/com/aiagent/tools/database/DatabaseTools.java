package com.aiagent.tools.database;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * 执行只读数据库查询的工具。
 * 仅允许 SELECT 语句，工具不得暴露写入操作。
 */
@Component
public class DatabaseTools {

    @Tool("Run a read-only SQL query against the database")
    public String query(String sql) {
        if (sql == null || !sql.trim().toUpperCase().startsWith("SELECT")) {
            return "Only SELECT queries are permitted";
        }
        // 占位实现，可替换为 JdbcTemplate 或 JPA 调用
        return "Query executed: " + sql + " → 0 rows returned";
    }
}
