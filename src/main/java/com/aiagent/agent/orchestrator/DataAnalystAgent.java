package com.aiagent.agent.orchestrator;

import com.aiagent.tools.calculator.CalculatorTool;
import com.aiagent.tools.sql.SqlTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Specialized Worker Agent: Data Analysis & Computation.
 * Executes supplied SELECT statements (when explicitly enabled) and arithmetic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataAnalystAgent implements SubAgent {

    private final SqlTool sqlTool;
    private final CalculatorTool calculatorTool;

    @Override
    public String getName() {
        return "DataAnalystAgent";
    }

    @Override
    public String getDescription() {
        return "Specialized in read-only SQL database querying, aggregation, and mathematical formula evaluation.";
    }

    @Override
    public boolean canHandle(String taskDescription) {
        if (taskDescription == null) return false;
        String lower = taskDescription.toLowerCase();
        return lower.contains("sql") || lower.contains("select") || lower.contains("database")
                || lower.contains("数据") || lower.contains("统计") || lower.contains("计算")
                || lower.contains("算一下") || lower.contains("表") || lower.contains("count");
    }

    @Override
    public String execute(String taskDescription, String context) {
        log.info("[DataAnalystAgent] Executing analytical task: {}", taskDescription);
        StringBuilder output = new StringBuilder();

        // 1. If contains math expression or calculate request
        if (taskDescription.matches(".*[0-9+\\-*/^()\\s]{3,}.*") || taskDescription.contains("计算") || taskDescription.contains("算")) {
            String expr = taskDescription.replaceAll("[^0-9+\\-*/^().]", " ").trim();
            if (!expr.isBlank()) {
                String calcRes = calculatorTool.calculate(expr);
                output.append("### 【数学计算结果】\n").append(expr).append(" = ").append(calcRes).append("\n\n");
            }
        }

        // 2. If SQL execution
        if (taskDescription.toUpperCase().contains("SELECT")) {
            int selectIdx = taskDescription.toUpperCase().indexOf("SELECT");
            String sql = taskDescription.substring(selectIdx).trim();
            String sqlRes = sqlTool.executeQuery(sql);
            output.append("### 【数据库查询分析】\n").append(sqlRes).append("\n");
        }

        if (output.length() == 0) {
            return "数据分析完成，未发现需执行的 SQL 查询或数学计算公式。";
        }

        return output.toString();
    }
}
