package com.aiagent.tools.calculator;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * Tool: Safe mathematical expression evaluation.
 * Uses exp4j library (no ScriptEngine for security).
 */
@Slf4j
@Component
public class CalculatorTool {

    @Tool("Evaluate a mathematical expression. Supports +, -, *, /, ^, sqrt, sin, cos, tan, log, ln, pi, e. Example: 2+3*4 or sqrt(144)")
    public String calculate(String expression) {
        if (expression == null || expression.isBlank()) {
            return "Error: empty expression";
        }
        try {
            Expression expr = new ExpressionBuilder(expression.trim())
                    .variables("pi", "e")
                    .build()
                    .setVariable("pi", Math.PI)
                    .setVariable("e", Math.E);

            double result = expr.evaluate();
            // Format: remove trailing .0 for integers
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            }
            return String.valueOf(result);
        } catch (Exception e) {
            log.warn("Calculator error for '{}': {}", expression, e.getMessage());
            return "Error evaluating expression: " + e.getMessage();
        }
    }
}
