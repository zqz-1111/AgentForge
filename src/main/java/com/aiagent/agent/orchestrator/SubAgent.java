package com.aiagent.agent.orchestrator;

/**
 * 多智能体协作中的 Worker 子智能体接口。
 */
public interface SubAgent {

    /**
     * 子智能体的唯一标识。
     */
    String getName();

    /**
     * 供任务路由使用的能力描述。
     */
    String getDescription();

    /**
     * 判断此子智能体是否适合处理该子任务。
     */
    boolean canHandle(String taskDescription);

    /**
     * 执行分配的子任务并返回结果。
     */
    String execute(String taskDescription, String context);
}
