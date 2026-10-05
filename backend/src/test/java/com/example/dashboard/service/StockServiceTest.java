package com.example.dashboard.service;

import com.example.dashboard.client.YahooFinanceClient;
import com.example.dashboard.dto.Candle;
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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

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
        LocalDate d = LocalDate.parse(date);
        Calendar cal = GregorianCalendar.from(d.atStartOfDay(ZoneId.systemDefault()));
        // constructor order: symbol, date, open, low, high, close, adjClose, volume
        return new HistoricalQuote("AAPL", cal,
                new BigDecimal(open), new BigDecimal(low), new BigDecimal(high),
                new BigDecimal(close), new BigDecimal(close), 1000L);
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

    @Test
    void propagatesIoException() throws IOException {
        when(yahooFinanceClient.getHistory(any(), any(), any()))
                .thenThrow(new IOException("upstream failure"));

        assertThatThrownBy(() -> stockService.getHistory("AAPL", LocalDate.now(), LocalDate.now()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("upstream failure");
    }
}
