package com.example.dashboard.agent;

import com.example.dashboard.dto.MarketQuote;
import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.RegionSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicResultEvaluatorTest {

    private final DeterministicResultEvaluator evaluator = new DeterministicResultEvaluator();

    private static AgentTask task(String type) {
        return new AgentTask("id", type, Map.of(), 1, 0);
    }

    private static QuoteSummary quote(BigDecimal price, String currency) {
        return new QuoteSummary("AAPL", "Apple", price, BigDecimal.ONE,
                BigDecimal.TEN, currency, 1L, "NasdaqGS", "REGULAR");
    }

    private static RegionSnapshot snapshot(List<MarketQuote> quotes, boolean stale) {
        return new RegionSnapshot("AMERICAS", quotes, 1L, 1L, 0, stale, true);
    }

    private static MarketQuote marketQuote() {
        return new MarketQuote("AAPL", "Apple", "NasdaqGS", BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, "USD", 1L, 1L, "REGULAR", false);
    }

    @Test
    void nullResultFails() {
        assertFalse(evaluator.evaluate(task("X"), null).pass());
    }

    @Test
    void validQuotePasses() {
        assertTrue(evaluator.evaluate(task(DataAgentWorker.QUOTE_LOOKUP),
                quote(BigDecimal.TEN, "USD")).pass());
    }

    @Test
    void quoteWithNullPriceFails() {
        assertFalse(evaluator.evaluate(task(DataAgentWorker.QUOTE_LOOKUP),
                quote(null, "USD")).pass());
    }

    @Test
    void quoteWithNonPositivePriceFails() {
        assertFalse(evaluator.evaluate(task(DataAgentWorker.QUOTE_LOOKUP),
                quote(BigDecimal.ZERO, "USD")).pass());
    }

    @Test
    void quoteWithoutCurrencyFails() {
        assertFalse(evaluator.evaluate(task(DataAgentWorker.QUOTE_LOOKUP),
                quote(BigDecimal.TEN, " ")).pass());
    }

    @Test
    void populatedSnapshotPasses() {
        assertTrue(evaluator.evaluate(task(DataAgentWorker.REGION_SNAPSHOT),
                snapshot(List.of(marketQuote()), false)).pass());
    }

    @Test
    void emptyOrStaleSnapshotFails() {
        assertFalse(evaluator.evaluate(task(DataAgentWorker.REGION_SNAPSHOT),
                snapshot(List.of(), false)).pass());
        assertFalse(evaluator.evaluate(task(DataAgentWorker.REGION_SNAPSHOT),
                snapshot(List.of(marketQuote()), true)).pass());
    }

    @Test
    void refreshVerdictFollowsAcceptedFlag() {
        assertTrue(evaluator.evaluate(task(DataAgentWorker.REGION_REFRESH),
                Map.of("accepted", true)).pass());
        assertFalse(evaluator.evaluate(task(DataAgentWorker.REGION_REFRESH),
                Map.of("accepted", false)).pass());
        assertFalse(evaluator.evaluate(task(DataAgentWorker.REGION_REFRESH),
                "not-a-map").pass());
    }

    @Test
    void unknownTypePassesAnyNonNullResult() {
        assertTrue(evaluator.evaluate(task("SOMETHING_ELSE"), "ok").pass());
    }
}
