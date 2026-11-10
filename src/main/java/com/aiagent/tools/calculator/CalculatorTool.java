package com.aiagent.tools.calculator;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Component;

/**
 * 安全计算数学表达式的工具。
 * 出于安全考虑，使用 exp4j 库进行求值。
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
            // 格式化时去掉整数末尾的 .0
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
