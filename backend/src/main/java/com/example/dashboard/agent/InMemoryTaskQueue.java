package com.example.dashboard.agent;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Single-node queue backed by a {@link LinkedBlockingQueue}. */
@Component
public class InMemoryTaskQueue implements TaskQueue {

    private final BlockingQueue<AgentTask> queue = new LinkedBlockingQueue<>();

    @Override
    public void enqueue(AgentTask task) {
        queue.add(task);
    }

    @Override
    public AgentTask poll(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }
}
