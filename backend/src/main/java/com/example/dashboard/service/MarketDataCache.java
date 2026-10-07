package com.example.dashboard.service;

import com.example.dashboard.dto.MarketQuote;
import com.example.dashboard.dto.RegionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared cache of the latest valid market snapshot per region. A failed
 * refresh never clears previously valid quotes — they are retained and marked
 * stale at the snapshot level (and per quote) instead.
 */
@Component
public class MarketDataCache {

    private final Map<String, RegionSnapshot> snapshots = new ConcurrentHashMap<>();

    /**
     * Store a successful refresh and return only the quotes whose displayed
     * values changed versus the previous snapshot.
     */
    public synchronized List<MarketQuote> applySuccess(
            String region, List<MarketQuote> quotes, long now, boolean marketOpen) {
        Map<String, MarketQuote> prev = quotesOf(region);
        List<MarketQuote> changed = quotes.stream()
                .filter(q -> displayedFieldsDiffer(prev.get(q.symbol()), q))
                .toList();
        snapshots.put(region, new RegionSnapshot(region, quotes, now, now, 0, false, marketOpen));
        return changed;
    }

    /** Mark the region stale after a failed refresh, retaining prior quotes. */
    public synchronized void applyFailure(String region, long now, boolean marketOpen) {
        RegionSnapshot prev = snapshots.get(region);
        List<MarketQuote> retained = prev == null ? List.of()
                : prev.quotes().stream().map(MarketQuote::asStale).toList();
        snapshots.put(region, new RegionSnapshot(region, retained,
                prev == null ? 0 : prev.lastSuccessAt(), now,
                (prev == null ? 0 : prev.consecutiveFailures()) + 1, true, marketOpen));
    }

    public RegionSnapshot snapshot(String region) {
        return snapshots.get(region);
    }

    public Map<String, RegionSnapshot> snapshots() {
        return Map.copyOf(snapshots);
    }

    private Map<String, MarketQuote> quotesOf(String region) {
        RegionSnapshot prev = snapshots.get(region);
        return prev == null ? Map.of()
                : prev.quotes().stream()
                        .collect(Collectors.toMap(MarketQuote::symbol, Function.identity(), (a, b) -> a));
    }

    private static boolean displayedFieldsDiffer(MarketQuote prev, MarketQuote next) {
        if (prev == null) {
            return true;
        }
        return differs(prev.price(), next.price())
                || differs(prev.changePercent(), next.changePercent())
                || differs(prev.marketCap(), next.marketCap())
                || !Objects.equals(prev.marketStatus(), next.marketStatus())
                || !Objects.equals(prev.sourceTimestamp(), next.sourceTimestamp());
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        return a == null ? b != null : b == null || a.compareTo(b) != 0;
    }
}
