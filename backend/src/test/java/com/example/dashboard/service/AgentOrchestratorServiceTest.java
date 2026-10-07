package com.example.dashboard.service;

import com.example.dashboard.agent.AgentTask;
import com.example.dashboard.agent.AgentWorker;
import com.example.dashboard.agent.DeterministicResultEvaluator;
import com.example.dashboard.agent.InMemoryAuditStore;
import com.example.dashboard.agent.InMemoryTaskQueue;
import com.example.dashboard.agent.TaskResult;
import com.example.dashboard.dto.QuoteSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOrchestratorServiceTest {

    private AgentOrchestratorService orchestrator;
    private InMemoryAuditStore audit;

    private static AgentWorker worker(String type, java.util.function.Function<AgentTask, Object> fn) {
        return new AgentWorker() {
            @Override
            public Set<String> taskTypes() {
                return Set.of(type);
            }

            @Override
            public Object execute(AgentTask task) throws Exception {
                return fn.apply(task);
            }
        };
    }

    private TaskResult await(String taskId) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            TaskResult r = orchestrator.status(taskId);
            if (r != null && !"PENDING".equals(r.status())) {
                return r;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("task did not finish");
    }

    @BeforeEach
    void setUp() {
        audit = new InMemoryAuditStore();
    }

    @AfterEach
    void tearDown() {
        if (orchestrator != null) {
            orchestrator.stop();
        }
    }

    @Test
    void executesTaskAndRecordsPassResult() throws Exception {
        orchestrator = new AgentOrchestratorService(new InMemoryTaskQueue(),
                new DeterministicResultEvaluator(), audit,
                java.util.List.of(worker("ECHO", t -> t.payload().get("value"))));
        orchestrator.start();

        String id = orchestrator.submit("echo", Map.of("value", "hello"));
        TaskResult r = await(id);

        assertEquals("PASS", r.status());
        assertEquals("hello", r.data());
        assertEquals(1, audit.recent(10).size());
        assertEquals("ECHO", audit.recent(10).get(0).type());
    }

    @Test
    void unknownTaskTypeFailsFast() throws Exception {
        orchestrator = new AgentOrchestratorService(new InMemoryTaskQueue(),
                new DeterministicResultEvaluator(), audit, java.util.List.of());
        orchestrator.start();

        TaskResult r = await(orchestrator.submit("NOSUCH", Map.of()));

        assertEquals("FAIL", r.status());
        assertTrue(r.reason().contains("no worker"));
    }

    @Test
    void evaluatorRejectionRetriesOnceThenFails() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        QuoteSummary badQuote = new QuoteSummary("X", "X", null, null,
                null, null, null, null, null); // price null → evaluator fail
        orchestrator = new AgentOrchestratorService(new InMemoryTaskQueue(),
                new DeterministicResultEvaluator(), audit,
                java.util.List.of(worker("QUOTE_LOOKUP", t -> {
                    calls.incrementAndGet();
                    return badQuote;
                })));
        orchestrator.start();

        TaskResult r = await(orchestrator.submit("QUOTE_LOOKUP", Map.of("symbol", "X")));

        assertEquals("FAIL", r.status());
        assertEquals(2, calls.get()); // initial attempt + one retry
        assertEquals(2, audit.recent(10).get(0).attempts());
    }

    @Test
    void workerExceptionBecomesError() throws Exception {
        orchestrator = new AgentOrchestratorService(new InMemoryTaskQueue(),
                new DeterministicResultEvaluator(), audit,
                java.util.List.of(worker("BOOM", t -> {
                    throw new RuntimeException("kaboom");
                })));
        orchestrator.start();

        TaskResult r = await(orchestrator.submit("BOOM", Map.of()));

        assertEquals("ERROR", r.status());
        assertEquals("kaboom", r.reason());
    }

    @Test
    void unknownTaskIdStatusIsNull() {
        orchestrator = new AgentOrchestratorService(new InMemoryTaskQueue(),
                new DeterministicResultEvaluator(), audit, java.util.List.of());
        assertNull(orchestrator.status("no-such-task"));
        assertNotNull(orchestrator.submit("X", null)); // null payload tolerated
    }
}
