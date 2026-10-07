package com.example.dashboard.agent;

/**
 * Queue abstraction between the orchestrator and worker agents. Stage 1 uses
 * the in-memory implementation (single node); the interface mirrors SQS
 * semantics so a {@code SqsTaskQueue} can replace it in Stage 2 without
 * touching the orchestrator.
 */
public interface TaskQueue {

    void enqueue(AgentTask task);

    /** @return the next task, or {@code null} if none arrives before timeout */
    AgentTask poll(long timeoutMs) throws InterruptedException;
}
