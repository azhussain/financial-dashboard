package com.example.dashboard.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Shared audit store backed by Redis ({@code LPUSH agent:audit} + {@code LTRIM}).
 * Enabled with {@code audit.store=redis}; survives restarts and is visible to
 * every agent role — the Stage-1 stand-in for the DynamoDB audit table.
 */
@Component
@ConditionalOnProperty(name = "audit.store", havingValue = "redis")
public class RedisAuditStore implements AuditStore {

    private static final Logger log = LoggerFactory.getLogger(RedisAuditStore.class);
    private static final String KEY = "agent:audit";
    private static final int CAPACITY = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper = new ObjectMapper();

    public RedisAuditStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void record(TaskAuditEntry entry) {
        try {
            redis.opsForList().leftPush(KEY, mapper.writeValueAsString(entry));
            redis.opsForList().trim(KEY, 0, CAPACITY - 1);
        } catch (Exception e) {
            log.warn("Audit write failed for task {}: {}", entry.taskId(), e.getMessage());
        }
    }

    @Override
    public List<TaskAuditEntry> recent(int limit) {
        List<String> raw = redis.opsForList().range(KEY, 0, Math.max(1, limit) - 1);
        if (raw == null) {
            return List.of();
        }
        return raw.stream().map(this::read).filter(Objects::nonNull).toList();
    }

    private TaskAuditEntry read(String json) {
        try {
            return mapper.readValue(json, TaskAuditEntry.class);
        } catch (Exception e) {
            return null;
        }
    }
}
