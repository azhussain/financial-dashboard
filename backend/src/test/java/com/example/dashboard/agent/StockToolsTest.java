package com.example.dashboard.agent;

import com.example.dashboard.dto.Candle;
import com.example.dashboard.service.StockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockToolsTest {

    @Mock
    private StockService stockService;

    @InjectMocks
    private StockTools stockTools;

    @AfterEach
    void cleanup() {
        stockTools.endCollecting();
    }

    @Test
    void historyToolSummarizesAndCollectsChart() throws Exception {
        List<Candle> candles = List.of(
                new Candle(LocalDate.of(2025, 9, 1), new BigDecimal("100"),
                        new BigDecimal("110"), new BigDecimal("95"), new BigDecimal("100"), 1L),
                new Candle(LocalDate.of(2025, 9, 2), new BigDecimal("105"),
                        new BigDecimal("120"), new BigDecimal("90"), new BigDecimal("110"), 1L));
        when(stockService.getHistory(eq("nvda"), any(), any())).thenReturn(candles);

        stockTools.beginCollecting();
        String summary = stockTools.getStockHistory("nvda", "2025-09-01", "2025-09-02");

        assertThat(summary).contains("NVDA", "2 trading days", "10.00%", "$120", "$90");
        assertThat(stockTools.collectedCharts()).hasSize(1);
        assertThat(stockTools.collectedCharts().get(0).symbol()).isEqualTo("NVDA");
        assertThat(stockTools.collectedCharts().get(0).candles()).hasSize(2);
    }

    @Test
    void historyToolRejectsInvertedRange() throws Exception {
        String result = stockTools.getStockHistory("AAPL", "2025-10-01", "2025-09-01");

        assertThat(result).contains("start date must be before end date");
    }

    @Test
    void regionalToolDelegatesToService() throws Exception {
        when(stockService.getTopStocksByRegion(3)).thenReturn(
                java.util.Map.of("AMERICAS", List.of(), "EMEA", List.of(), "APAC", List.of()));

        Object result = stockTools.getRegionalTopStocks(3);

        assertThat(result).isInstanceOf(java.util.Map.class);
        org.mockito.Mockito.verify(stockService).getTopStocksByRegion(3);
    }

    @Test
    void chartsAreEmptyWithoutCollectionScope() throws Exception {
        assertThat(stockTools.collectedCharts()).isEmpty();
    }
}
