package com.example.dashboard.controller;

import com.example.dashboard.agent.TaskResult;
import com.example.dashboard.dto.TaskRequest;
import com.example.dashboard.service.AgentOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Task submission/status surface for the agent mesh. Everything here sits
 * behind {@code GatewayAuthFilter} ({@code /api/agent/**}).
 */
@RestController
@RequestMapping("/api/agent/tasks")
public class AgentTaskController {

    private static final int MAX_TYPE_LEN = 64;

    private final AgentOrchestratorService orchestrator;

    public AgentTaskController(AgentOrchestratorService orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** Submit a task; returns 202 + taskId for status polling. */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody(required = false) TaskRequest request) {
        String type = request != null && request.type() != null
                ? request.type().trim().toUpperCase() : "";
        if (type.isEmpty() || type.length() > MAX_TYPE_LEN) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "type must be 1-64 characters"));
        }
        String taskId = orchestrator.submit(type, request.payload());
        return ResponseEntity.accepted()
                .body(Map.of("taskId", taskId, "status", "PENDING"));
    }

    /** Poll a task's outcome: PENDING / PASS / FAIL / ERROR. */
    @GetMapping("/{taskId}")
    public ResponseEntity<?> status(@PathVariable String taskId) {
        TaskResult result = orchestrator.status(taskId);
        return result != null
                ? ResponseEntity.ok(result)
                : ResponseEntity.notFound().build();
    }

    /** Recent audit entries (newest first), for ops/debugging. */
    @GetMapping("/audit")
    public ResponseEntity<?> audit(@RequestParam(defaultValue = "50") int n) {
        return ResponseEntity.ok(orchestrator.audit(Math.min(n, 200)));
    }
}
