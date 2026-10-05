package com.example.dashboard.client;

import org.springframework.stereotype.Component;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes2.QueryInterval;
import yahoofinance.query2v8.HistQuotesQuery2V8Request;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;

@Component
public class YahooFinanceClient {

    public List<HistoricalQuote> getHistory(String symbol, Calendar from, Calendar to) throws IOException {
        HistQuotesQuery2V8Request request =
                new HistQuotesQuery2V8Request(symbol.toUpperCase(), from, to, QueryInterval.DAILY);
        return request.getResult();
    }
}
