package com.example.dashboard.controller;

import com.example.dashboard.service.MarketsAgentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-sent events stream for dashboard market data. A new subscriber
 * immediately receives a {@code snapshot} event with the shared cache, then
 * {@code market-update} events carrying only the quotes whose displayed values
 * changed (freshness metadata always advances).
 */
@RestController
@RequestMapping("/api/markets")
public class MarketStreamController {

    private final MarketsAgentService marketsAgentService;

    public MarketStreamController(MarketsAgentService marketsAgentService) {
        this.marketsAgentService = marketsAgentService;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return marketsAgentService.subscribe();
    }

    /** Trigger an out-of-band refresh for one region; changes fan out via SSE. */
    @PostMapping("/{region}/refresh")
    public ResponseEntity<Void> refresh(@PathVariable String region) {
        return marketsAgentService.refreshAsync(region)
                ? ResponseEntity.accepted().build()
                : ResponseEntity.notFound().build();
    }
}
