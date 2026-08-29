package com.aiagent.agent.orchestrator;

/**
 * Worker Sub-Agent interface for Multi-Agent Collaboration.
 */
public interface SubAgent {

    /**
     * Unique identifier name for the Sub-Agent.
     */
    String getName();

    /**
     * Capability description for task routing.
     */
    String getDescription();

    /**
     * Evaluates if this sub-agent is suited to handle the sub-task.
     */
    boolean canHandle(String taskDescription);

    /**
     * Execute the assigned sub-task and return the result.
     */
    String execute(String taskDescription, String context);
}
