package com.example.dashboard.agent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Default audit store: bounded in-process ring buffer (newest first). */
@Component
@ConditionalOnProperty(name = "audit.store", havingValue = "inmemory", matchIfMissing = true)
public class InMemoryAuditStore implements AuditStore {

    private static final int CAPACITY = 500;

    private final Deque<TaskAuditEntry> entries = new ArrayDeque<>();

    @Override
    public synchronized void record(TaskAuditEntry entry) {
        entries.addFirst(entry);
        while (entries.size() > CAPACITY) {
            entries.removeLast();
        }
    }

    @Override
    public synchronized List<TaskAuditEntry> recent(int limit) {
        return entries.stream().limit(Math.max(1, limit)).collect(ArrayList::new,
                ArrayList::add, ArrayList::addAll);
    }
}
