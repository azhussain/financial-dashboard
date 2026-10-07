package com.example.dashboard.service;

import com.example.dashboard.dto.MarketQuote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataCacheTest {

    private final MarketDataCache cache = new MarketDataCache();

    private static MarketQuote quote(String symbol, String price, long fetchTime) {
        return new MarketQuote(symbol, symbol + " Inc", "NasdaqGS",
                new BigDecimal(price), BigDecimal.ONE, new BigDecimal("1000000"),
                "USD", 1_700_000_000L, fetchTime, "REGULAR", false);
    }

    @Test
    void firstSuccessReportsAllQuotesAsChanged() {
        List<MarketQuote> changed = cache.applySuccess("AMERICAS",
                List.of(quote("AAPL", "100", 1), quote("MSFT", "200", 1)), 1, true);

        assertThat(changed).extracting(MarketQuote::symbol)
                .containsExactly("AAPL", "MSFT");
        assertThat(cache.snapshot("AMERICAS").stale()).isFalse();
    }

    @Test
    void unchangedDisplayedValuesProduceNoUpdates() {
        cache.applySuccess("AMERICAS",
                List.of(quote("AAPL", "100", 1), quote("MSFT", "200", 1)), 1, true);

        // freshness metadata advances even though values are identical
        List<MarketQuote> changed = cache.applySuccess("AMERICAS",
                List.of(quote("AAPL", "100", 2), quote("MSFT", "200", 2)), 2, true);

        assertThat(changed).isEmpty();
        assertThat(cache.snapshot("AMERICAS").lastSuccessAt()).isEqualTo(2);
        assertThat(cache.snapshot("AMERICAS").quotes().get(0).lastFetchTime()).isEqualTo(2);
    }

    @Test
    void onlyChangedQuotesAreReported() {
        cache.applySuccess("AMERICAS",
                List.of(quote("AAPL", "100", 1), quote("MSFT", "200", 1)), 1, true);

        List<MarketQuote> changed = cache.applySuccess("AMERICAS",
                List.of(quote("AAPL", "101", 2), quote("MSFT", "200", 2)), 2, true);

        assertThat(changed).extracting(MarketQuote::symbol).containsExactly("AAPL");
    }

    @Test
    void failureRetainsValidQuotesAndMarksStale() {
        cache.applySuccess("AMERICAS", List.of(quote("AAPL", "100", 1)), 1000, true);

        cache.applyFailure("AMERICAS", 2000, false);

        var snapshot = cache.snapshot("AMERICAS");
        assertThat(snapshot.quotes()).extracting(MarketQuote::symbol).containsExactly("AAPL");
        assertThat(snapshot.stale()).isTrue();
        assertThat(snapshot.lastSuccessAt()).isEqualTo(1000);
        assertThat(snapshot.lastAttemptAt()).isEqualTo(2000);
        assertThat(snapshot.consecutiveFailures()).isEqualTo(1);
        assertThat(snapshot.quotes().get(0).stale()).isTrue();
    }

    @Test
    void failedResponsesNeverReplaceValidValues() {
        cache.applySuccess("AMERICAS", List.of(quote("AAPL", "100", 1)), 1000, true);
        cache.applyFailure("AMERICAS", 2000, false);
        cache.applyFailure("AMERICAS", 3000, false);

        var snapshot = cache.snapshot("AMERICAS");
        assertThat(snapshot.quotes().get(0).price()).isEqualByComparingTo("100");
        assertThat(snapshot.consecutiveFailures()).isEqualTo(2);

        // recovery clears the stale flag
        cache.applySuccess("AMERICAS", List.of(quote("AAPL", "101", 4)), 4000, true);
        assertThat(cache.snapshot("AMERICAS").stale()).isFalse();
        assertThat(cache.snapshot("AMERICAS").consecutiveFailures()).isZero();
    }
}
