package com.example.dashboard.service;

import com.example.dashboard.client.YahooFinanceClient;
import com.example.dashboard.dto.Candle;
import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import yahoofinance.histquotes.HistoricalQuote;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private YahooFinanceClient yahooFinanceClient;

    @InjectMocks
    private StockService stockService;

    private HistoricalQuote quote(String date, String open, String high, String low, String close) {
        return quote(date, open, high, low, close, close);
    }

    private HistoricalQuote quote(String date, String open, String high,
                                  String low, String close, String adjClose) {
        LocalDate d = LocalDate.parse(date);
        // noon UTC keeps the mapped candle date stable across server timezones
        Calendar cal = GregorianCalendar.from(d.atTime(12, 0).atZone(ZoneOffset.UTC));
        // constructor order: symbol, date, open, low, high, close, adjClose, volume
        return new HistoricalQuote("AAPL", cal,
                new BigDecimal(open), new BigDecimal(low), new BigDecimal(high),
                new BigDecimal(close), new BigDecimal(adjClose), 1000L);
    }

    @Test
    void mapsQuotesToCandles() throws IOException {
        when(yahooFinanceClient.getHistory(eq("aapl"), any(), any()))
                .thenReturn(List.of(
                        quote("2025-09-02", "229.25", "230.85", "226.97", "229.72"),
                        quote("2025-09-03", "237.21", "238.85", "234.36", "238.47")));

        List<Candle> candles = stockService.getHistory("aapl",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30));

        assertThat(candles).hasSize(2);
        Candle first = candles.get(0);
        assertThat(first.date()).isEqualTo(LocalDate.of(2025, 9, 2));
        assertThat(first.open()).isEqualByComparingTo("229.25");
        assertThat(first.high()).isEqualByComparingTo("230.85");
        assertThat(first.low()).isEqualByComparingTo("226.97");
        assertThat(first.close()).isEqualByComparingTo("229.72");
        assertThat(first.volume()).isEqualTo(1000L);
    }

    @Test
    void extendsEndDateByOneDaySoSelectionIsInclusive() throws IOException {
        when(yahooFinanceClient.getHistory(any(), any(), any())).thenReturn(List.of());

        stockService.getHistory("AAPL", LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30));

        ArgumentCaptor<Calendar> fromCap = ArgumentCaptor.forClass(Calendar.class);
        ArgumentCaptor<Calendar> toCap = ArgumentCaptor.forClass(Calendar.class);
        verify(yahooFinanceClient).getHistory(eq("AAPL"), fromCap.capture(), toCap.capture());

        ZoneId zone = ZoneId.systemDefault();
        assertThat(fromCap.getValue().toInstant().atZone(zone).toLocalDate())
                .isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(toCap.getValue().toInstant().atZone(zone).toLocalDate())
                .isEqualTo(LocalDate.of(2025, 10, 1));
    }

    @Test
    void dropsQuotesWithNullClose() throws IOException {
        HistoricalQuote broken = quote("2025-09-03", "1", "1", "1", "1");
        broken.setClose(null);
        when(yahooFinanceClient.getHistory(any(), any(), any()))
                .thenReturn(List.of(quote("2025-09-02", "1", "2", "1", "2"), broken));

        List<Candle> candles = stockService.getHistory("AAPL",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30));

        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).date()).isEqualTo(LocalDate.of(2025, 9, 2));
    }

    @Test
    void returnsEmptyListWhenNoData() throws IOException {
        when(yahooFinanceClient.getHistory(any(), any(), any())).thenReturn(List.of());

        assertThat(stockService.getHistory("FAKE", LocalDate.now(), LocalDate.now())).isEmpty();
    }

    private QuoteSummary quote(String symbol, String name, String marketCap, String currency) {
        return new QuoteSummary(symbol, name, BigDecimal.TEN, BigDecimal.ONE,
                marketCap == null ? null : new BigDecimal(marketCap), currency, 1_700_000_000L);
    }

    private QuoteSummary fx(String symbol, String price) {
        return new QuoteSummary(symbol, symbol, new BigDecimal(price), BigDecimal.ZERO,
                null, "USD", 1_700_000_000L);
    }

    @Test
    void returnsTopStocksSortedByMarketCapLimited() throws Exception {
        when(yahooFinanceClient.getQuotes(any())).thenReturn(List.of(
                quote("AAPL", "Apple", "3000", "USD"),
                quote("MSFT", "Microsoft", "4000", "USD"),
                quote("NOCAP", "NoCap", null, "USD"),
                quote("NVDA", "Nvidia", "5000", "USD"),
                quote("GOOGL", "Alphabet", "2000", "USD")));

        List<QuoteSummary> top = stockService.getTopStocks(2);

        assertThat(top).extracting(QuoteSummary::symbol).containsExactly("NVDA", "MSFT");
    }

    @Test
    void returnsTopStocksGroupedByRegion() throws Exception {
        when(yahooFinanceClient.getQuotes(any())).thenReturn(List.of(
                quote("NVDA", "Nvidia", "5000", "USD"),
                quote("AAPL", "Apple", "3000", "USD"),
                quote("ASML.AS", "ASML", "400", "EUR"),
                quote("MC.PA", "LVMH", "350", "EUR"),
                quote("0700.HK", "Tencent", "600", "HKD"),
                quote("9988.HK", "Alibaba", "200", "HKD")));

        Map<String, List<QuoteSummary>> regions = stockService.getTopStocksByRegion(2);

        assertThat(regions.keySet()).containsExactly("AMERICAS", "EMEA", "APAC");
        assertThat(regions.get("AMERICAS")).extracting(QuoteSummary::symbol)
                .containsExactly("NVDA", "AAPL");
        assertThat(regions.get("EMEA")).extracting(QuoteSummary::symbol)
                .containsExactly("ASML.AS", "MC.PA");
        assertThat(regions.get("APAC")).extracting(QuoteSummary::symbol)
                .containsExactly("0700.HK", "9988.HK");
    }

    @Test
    void ranksRegionalStocksByUsdNormalizedMarketCap() throws Exception {
        // Raw caps: "stockB" 5000 XYZ > "stockA" 900 USD — but XYZUSD is 0.10,
        // so stockA should rank first once normalized.
        when(yahooFinanceClient.getQuotes(any())).thenAnswer(inv -> {
            List<String> symbols = inv.getArgument(0);
            if (symbols.stream().anyMatch(s -> s.endsWith("=X"))) {
                return List.of(fx("HKDUSD=X", "0.13"), fx("EURUSD=X", "1.10"));
            }
            return List.of(
                    quote("AAPL", "Apple", "3000", "USD"),
                    quote("0700.HK", "Tencent", "60000", "HKD"),   // 7800 USD
                    quote("9988.HK", "Alibaba", "20000", "HKD"),   // 2600 USD
                    quote("ASML.AS", "ASML", "400", "EUR"),        // 440 USD
                    quote("MC.PA", "LVMH", "350", "EUR"));         // 385 USD
        });

        Map<String, List<QuoteSummary>> regions = stockService.getTopStocksByRegion(2);

        // 60000 HKD * 0.13 = 7800 > 20000 HKD * 0.13 = 2600
        assertThat(regions.get("APAC")).extracting(QuoteSummary::symbol)
                .containsExactly("0700.HK", "9988.HK");
        assertThat(regions.get("EMEA")).extracting(QuoteSummary::symbol)
                .containsExactly("ASML.AS", "MC.PA");
    }

    @Test
    void dropsDuplicateListingsOfSameCompanyWithinRegion() throws Exception {
        // Yahoo may return the same company under a different candidate symbol;
        // feed two AMERICAS candidates with identical normalized names.
        when(yahooFinanceClient.getQuotes(any())).thenReturn(List.of(
                quote("AAPL", "Apple Inc.", "3000", "USD"),
                quote("MSFT", "Apple Inc.", "2000", "USD"), // duplicate company name
                quote("NVDA", "Nvidia", "5000", "USD")));

        Map<String, List<QuoteSummary>> regions = stockService.getTopStocksByRegion(5);

        assertThat(regions.get("AMERICAS")).extracting(QuoteSummary::symbol)
                .containsExactly("NVDA", "AAPL"); // MSFT deduped away
    }

    @Test
    void adjustsCandlesAcrossSplits() throws Exception {
        // 4:1 split: raw close 400, adjClose 100 → OHLC scaled by 0.25
        HistoricalQuote preSplit = quote("2025-09-02", "400", "410", "380", "400", "100");
        HistoricalQuote postSplit = quote("2025-09-03", "100", "105", "95", "100", "100");
        when(yahooFinanceClient.getHistory(any(), any(), any()))
                .thenReturn(List.of(preSplit, postSplit));

        List<Candle> candles = stockService.getHistory("XYZ",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30));

        assertThat(candles.get(0).close()).isEqualByComparingTo("100");
        assertThat(candles.get(0).open()).isEqualByComparingTo("100");
        assertThat(candles.get(0).high()).isEqualByComparingTo("102.5");
        assertThat(candles.get(0).low()).isEqualByComparingTo("95");
        assertThat(candles.get(1).close()).isEqualByComparingTo("100");
    }

    @Test
    void preservesHolidayGapsInCandleDates() throws Exception {
        // Sep 6-7 2025 is a weekend — candles jump Fri 5th → Mon 8th
        when(yahooFinanceClient.getHistory(any(), any(), any())).thenReturn(List.of(
                quote("2025-09-05", "100", "105", "95", "102"),
                quote("2025-09-08", "103", "108", "101", "107")));

        List<Candle> candles = stockService.getHistory("AAPL",
                LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 30));

        assertThat(candles).extracting(Candle::date)
                .containsExactly(LocalDate.of(2025, 9, 5), LocalDate.of(2025, 9, 8));
    }

    @Test
    void searchKeepsOnlyUsEquitiesAndLimits() throws Exception {
        when(yahooFinanceClient.search("apple")).thenReturn(List.of(
                new SearchResult("AAPL", "Apple Inc.", "NMS", "EQUITY"),
                new SearchResult("SAAPL=F", "Apple Futures", "CME", "FUTURE"),
                new SearchResult("APLE", "Apple Hospitality", "NYQ", "EQUITY"),
                new SearchResult("APPL.NS", "Apple India", "NSI", "EQUITY"),
                new SearchResult("FAKE", "Fake Co", "NMS", "EQUITY")));

        List<SearchResult> results = stockService.search("apple", 2);

        assertThat(results).extracting(SearchResult::symbol).containsExactly("AAPL", "APLE");
    }

    @Test
    void propagatesIoException() throws IOException {
        when(yahooFinanceClient.getHistory(any(), any(), any()))
                .thenThrow(new IOException("upstream failure"));

        assertThatThrownBy(() -> stockService.getHistory("AAPL", LocalDate.now(), LocalDate.now()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("upstream failure");
    }
}
