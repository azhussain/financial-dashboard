package com.example.dashboard.agent;

/**
 * Terminal (or in-flight) outcome of an {@link AgentTask}.
 * Status: {@code PENDING} queued/running, {@code PASS} evaluator-approved,
 * {@code FAIL} evaluator-rejected after retries, {@code ERROR} worker threw.
 */
public record TaskResult(
        String taskId,
        String status,
        Object data,
        String reason,
        long completedAt
) {
    public static TaskResult pending(String taskId) {
        return new TaskResult(taskId, "PENDING", null, null, 0);
    }
}
