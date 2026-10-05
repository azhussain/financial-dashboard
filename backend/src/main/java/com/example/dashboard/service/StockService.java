package com.example.dashboard.service;

import com.example.dashboard.client.YahooFinanceClient;
import com.example.dashboard.dto.Candle;
import org.springframework.stereotype.Service;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

@Service
public class StockService {

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

    private Calendar toCalendar(LocalDate date) {
        return GregorianCalendar.from(date.atStartOfDay(ZoneId.systemDefault()));
    }
}
