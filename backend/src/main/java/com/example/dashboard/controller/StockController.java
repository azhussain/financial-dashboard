package com.example.dashboard.controller;

import com.example.dashboard.dto.Candle;
import com.example.dashboard.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
public class StockController {

    private static final Logger log = LoggerFactory.getLogger(StockController.class);

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping("/{symbol}/history")
    public ResponseEntity<?> getHistory(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        if (from.isAfter(to)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Start date must be before end date"));
        }

        try {
            List<Candle> candles = stockService.getHistory(symbol, from, to);
            return ResponseEntity.ok(candles);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch stock data for {}", symbol, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch stock data"));
        }
    }

    @GetMapping("/{symbol}/quote")
    public ResponseEntity<?> getQuote(@PathVariable String symbol) {
        try {
            return ResponseEntity.ok(stockService.getQuote(symbol));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to fetch quote for {}", symbol, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch stock data"));
        }
    }

    @GetMapping("/top")
    public ResponseEntity<?> getTopStocks(
            @RequestParam(defaultValue = "5") int limit) {

        if (limit < 1 || limit > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Limit must be between 1 and 20"));
        }

        try {
            return ResponseEntity.ok(stockService.getTopStocks(limit));
        } catch (Exception e) {
            log.error("Failed to fetch top stocks", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch top stocks"));
        }
    }

    @GetMapping("/markets")
    public ResponseEntity<?> getTopStocksByRegion(
            @RequestParam(defaultValue = "5") int limit) {

        if (limit < 1 || limit > 20) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Limit must be between 1 and 20"));
        }

        try {
            return ResponseEntity.ok(stockService.getTopStocksByRegion(limit));
        } catch (Exception e) {
            log.error("Failed to fetch regional top stocks", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch top stocks"));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q) {
        String query = q.trim();
        if (query.isEmpty() || query.length() > 50) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Query must be 1-50 characters"));
        }

        try {
            return ResponseEntity.ok(stockService.search(query, 8));
        } catch (Exception e) {
            log.error("Failed to search stocks for query {}", query, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to search stocks"));
        }
    }
}
