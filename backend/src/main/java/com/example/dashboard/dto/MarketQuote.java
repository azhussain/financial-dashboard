package com.example.dashboard.dto;

import java.math.BigDecimal;

/**
 * One ranked market listing inside a region snapshot.
 *
 * @param sourceTimestamp epoch seconds reported by the quote source (Yahoo regularMarketTime)
 * @param lastFetchTime   epoch millis of the backend's last successful fetch
 * @param stale           true while a refresh has failed after the last success
 */
public record MarketQuote(
        String symbol,
        String name,
        String exchange,
        BigDecimal price,
        BigDecimal changePercent,
        BigDecimal marketCap,
        String currency,
        Long sourceTimestamp,
        long lastFetchTime,
        String marketStatus,
        boolean stale
) {
    public MarketQuote asStale() {
        return new MarketQuote(symbol, name, exchange, price, changePercent,
                marketCap, currency, sourceTimestamp, lastFetchTime, marketStatus, true);
    }
}
