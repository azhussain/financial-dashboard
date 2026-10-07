package com.example.dashboard.service;

import com.example.dashboard.client.YahooFinanceClient;
import com.example.dashboard.dto.Candle;
import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.SearchResult;
import org.springframework.stereotype.Service;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StockService {

    // Large-cap candidates per region; the top N by market cap are returned.
    // Ranking compares raw Yahoo marketCap values, which are denominated in
    // each listing's own currency — an approximation for display purposes.
    private static final Map<String, List<String>> REGION_CANDIDATES;
    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("AMERICAS", List.of(
                "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META",
                "AVGO", "TSLA", "BRK-B", "LLY", "JPM", "V", "WMT", "XOM",
                "MA", "COST", "HD", "NFLX", "PG", "CRM", "ORCL", "AMD",
                "RY.TO", "TD.TO", "PBR", "ITUB", "BSBR"));
        m.put("EMEA", List.of(
                "2222.SR", "MC.PA", "ASML.AS", "NESN.SW", "NOVO-B.CO", "ROG.SW",
                "SAP.DE", "OR.PA", "SIE.DE", "AIR.PA", "SAN.PA", "SU.PA",
                "TTE.PA", "ALV.DE", "DTE.DE", "PRX.AS", "UBSG.SW", "IFX.DE",
                "BAS.DE", "AI.PA", "EL.PA", "CS.PA"));
        m.put("APAC", List.of(
                "0700.HK", "9988.HK", "3690.HK", "9618.HK", "1299.HK", "1398.HK",
                "2318.HK", "0941.HK", "3988.HK", "0939.HK",
                "2330.TW", "005930.KS", "000660.KS",
                "7203.T", "6758.T", "8306.T", "6861.T", "9984.T", "7974.T", "6501.T",
                "BHP.AX", "CBA.AX", "CSL.AX"));
        REGION_CANDIDATES = Collections.unmodifiableMap(m);
    }

    private static final List<String> TOP_US_CANDIDATES = REGION_CANDIDATES.get("AMERICAS");

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
                .map(q -> {
                    // Scale OHLC by adjClose/close so charts stay continuous
                    // across splits and dividends instead of showing fake gaps.
                    BigDecimal close = q.getClose();
                    BigDecimal ratio = BigDecimal.ONE;
                    if (q.getAdjClose() != null && close.signum() != 0) {
                        ratio = q.getAdjClose().divide(close, 10, RoundingMode.HALF_UP);
                    }
                    return new Candle(
                            // Daily bars are dated in UTC so candle dates do not
                            // shift with the server's local timezone.
                            q.getDate().toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                            scale(q.getOpen(), ratio),
                            scale(q.getHigh(), ratio),
                            scale(q.getLow(), ratio),
                            scale(close, ratio),
                            q.getVolume()
                    );
                })
                .toList();
    }

    private static BigDecimal scale(BigDecimal v, BigDecimal ratio) {
        return v == null ? null : v.multiply(ratio).stripTrailingZeros();
    }

    public List<QuoteSummary> getTopStocks(int limit) throws IOException, InterruptedException {
        return yahooFinanceClient.getQuotes(TOP_US_CANDIDATES).stream()
                .filter(q -> q.marketCap() != null)
                .sorted(Comparator.comparing(QuoteSummary::marketCap).reversed())
                .limit(limit)
                .toList();
    }

    public QuoteSummary getQuote(String symbol) throws IOException, InterruptedException {
        String sym = symbol.trim().toUpperCase();
        return yahooFinanceClient.getQuotes(List.of(sym)).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ticker symbol: " + sym));
    }

    public List<String> regionCandidates(String region) {
        return REGION_CANDIDATES.getOrDefault(region, List.of());
    }

    /** Fetch and rank an arbitrary candidate pool by USD-normalized market cap. */
    public List<QuoteSummary> rankCandidates(List<String> symbols, int limit)
            throws IOException, InterruptedException {
        List<QuoteSummary> quotes = yahooFinanceClient.getQuotes(symbols);
        Map<String, BigDecimal> usdRates = fetchUsdRates(currenciesOf(quotes));
        return rankAndLimit(quotes.stream(), usdRates, limit);
    }

    public Map<String, List<QuoteSummary>> getTopStocksByRegion(int limit)
            throws IOException, InterruptedException {
        List<String> all = REGION_CANDIDATES.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        Map<String, QuoteSummary> bySymbol = yahooFinanceClient.getQuotes(all).stream()
                .collect(Collectors.toMap(QuoteSummary::symbol, Function.identity(), (a, b) -> a));

        // Yahoo reports market cap in each listing's local currency; normalize
        // to USD so rankings compare like-for-like across currencies.
        Map<String, BigDecimal> usdRates = fetchUsdRates(currenciesOf(bySymbol.values()));

        Map<String, List<QuoteSummary>> result = new LinkedHashMap<>();
        REGION_CANDIDATES.forEach((region, symbols) ->
                result.put(region, rankAndLimit(
                        symbols.stream().map(bySymbol::get).filter(Objects::nonNull),
                        usdRates, limit)));
        return result;
    }

    private List<QuoteSummary> rankAndLimit(
            java.util.stream.Stream<QuoteSummary> quotes,
            Map<String, BigDecimal> usdRates, int limit) {
        Set<String> seenCompanies = new LinkedHashSet<>();
        return quotes
                .filter(q -> q.marketCap() != null)
                // drop duplicate listings of the same company within a region
                .filter(q -> seenCompanies.add(normalizeName(q.name())))
                .sorted(Comparator.comparing(q -> marketCapUsd(q, usdRates),
                        Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    private static Set<String> currenciesOf(java.util.Collection<QuoteSummary> quotes) {
        return quotes.stream()
                .map(QuoteSummary::currency)
                .filter(c -> c != null && !"USD".equals(c))
                .collect(Collectors.toSet());
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static BigDecimal marketCapUsd(QuoteSummary q, Map<String, BigDecimal> usdRates) {
        BigDecimal rate = usdRates.get(q.currency());
        return rate == null ? q.marketCap() : q.marketCap().multiply(rate);
    }

    // USD per 1 unit of each currency; missing rates leave values unconverted.
    private Map<String, BigDecimal> fetchUsdRates(Set<String> currencies) {
        if (currencies.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> fxSymbols = currencies.stream()
                    .map(c -> c + "USD=X")
                    .toList();
            Map<String, BigDecimal> rates = new LinkedHashMap<>();
            List<QuoteSummary> direct = yahooFinanceClient.getQuotes(fxSymbols);
            Set<String> found = new LinkedHashSet<>();
            for (QuoteSummary fx : direct) {
                if (fx.symbol().endsWith("USD=X") && fx.price() != null) {
                    String ccy = fx.symbol().substring(0, fx.symbol().length() - 5);
                    rates.put(ccy, fx.price());
                    found.add(ccy);
                }
            }
            // Some pairs only exist as USD{CCY}=X — invert those.
            Set<String> missing = currencies.stream()
                    .filter(c -> !found.contains(c))
                    .collect(Collectors.toSet());
            if (!missing.isEmpty()) {
                for (QuoteSummary fx : yahooFinanceClient.getQuotes(
                        missing.stream().map(c -> "USD" + c + "=X").toList())) {
                    if (fx.symbol().startsWith("USD") && fx.symbol().endsWith("=X")
                            && fx.price() != null && fx.price().signum() != 0) {
                        rates.put(fx.symbol().substring(3, fx.symbol().length() - 2),
                                BigDecimal.ONE.divide(fx.price(), 10, RoundingMode.HALF_UP));
                    }
                }
            }
            return rates;
        } catch (Exception e) {
            return Map.of(); // keep un-normalized ranking on FX failure
        }
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
