package com.example.dashboard.agent;

import com.example.dashboard.service.StockService;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * AMERICAS coverage: primary large caps listed on NYSE/Nasdaq (US), major
 * NYSE-listed ADRs (e.g. PBR, ITUB), and TSX large caps (e.g. RY.TO, TD.TO).
 * Ranking uses the regular-session price (regularMarketPrice); extended-hours
 * quotes are not blended in. Top 20 = highest USD-normalized market cap across
 * the full candidate pool, re-fetched every cycle — refreshing only the
 * incumbent top 20 would miss new entrants.
 */
@Component
public class AmericasMarketWorker implements RegionalMarketWorker {

    private final StockService stockService;

    public AmericasMarketWorker(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public String region() {
        return "AMERICAS";
    }

    @Override
    public List<String> candidates() {
        return stockService.regionCandidates(region());
    }

    @Override
    public List<TradingWindow> tradingWindows() {
        // NYSE/Nasdaq and TSX share the America/Toronto-New_York session clock
        return List.of(TradingWindow.weekdays(
                ZoneId.of("America/New_York"), LocalTime.of(9, 30), LocalTime.of(16, 0)));
    }
}
