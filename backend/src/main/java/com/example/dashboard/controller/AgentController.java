package com.example.dashboard.controller;

import com.example.dashboard.dto.ChatRequest;
import com.example.dashboard.service.MarketAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final MarketAgentService agentService;

    public AgentController(MarketAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody(required = false) ChatRequest request) {
        if (!agentService.isEnabled()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "AI agent is not configured. Set OPENAI_API_KEY."));
        }

        String message = request != null && request.message() != null
                ? request.message().trim() : "";
        if (message.isEmpty() || message.length() > 1000) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message must be 1-1000 characters"));
        }

        String sessionId = request != null && request.sessionId() != null
                ? request.sessionId().trim() : "";
        if (sessionId.length() > 64) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "sessionId must be at most 64 characters"));
        }

        try {
            return ResponseEntity.ok(
                    agentService.chat(message, sessionId.isEmpty() ? "default" : sessionId));
        } catch (Exception e) {
            log.error("Agent chat failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process the request"));
        }
    }
}
