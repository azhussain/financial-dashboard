package com.example.dashboard.agent;

import com.example.dashboard.service.StockService;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * EMEA coverage: large caps on Euronext, Xetra, SIX, Nasdaq Copenhagen, and
 * Tadawul. London listings are excluded — Yahoo quotes them in pence, which
 * distorts price display. Regular-session prices only; top 20 by
 * USD-normalized market cap, refreshed across the full pool each cycle.
 */
@Component
public class EmeaMarketWorker implements RegionalMarketWorker {

    private final StockService stockService;

    public EmeaMarketWorker(StockService stockService) {
        this.stockService = stockService;
    }

    @Override
    public String region() {
        return "EMEA";
    }

    @Override
    public List<String> candidates() {
        return stockService.regionCandidates(region());
    }

    @Override
    public List<TradingWindow> tradingWindows() {
        return List.of(
                // Euronext, Xetra, SIX, Nasdaq Copenhagen (Europe/Paris ≈ CET)
                TradingWindow.weekdays(ZoneId.of("Europe/Paris"),
                        LocalTime.of(9, 0), LocalTime.of(17, 30)),
                // Tadawul trades Sunday–Thursday
                new TradingWindow(ZoneId.of("Asia/Riyadh"),
                        LocalTime.of(10, 0), LocalTime.of(15, 0),
                        Set.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
                                DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)));
    }
}
