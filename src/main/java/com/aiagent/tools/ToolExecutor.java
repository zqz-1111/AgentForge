package com.aiagent.tools;

import com.aiagent.tools.ToolRegistry.ToolEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Dispatches tool calls from the ReAct loop to the correct tool implementation.
 * Uses ToolRegistry for dynamic, annotation-driven tool discovery.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final ToolRegistry toolRegistry;

    public String execute(String toolName, String input) {
        ToolEntry entry = toolRegistry.lookup(toolName);
        if (entry == null) {
            log.warn("Unknown tool requested: {}", toolName);
            return "Unknown tool: " + toolName;
        }

        try {
            Method method = entry.method();
            method.setAccessible(true);
            Object result = method.invoke(entry.bean(), input);
            return result != null ? result.toString() : "Tool returned null";
        } catch (Exception e) {
            log.error("Tool execution failed for {}: {}", toolName, e.getMessage());
            return "Tool execution error: " + e.getMessage();
        }
    }
}
