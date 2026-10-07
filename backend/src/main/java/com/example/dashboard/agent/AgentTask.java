package com.example.dashboard.agent;

import java.util.Map;

/**
 * One unit of work routed through the agent mesh. {@code type} selects the
 * worker (e.g. {@code QUOTE_LOOKUP}, {@code REGION_SNAPSHOT}); {@code payload}
 * is worker-defined input; {@code attempts} tracks retry budget.
 */
public record AgentTask(
        String id,
        String type,
        Map<String, Object> payload,
        int attempts,
        long createdAt
) {
    public AgentTask retry() {
        return new AgentTask(id, type, payload, attempts + 1, createdAt);
    }
}
