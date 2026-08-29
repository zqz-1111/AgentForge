package com.aiagent.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Auto-discovers all @Tool-annotated methods at startup.
 * Replaces the hardcoded switch in ToolExecutor with dynamic registration.
 */
@Slf4j
@Component
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final Map<String, ToolEntry> registry = new HashMap<>();

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    void init() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            try {
                Object bean = applicationContext.getBean(beanName);
                // Get the target class (handle CGLIB proxies)
                Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
                for (Method method : targetClass.getDeclaredMethods()) {
                    Tool toolAnnotation = method.getAnnotation(Tool.class);
                    if (toolAnnotation != null) {
                        String toolName = method.getName().toLowerCase();
                        String description = extractDescription(toolAnnotation);
                        ToolEntry entry = new ToolEntry(bean, method, description);
                        registry.put(toolName, entry);
                        log.info("Registered tool: {} -> {}.{}() - {}",
                                toolName, targetClass.getSimpleName(), method.getName(), description);
                    }
                }
            } catch (Exception e) {
                // Skip beans that can't be introspected (e.g., infrastructure beans)
                log.debug("Skipping bean {} during tool scan: {}", beanName, e.getMessage());
            }
        }
        log.info("ToolRegistry initialized with {} tools: {}", registry.size(), registry.keySet());
    }

    public ToolEntry lookup(String toolName) {
        return registry.get(toolName.trim().toLowerCase());
    }

    public List<ToolEntry> allTools() {
        return new ArrayList<>(registry.values());
    }

    public Set<String> toolNames() {
        return registry.keySet();
    }

    private String extractDescription(Tool tool) {
        String[] values = tool.value();
        if (values != null && values.length > 0) {
            return String.join(" ", values);
        }
        String name = tool.name();
        return (name != null && !name.isEmpty()) ? name : "No description";
    }

    public record ToolEntry(Object bean, Method method, String description) {
        public String name() {
            return method.getName().toLowerCase();
        }
    }
}
