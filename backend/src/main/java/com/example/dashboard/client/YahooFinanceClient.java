package com.example.dashboard.client;

import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import yahoofinance.histquotes.HistoricalQuote;
import yahoofinance.histquotes2.QueryInterval;
import yahoofinance.query2v8.HistQuotesQuery2V8Request;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Component
public class YahooFinanceClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final String CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb";
    private static final String COOKIE_URL = "https://fc.yahoo.com";
    private static final String QUOTE_URL = "https://query1.finance.yahoo.com/v7/finance/quote";
    private static final String SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search";

    private static final long QUOTE_CACHE_TTL_MS = 15_000;

    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private volatile String crumb;

    // Short-TTL quote cache so the dashboard and the agent read the same
    // snapshot when they fetch within a few seconds of each other.
    private final Map<String, QuoteSummary> quoteCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Long> quoteCacheTime = new java.util.concurrent.ConcurrentHashMap<>();

    public List<HistoricalQuote> getHistory(String symbol, Calendar from, Calendar to) throws IOException {
        HistQuotesQuery2V8Request request =
                new HistQuotesQuery2V8Request(symbol.toUpperCase(), from, to, QueryInterval.DAILY);
        return request.getResult();
    }

    public List<QuoteSummary> getQuotes(List<String> symbols) throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        List<QuoteSummary> result = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String s : symbols) {
            Long t = quoteCacheTime.get(s);
            QuoteSummary cached = quoteCache.get(s);
            if (cached != null && t != null && now - t < QUOTE_CACHE_TTL_MS) {
                result.add(cached);
            } else {
                stale.add(s);
            }
        }
        if (!stale.isEmpty()) {
            String body = fetchQuotes(String.join(",", stale));
            for (JsonNode node : mapper.readTree(body).path("quoteResponse").path("result")) {
                QuoteSummary q = new QuoteSummary(
                        node.path("symbol").asText(),
                        node.path("shortName").asText(node.path("longName").asText()),
                        decimal(node, "regularMarketPrice"),
                        decimal(node, "regularMarketChangePercent"),
                        decimal(node, "marketCap"),
                        node.path("currency").asText(null),
                        node.hasNonNull("regularMarketTime") ? node.get("regularMarketTime").asLong() : null,
                        node.path("fullExchangeName").asText(node.path("exchange").asText(null)),
                        node.path("marketState").asText(null));
                quoteCache.put(q.symbol(), q);
                quoteCacheTime.put(q.symbol(), now);
                result.add(q);
            }
        }
        return result;
    }

    public List<SearchResult> search(String query) throws IOException, InterruptedException {
        String url = SEARCH_URL + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&quotesCount=15&newsCount=0&listsCount=0";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Yahoo search request failed with HTTP " + response.statusCode());
        }

        List<SearchResult> results = new ArrayList<>();
        for (JsonNode node : mapper.readTree(response.body()).path("quotes")) {
            results.add(new SearchResult(
                    node.path("symbol").asText(),
                    node.path("shortname").asText(node.path("longname").asText()),
                    node.path("exchange").asText(),
                    node.path("quoteType").asText()));
        }
        return results;
    }

    private java.math.BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.decimalValue() : null;
    }

    private String fetchQuotes(String symbols) throws IOException, InterruptedException {
        String url = QUOTE_URL + "?symbols=" + URLEncoder.encode(symbols, StandardCharsets.UTF_8);
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "&crumb=" + URLEncoder.encode(crumb(), StandardCharsets.UTF_8)))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();
            }
            if (response.statusCode() != 401 && response.statusCode() != 403) {
                throw new IOException("Yahoo quote request failed with HTTP " + response.statusCode());
            }
            resetCrumb();
        }
        throw new IOException("Yahoo quote request unauthorized after crumb refresh");
    }

    private String crumb() throws IOException, InterruptedException {
        if (crumb == null) {
            synchronized (this) {
                if (crumb == null) {
                    // fc.yahoo.com sets the A1 cookie Yahoo requires; the response itself is irrelevant.
                    http.send(HttpRequest.newBuilder().uri(URI.create(COOKIE_URL))
                                    .header("User-Agent", USER_AGENT).GET().build(),
                            HttpResponse.BodyHandlers.discarding());
                    HttpResponse<String> response = http.send(
                            HttpRequest.newBuilder().uri(URI.create(CRUMB_URL))
                                    .header("User-Agent", USER_AGENT).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200 || response.body().isBlank()) {
                        throw new IOException("Failed to obtain Yahoo crumb: HTTP " + response.statusCode());
                    }
                    crumb = response.body().trim();
                }
            }
        }
        return crumb;
    }

    private synchronized void resetCrumb() {
        crumb = null;
    }
}
