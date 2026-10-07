package com.example.dashboard.agent;

import java.util.List;

/** Shared-state audit trail for the agent mesh. */
public interface AuditStore {

    void record(TaskAuditEntry entry);

    /** @return most recent entries, newest first */
    List<TaskAuditEntry> recent(int limit);
}
