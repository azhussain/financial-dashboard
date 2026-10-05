package com.example.dashboard.dto;

public record SearchResult(
        String symbol,
        String name,
        String exchange,
        String quoteType
) {
}
