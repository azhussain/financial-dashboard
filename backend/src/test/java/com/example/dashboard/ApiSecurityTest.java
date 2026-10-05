package com.example.dashboard;

import com.example.dashboard.service.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StockService stockService;

    // ---------- HTTP method enforcement ----------

    @Test
    void onlyGetIsAllowed() throws Exception {
        String url = "/api/stocks/AAPL/history?from=2025-09-01&to=2025-09-30";

        mockMvc.perform(post(url)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(url)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(url)).andExpect(status().isMethodNotAllowed());
    }

    // ---------- CORS policy ----------

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/stocks/AAPL/history")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsUnknownOrigins() throws Exception {
        mockMvc.perform(options("/api/stocks/AAPL/history")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    // ---------- Injection / malicious input ----------

    @Test
    void scriptPayloadInTickerDoesNotBreakApi() throws Exception {
        when(stockService.getHistory(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/stocks/{symbol}/history", "<script>alert(1)")
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string("[]"));
    }

    @Test
    void sqlInjectionPayloadInTickerDoesNotBreakApi() throws Exception {
        when(stockService.getHistory(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/stocks/{symbol}/history", "'; DROP TABLE stocks; --")
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
    }

    @Test
    void injectionInDateParamsIsRejected() throws Exception {
        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "2025-09-01' OR '1'='1")
                        .param("to", "2025-09-30"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void excessivelyLongTickerIsHandled() throws Exception {
        when(stockService.getHistory(any(), any(), any())).thenReturn(List.of());
        String longTicker = "A".repeat(5000);

        mockMvc.perform(get("/api/stocks/{symbol}/history", longTicker)
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isOk());
    }

    // ---------- Error response hardening ----------

    @Test
    void internalErrorsDoNotLeakImplementationDetails() throws Exception {
        when(stockService.getHistory(any(), any(), any()))
                .thenThrow(new RuntimeException("yahoofinance.query2v8 internal detail"));

        mockMvc.perform(get("/api/stocks/AAPL/history")
                        .param("from", "2025-09-01")
                        .param("to", "2025-09-30"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to fetch stock data"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("yahoofinance"))));
    }

    @Test
    void unknownEndpointsReturn404NotStackTrace() throws Exception {
        mockMvc.perform(get("/api/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }
}
