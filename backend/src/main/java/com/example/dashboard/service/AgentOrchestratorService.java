package com.example.dashboard.service;

import com.example.dashboard.agent.AgentTask;
import com.example.dashboard.agent.AgentWorker;
import com.example.dashboard.agent.AuditStore;
import com.example.dashboard.agent.ResultEvaluator;
import com.example.dashboard.agent.TaskAuditEntry;
import com.example.dashboard.agent.TaskQueue;
import com.example.dashboard.agent.TaskResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Orchestrator: accepts agent tasks, queues them, routes each to the
 * worker that declares its type, runs the result through the evaluator, and
 * records every outcome to the shared audit store. Evaluator failures are
 * requeued up to {@link #MAX_ATTEMPTS}; worker exceptions become {@code ERROR}.
 *
 * Runs on a single consumer thread — the queue and worker map are the
 * extension points (SQS + per-role containers in Stage 2/3) without changing
 * this flow.
 */
@Service
public class AgentOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorService.class);

    static final int MAX_ATTEMPTS = 2;
    private static final long POLL_MS = 500;
    private static final int RESULT_CAP = 2000;

    private final TaskQueue queue;
    private final ResultEvaluator evaluator;
    private final AuditStore audit;
    private final Map<String, AgentWorker> workers;
    private final Map<String, TaskResult> results = new ConcurrentHashMap<>();
    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private volatile boolean running = true;

    public AgentOrchestratorService(TaskQueue queue, ResultEvaluator evaluator,
                                    AuditStore audit, List<AgentWorker> workerList) {
        this.queue = queue;
        this.evaluator = evaluator;
        this.audit = audit;
        this.workers = new ConcurrentHashMap<>();
        workerList.forEach(w -> w.taskTypes().forEach(t -> workers.put(t, w)));
    }

    @PostConstruct
    void start() {
        runner.submit(this::loop);
    }

    @PreDestroy
    void stop() {
        running = false;
        runner.shutdownNow();
    }

    /** Enqueue a task; returns its id for status polling. */
    public String submit(String type, Map<String, Object> payload) {
        AgentTask task = new AgentTask(UUID.randomUUID().toString(),
                type == null ? "" : type.trim().toUpperCase(),
                payload == null ? Map.of() : payload, 1, System.currentTimeMillis());
        results.put(task.id(), TaskResult.pending(task.id()));
        queue.enqueue(task);
        return task.id();
    }

    /** @return the task's current result, or null if the id is unknown. */
    public TaskResult status(String taskId) {
        return results.get(taskId);
    }

    public List<TaskAuditEntry> audit(int limit) {
        return audit.recent(limit);
    }

    private void loop() {
        while (running) {
            try {
                AgentTask task = queue.poll(POLL_MS);
                if (task != null) {
                    execute(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("Orchestrator loop error", e);
            }
        }
    }

    private void execute(AgentTask task) {
        long started = System.currentTimeMillis();
        AgentWorker worker = workers.get(task.type());
        if (worker == null) {
            finish(task, "FAIL", null, "no worker for type " + task.type(), started);
            return;
        }
        try {
            Object result = worker.execute(task);
            ResultEvaluator.Verdict verdict = evaluator.evaluate(task, result);
            if (verdict.pass()) {
                finish(task, "PASS", result, null, started);
            } else if (task.attempts() < MAX_ATTEMPTS) {
                log.info("Task {} ({}) failed evaluation ({}), retrying",
                        task.id(), task.type(), verdict.reason());
                queue.enqueue(task.retry());
            } else {
                finish(task, "FAIL", null, verdict.reason(), started);
            }
        } catch (Exception e) {
            finish(task, "ERROR", null, e.getMessage(), started);
        }
    }

    private void finish(AgentTask task, String status, Object data, String reason,
                        long started) {
        long now = System.currentTimeMillis();
        evictIfNeeded();
        results.put(task.id(), new TaskResult(task.id(), status, data, reason, now));
        audit.record(new TaskAuditEntry(task.id(), task.type(), status, task.attempts(),
                now - started, reason, now));
        log.info("Agent task {} {} ({}) in {}ms{}", task.id(), status, task.type(),
                now - started, reason != null ? ": " + reason : "");
    }

    private void evictIfNeeded() {
        if (results.size() <= RESULT_CAP) {
            return;
        }
        results.values().removeIf(r -> !"PENDING".equals(r.status()));
    }
}
