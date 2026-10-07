package com.example.dashboard.agent;

import com.example.dashboard.service.StockService;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * APAC coverage: large caps on HKEX, TSE (Tokyo), KRX (Korea), TWSE (Taiwan),
 * and ASX (Australia). Regular-session prices only; top 20 by USD-normalized
 * market cap, refreshed across the full pool each cycle.
 */
@Component
public class ApacMarketWorker implements RegionalMarketWorker {

    private final StockService stockService;

    public ApacMarketWorker(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public String region() {
        return "APAC";
    }

    @Override
    public List<String> candidates() {
        return stockService.regionCandidates(region());
    }

    @Override
    public List<TradingWindow> tradingWindows() {
        // Any open session counts — APAC spans several time zones.
        // Lunch breaks are ignored (quotes continue updating anyway).
        return List.of(
                TradingWindow.weekdays(ZoneId.of("Asia/Tokyo"),
                        LocalTime.of(9, 0), LocalTime.of(15, 0)),
                TradingWindow.weekdays(ZoneId.of("Asia/Hong_Kong"),
                        LocalTime.of(9, 30), LocalTime.of(16, 0)),
                TradingWindow.weekdays(ZoneId.of("Asia/Seoul"),
                        LocalTime.of(9, 0), LocalTime.of(15, 30)),
                TradingWindow.weekdays(ZoneId.of("Asia/Taipei"),
                        LocalTime.of(9, 0), LocalTime.of(13, 30)),
                TradingWindow.weekdays(ZoneId.of("Australia/Sydney"),
                        LocalTime.of(10, 0), LocalTime.of(16, 0)));
    }
}
