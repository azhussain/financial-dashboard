package com.example.dashboard.agent;

/** Immutable audit record written for every task outcome. */
public record TaskAuditEntry(
        String taskId,
        String type,
        String status,
        int attempts,
        long durationMs,
        String reason,
        long timestamp
) {
}
