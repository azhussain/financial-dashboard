package com.example.dashboard.agent;

import java.util.Set;

/**
 * A worker agent in the mesh: declares which task types it handles and
 * executes them. The orchestrator routes purely on {@link #taskTypes()}, so
 * adding a role (research, analyst, ...) is a new bean, not a code change.
 */
public interface AgentWorker {

    Set<String> taskTypes();

    /** @return worker-defined result payload, evaluated before acceptance */
    Object execute(AgentTask task) throws Exception;
}
