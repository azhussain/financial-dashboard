package com.example.dashboard.controller;

import com.example.dashboard.dto.Candle;
import com.example.dashboard.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockService stockService;

    private Candle sampleCandle() {
        return new Candle(LocalDate.of(2025, 9, 2),
                new BigDecimal("229.25"), new BigDecimal("230.85"),
                new BigDecimal("226.97"), new BigDecimal("229.72"), 44075600L);
    }

    @Test
    void returnsCandlesForValidRequest() throws Exception {
        when(stockService.getHistory(eq("AAPL"), eq(LocalDate.of(2025, 9, 1)), eq(LocalDate.of(2025, 9, 30))))
                .thenReturn(List.of(sampleCandle()));

        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2025-09-02"))
                .andExpect(jsonPath("$[0].open").value(229.25))
                .andExpect(jsonPath("$[0].high").value(230.85))
                .andExpect(jsonPath("$[0].low").value(226.97))
                .andExpect(jsonPath("$[0].close").value(229.72))
                .andExpect(jsonPath("$[0].volume").value(44075600));
    }

    @Test
    void rejectsWhenStartDateIsAfterEndDate() throws Exception {
        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "2025-09-30")
                        .param("to", "2025-09-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Start date must be before end date"));

        verifyNoInteractions(stockService);
    }

    @Test
    void rejectsMissingDateParameters() throws Exception {
        mockMvc.perform(get("/api/stocks/AAPL/history"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stocks/AAPL/history").param("from", "2025-09-01"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }

    @Test
    void rejectsMalformedDates() throws Exception {
        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "not-a-date")
                        .param("to", "2025-09-30"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "09/01/2025")
                        .param("to", "2025-09-30"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }

    @Test
    void mapsUnknownTickerToBadRequest() throws Exception {
        when(stockService.getHistory(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unknown ticker symbol: FAKE"));

        mockMvc.perform(get("/api/stocks/FAKE/history")
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unknown ticker symbol: FAKE"));
    }

    @Test
    void returnsTopStocks() throws Exception {
        when(stockService.getTopStocks(5)).thenReturn(List.of(
                new com.example.dashboard.dto.QuoteSummary("NVDA", "NVIDIA Corporation",
                        new BigDecimal("238.90"), new BigDecimal("2.12"), new BigDecimal("5770000000000"))));

        mockMvc.perform(get("/api/stocks/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("NVDA"))
                .andExpect(jsonPath("$[0].marketCap").value(5770000000000L));
    }

    @Test
    void rejectsInvalidTopLimit() throws Exception {
        mockMvc.perform(get("/api/stocks/top").param("limit", "0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/stocks/top").param("limit", "99"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }

    @Test
    void returnsSearchResults() throws Exception {
        when(stockService.search("app", 8)).thenReturn(List.of(
                new com.example.dashboard.dto.SearchResult("AAPL", "Apple Inc.", "NMS", "EQUITY")));

        mockMvc.perform(get("/api/stocks/search").param("q", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."));
    }

    @Test
    void rejectsBlankSearchQuery() throws Exception {
        mockMvc.perform(get("/api/stocks/search").param("q", "   "))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(stockService);
    }

    @Test
    void mapsUpstreamFailureToGeneric500() throws Exception {
        when(stockService.getHistory(any(), any(), any()))
                .thenThrow(new IOException("upstream exploded"));

        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to fetch stock data"));
    }
}
