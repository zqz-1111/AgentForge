package com.aiagent.agent.workflow;

import java.util.Map;

public interface WorkflowWorker {
    String name();
    String description();
    Map<String, Class<?>> inputs();
    Class<?> outputType();
    Object execute(Map<String, Object> inputs, WorkflowRequest request);
}
