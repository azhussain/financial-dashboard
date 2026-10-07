package com.example.dashboard.service;

import com.example.dashboard.agent.RegionalMarketWorker;
import com.example.dashboard.agent.TradingWindow;
import com.example.dashboard.dto.QuoteSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketsAgentServiceTest {

    @Mock
    private StockService stockService;

    private final MarketDataCache cache = new MarketDataCache();

    private static RegionalMarketWorker worker(String region, List<String> candidates) {
        return new RegionalMarketWorker() {
            @Override
            public String region() {
                return region;
            }

            @Override
            public List<String> candidates() {
                return candidates;
            }
        };
    }

    private static RegionalMarketWorker workerWithWindow(
            String region, List<String> candidates, List<TradingWindow> windows) {
        return new RegionalMarketWorker() {
            @Override
            public String region() {
                return region;
            }

            @Override
            public List<String> candidates() {
                return candidates;
            }

            @Override
            public List<TradingWindow> tradingWindows() {
                return windows;
            }
        };
    }

    private MarketsAgentService agent(List<RegionalMarketWorker> workers) {
        // constructed without @PostConstruct — cycles are driven manually
        return new MarketsAgentService(stockService, cache, workers);
    }

    private MarketsAgentService agentAt(List<RegionalMarketWorker> workers, Instant now) {
        MarketsAgentService agent = new MarketsAgentService(stockService, cache, workers);
        agent.setClock(Clock.fixed(now, ZoneOffset.UTC));
        return agent;
    }

    @Test
    void nextDelayIsFiveMinutesAfterSuccessAndBoundedOnFailures() {
        assertThat(MarketsAgentService.nextDelayMs(0)).isEqualTo(300_000);
        assertThat(MarketsAgentService.nextDelayMs(1)).isEqualTo(30_000);
        assertThat(MarketsAgentService.nextDelayMs(2)).isEqualTo(60_000);
        assertThat(MarketsAgentService.nextDelayMs(3)).isEqualTo(120_000);
        assertThat(MarketsAgentService.nextDelayMs(4)).isEqualTo(240_000);
        assertThat(MarketsAgentService.nextDelayMs(5)).isEqualTo(300_000); // capped
        assertThat(MarketsAgentService.nextDelayMs(20)).isEqualTo(300_000);
    }

    @Test
    void adaptiveCadenceSlowsWhenMarketClosed() {
        assertThat(MarketsAgentService.delayAfterRefresh(true, true, 0))
                .isEqualTo(300_000);            // open → 5 min
        assertThat(MarketsAgentService.delayAfterRefresh(true, false, 0))
                .isEqualTo(1_800_000);          // closed → 30 min
        assertThat(MarketsAgentService.delayAfterRefresh(false, true, 2))
                .isEqualTo(60_000);             // open + failure → backoff
        assertThat(MarketsAgentService.delayAfterRefresh(false, false, 10))
                .isEqualTo(300_000);            // closed + failure → backoff capped at 5 min
    }

    @Test
    void snapshotRecordsMarketClosedOutsideTradingWindow() throws Exception {
        // Tokyo-only window: 2025-01-05 is a Sunday → market closed
        RegionalMarketWorker tokyo = workerWithWindow("APAC", List.of("7203.T"),
                List.of(TradingWindow.weekdays(ZoneId.of("Asia/Tokyo"),
                        LocalTime.of(9, 0), LocalTime.of(15, 0))));
        when(stockService.rankCandidates(anyList(), anyInt())).thenReturn(List.of(
                new QuoteSummary("7203.T", "Toyota", new BigDecimal("2800"),
                        BigDecimal.ONE, new BigDecimal("40000000000000"), "JPY",
                        1_700_000_000L, "TSE", "CLOSED")));

        MarketsAgentService agent = agentAt(List.of(tokyo),
                Instant.parse("2025-01-05T12:00:00Z"));
        agent.refresh(tokyo);

        assertThat(cache.snapshot("APAC").marketOpen()).isFalse();

        // Monday 10:00 JST = 01:00 UTC → market open
        MarketsAgentService openAgent = agentAt(List.of(tokyo),
                Instant.parse("2025-01-06T01:00:00Z"));
        openAgent.refresh(tokyo);

        assertThat(cache.snapshot("APAC").marketOpen()).isTrue();
    }

    @Test
    void refreshStoresRankedQuotesWithFreshnessMetadata() throws Exception {
        RegionalMarketWorker americas = worker("AMERICAS", List.of("AAPL"));
        when(stockService.rankCandidates(anyList(), anyInt())).thenReturn(List.of(
                new QuoteSummary("AAPL", "Apple Inc.", new BigDecimal("200"),
                        new BigDecimal("1.5"), new BigDecimal("3000000"), "USD",
                        1_700_000_000L, "NasdaqGS", "REGULAR")));

        MarketsAgentService agent = agent(List.of(americas));
        assertThat(agent.refresh(americas)).isTrue();

        var snapshot = cache.snapshot("AMERICAS");
        assertThat(snapshot.quotes()).hasSize(1);
        var q = snapshot.quotes().get(0);
        assertThat(q.exchange()).isEqualTo("NasdaqGS");
        assertThat(q.marketStatus()).isEqualTo("REGULAR");
        assertThat(q.sourceTimestamp()).isEqualTo(1_700_000_000L);
        assertThat(q.lastFetchTime()).isPositive();
        assertThat(q.stale()).isFalse();
        assertThat(snapshot.lastSuccessAt()).isPositive();
    }

    @Test
    void failedRefreshRetainsLastValidDataAndMarksStale() throws Exception {
        RegionalMarketWorker americas = worker("AMERICAS", List.of("AAPL"));
        when(stockService.rankCandidates(anyList(), anyInt())).thenReturn(List.of(
                new QuoteSummary("AAPL", "Apple Inc.", new BigDecimal("200"),
                        BigDecimal.ONE, new BigDecimal("3000000"), "USD",
                        1_700_000_000L, "NasdaqGS", "REGULAR")));

        MarketsAgentService agent = agent(List.of(americas));
        agent.refresh(americas);

        when(stockService.rankCandidates(anyList(), anyInt()))
                .thenThrow(new IOException("yahoo down"));

        assertThat(agent.refresh(americas)).isFalse();

        var snapshot = cache.snapshot("AMERICAS");
        assertThat(snapshot.stale()).isTrue();
        assertThat(snapshot.consecutiveFailures()).isEqualTo(1);
        assertThat(snapshot.quotes()).hasSize(1);
        assertThat(snapshot.quotes().get(0).price()).isEqualByComparingTo("200");
    }
}
