package com.example.dashboard.dto;

import java.util.List;

/**
 * Shared cached view of one region: the latest valid ranked quotes plus
 * freshness/health metadata.
 *
 * @param lastSuccessAt     epoch millis of the last refresh that produced data
 * @param lastAttemptAt     epoch millis of the most recent refresh attempt
 * @param consecutiveFailures how many refreshes in a row failed
 * @param stale             true when the quotes were fetched before the last
 *                          failed attempt (data retained, marked stale)
 * @param marketOpen        true when any of the region's exchanges is in session
 */
public record RegionSnapshot(
        String region,
        List<MarketQuote> quotes,
        long lastSuccessAt,
        long lastAttemptAt,
        int consecutiveFailures,
        boolean stale,
        boolean marketOpen
) {
}
