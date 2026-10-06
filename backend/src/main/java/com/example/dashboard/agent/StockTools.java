package com.example.dashboard.agent;

import com.example.dashboard.dto.Candle;
import com.example.dashboard.dto.ChartPayload;
import com.example.dashboard.service.StockService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class StockTools {

    private final StockService stockService;

    // Charts fetched by tools during a single chat turn, for the UI to render.
    private final ThreadLocal<List<ChartPayload>> collectedCharts = new ThreadLocal<>();

    public StockTools(StockService stockService) {
        this.stockService = stockService;
    }

    public void beginCollecting() {
        collectedCharts.set(new ArrayList<>());
    }

    public List<ChartPayload> collectedCharts() {
        List<ChartPayload> charts = collectedCharts.get();
        return charts == null ? List.of() : List.copyOf(charts);
    }

    public void endCollecting() {
        collectedCharts.remove();
    }

    @Tool("Search for US-listed stocks by company name or ticker keyword. "
            + "Use this to resolve a company name to its ticker symbol. "
            + "Returns matching symbols with company name and exchange.")
    public List<Map<String, String>> searchStocks(
            @P("search keyword, e.g. a company name or ticker fragment") String query)
            throws Exception {
        return stockService.search(query, 8).stream()
                .map(r -> Map.of(
                        "symbol", r.symbol(),
                        "name", r.name(),
                        "exchange", r.exchange()))
                .toList();
    }

    @Tool("Get the top US stocks ranked by live market capitalization, "
            + "with current price and daily change percent.")
    public Object getTopStocks(
            @P("how many stocks to return, 1-10") int limit) throws Exception {
        return stockService.getTopStocks(Math.max(1, Math.min(limit, 10)));
    }

    @Tool("Get the dashboard's market overview: top stocks per region "
            + "(Americas, EMEA, APAC) ranked by market cap, with live price, "
            + "daily change percent, market cap and currency. Use this for "
            + "market-overview and where-to-invest questions.")
    public Object getRegionalTopStocks(
            @P("how many stocks per region, 1-10") int limit) throws Exception {
        return stockService.getTopStocksByRegion(Math.max(1, Math.min(limit, 10)));
    }

    @Tool("Get daily OHLC stock price history (open, high, low, close, volume) "
            + "for a US ticker between two dates. Dates are YYYY-MM-DD. "
            + "The data is also charted for the user automatically.")
    public String getStockHistory(
            @P("ticker symbol, e.g. AAPL") String symbol,
            @P("start date, YYYY-MM-DD") String from,
            @P("end date, YYYY-MM-DD") String to) throws Exception {
        LocalDate fromDate = LocalDate.parse(from);
        LocalDate toDate = LocalDate.parse(to);
        if (fromDate.isAfter(toDate)) {
            return "Error: start date must be before end date";
        }

        List<Candle> candles = stockService.getHistory(symbol, fromDate, toDate);
        List<ChartPayload> charts = collectedCharts.get();
        if (charts != null) {
            charts.add(new ChartPayload(symbol.toUpperCase(), candles));
        }
        return summarize(symbol.toUpperCase(), candles);
    }

    private String summarize(String symbol, List<Candle> candles) {
        if (candles.isEmpty()) {
            return symbol + ": no trading data found for the requested range";
        }
        Candle first = candles.get(0);
        Candle last = candles.get(candles.size() - 1);
        BigDecimal high = candles.stream().map(Candle::high).max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = candles.stream().map(Candle::low).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal changePct = last.close().subtract(first.close())
                .divide(first.close(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return "%s: %d trading days, %s to %s. First close $%s, last close $%s (%s%%), "
                .formatted(symbol, candles.size(), first.date(), last.date(),
                        first.close(), last.close(), changePct)
                + "period high $%s, period low $%s.".formatted(high, low);
    }
}
