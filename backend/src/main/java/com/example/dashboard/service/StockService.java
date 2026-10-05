package com.example.dashboard.service;

import com.example.dashboard.client.YahooFinanceClient;
import com.example.dashboard.dto.Candle;
import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.SearchResult;
import org.springframework.stereotype.Service;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;

@Service
public class StockService {

    // Large-cap US candidates; the top N by market cap are returned.
    private static final List<String> TOP_US_CANDIDATES = List.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META",
            "AVGO", "TSLA", "BRK-B", "LLY", "JPM", "V");

    // Yahoo exchange codes for US stock exchanges.
    private static final Set<String> US_EXCHANGES =
            Set.of("NMS", "NYQ", "ASE", "NGM", "NCM", "PCX", "BTS");

    private final YahooFinanceClient yahooFinanceClient;

    public StockService(YahooFinanceClient yahooFinanceClient) {
        this.yahooFinanceClient = yahooFinanceClient;
    }

    public List<Candle> getHistory(String symbol, LocalDate from, LocalDate to) throws IOException {
        // Yahoo treats the end of the range as exclusive; add one day so the
        // user's selected end date is included in the result.
        Calendar fromCal = toCalendar(from);
        Calendar toCal = toCalendar(to.plusDays(1));

        List<HistoricalQuote> quotes = yahooFinanceClient.getHistory(symbol, fromCal, toCal);

        return quotes.stream()
                .filter(q -> q.getClose() != null)
                .map(q -> new Candle(
                        q.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                        q.getOpen(),
                        q.getHigh(),
                        q.getLow(),
                        q.getClose(),
                        q.getVolume()
                ))
                .toList();
    }

    public List<QuoteSummary> getTopStocks(int limit) throws IOException, InterruptedException {
        return yahooFinanceClient.getQuotes(TOP_US_CANDIDATES).stream()
                .filter(q -> q.marketCap() != null)
                .sorted(Comparator.comparing(QuoteSummary::marketCap).reversed())
                .limit(limit)
                .toList();
    }

    public List<SearchResult> search(String query, int limit) throws IOException, InterruptedException {
        return yahooFinanceClient.search(query).stream()
                .filter(r -> "EQUITY".equals(r.quoteType()) && US_EXCHANGES.contains(r.exchange()))
                .limit(limit)
                .toList();
    }

    private Calendar toCalendar(LocalDate date) {
        return GregorianCalendar.from(date.atStartOfDay(ZoneId.systemDefault()));
    }
}
