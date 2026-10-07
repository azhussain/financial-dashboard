package com.example.dashboard.service;

import com.example.dashboard.agent.RegionalMarketWorker;
import com.example.dashboard.dto.MarketQuote;
import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.RegionSnapshot;
import com.example.dashboard.agent.TradingWindow;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Markets Agent: runs one shared refresh loop across all users for every
 * registered regional worker, keeps {@link MarketDataCache} current, and fans
 * updates out to connected dashboards over SSE.
 *
 * Scheduling: a single-threaded executor self-schedules the next cycle only
 * after the current one finishes, so refreshes can never overlap. Cadence is
 * 5 minutes after a success; after failures it retries with bounded backoff
 * (30s, 60s, … capped at the 5-minute cadence).
 */
@Service
public class MarketsAgentService {

    private static final Logger log = LoggerFactory.getLogger(MarketsAgentService.class);

    static final long REFRESH_MS = TimeUnit.MINUTES.toMillis(5);
    static final long CLOSED_REFRESH_MS = TimeUnit.MINUTES.toMillis(30);
    static final long FIRST_RETRY_MS = 30_000;
    static final long HEARTBEAT_MS = 25_000;
    private static final int TOP_N = 20;

    private final StockService stockService;
    private final MarketDataCache cache;
    private final List<RegionalMarketWorker> workers;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private Clock clock = Clock.systemUTC();

    public MarketsAgentService(StockService stockService, MarketDataCache cache,
                               List<RegionalMarketWorker> workers) {
        this.stockService = stockService;
        this.cache = cache;
        this.workers = workers;
    }

    /** Test hook: fixed clock for trading-window evaluation. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        workers.forEach(w -> scheduler.schedule(() -> runWorker(w), 0, TimeUnit.MILLISECONDS));
        scheduler.scheduleWithFixedDelay(this::heartbeat, HEARTBEAT_MS, HEARTBEAT_MS,
                TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }

    /** Delay before the next cycle: bounded exponential backoff after failures. */
    static long nextDelayMs(int consecutiveFailures) {
        if (consecutiveFailures <= 0) {
            return REFRESH_MS;
        }
        long delay = FIRST_RETRY_MS << Math.min(consecutiveFailures - 1, 8);
        return Math.min(delay, REFRESH_MS);
    }

    /**
     * Adaptive cadence: full rate while any of the region's exchanges is in
     * session, reduced rate when all are closed. Failures retry with bounded
     * backoff, never slower than the current base cadence.
     */
    static long delayAfterRefresh(boolean succeeded, boolean marketOpen, int failures) {
        long base = marketOpen ? REFRESH_MS : CLOSED_REFRESH_MS;
        return succeeded ? base : Math.min(nextDelayMs(failures), base);
    }

    private boolean isMarketOpen(RegionalMarketWorker worker) {
        List<TradingWindow> windows = worker.tradingWindows();
        Instant now = clock.instant();
        return windows.isEmpty() || windows.stream().anyMatch(w -> w.isOpen(now));
    }

    private void runWorker(RegionalMarketWorker worker) {
        boolean open = isMarketOpen(worker);
        boolean ok = refresh(worker, open);
        var snapshot = cache.snapshot(worker.region());
        int failures = snapshot == null ? 0 : snapshot.consecutiveFailures();
        long delay = delayAfterRefresh(ok, open, failures);
        log.debug("Markets agent: {} next refresh in {}s (market {})",
                worker.region(), delay / 1000, open ? "open" : "closed");
        scheduler.schedule(() -> runWorker(worker), delay, TimeUnit.MILLISECONDS);
    }

    /** @return true when the refresh produced valid data. */
    boolean refresh(RegionalMarketWorker worker) {
        return refresh(worker, isMarketOpen(worker));
    }

    boolean refresh(RegionalMarketWorker worker, boolean marketOpen) {
        String region = worker.region();
        long now = clock.millis();
        try {
            List<QuoteSummary> ranked =
                    stockService.rankCandidates(worker.candidates(), TOP_N);
            List<MarketQuote> quotes = ranked.stream()
                    .map(q -> toMarketQuote(q, now))
                    .toList();
            List<MarketQuote> changed = cache.applySuccess(region, quotes, now, marketOpen);
            RegionSnapshot snapshot = cache.snapshot(region);
            publish("market-update", new MarketUpdate(region, snapshot.lastSuccessAt(),
                    snapshot.lastAttemptAt(), snapshot.stale(),
                    snapshot.consecutiveFailures(), changed,
                    quotes.stream().map(MarketQuote::symbol).toList(),
                    snapshot.marketOpen()));
            log.info("Markets agent refreshed {}: {} quotes, {} changed (market {})",
                    region, quotes.size(), changed.size(), marketOpen ? "open" : "closed");
            return true;
        } catch (Exception e) {
            cache.applyFailure(region, now, marketOpen);
            RegionSnapshot snapshot = cache.snapshot(region);
            publish("market-update", new MarketUpdate(region, snapshot.lastSuccessAt(),
                    snapshot.lastAttemptAt(), snapshot.stale(),
                    snapshot.consecutiveFailures(), List.of(), List.of(), marketOpen));
            log.warn("Markets agent refresh failed for {} (attempts: {}): {}",
                    region, snapshot.consecutiveFailures(), e.getMessage());
            return false;
        }
    }

    private static MarketQuote toMarketQuote(QuoteSummary q, long now) {
        return new MarketQuote(q.symbol(), q.name(), q.exchange(), q.price(),
                q.changePercent(), q.marketCap(), q.currency(), q.marketTime(),
                now, q.marketState(), false);
    }

    /**
     * Queue a manual refresh for one region on the shared scheduler, so it can
     * never overlap a cycle already in progress. Returns false for unknown
     * regions.
     */
    public boolean refreshAsync(String region) {
        return workers.stream()
                .filter(w -> w.region().equalsIgnoreCase(region))
                .findFirst()
                .map(w -> {
                    scheduler.execute(() -> refresh(w));
                    return true;
                })
                .orElse(false);
    }

    /** Register a dashboard; it immediately receives the latest snapshot. */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(cache.snapshots()));
        } catch (IOException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private void publish(String event, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    private void heartbeat() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("hb"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** Payload of {@code market-update} events. */
    public record MarketUpdate(
            String region,
            long lastSuccessAt,
            long lastAttemptAt,
            boolean stale,
            int consecutiveFailures,
            List<MarketQuote> changed,
            List<String> order,
            boolean marketOpen
    ) {
    }
}
