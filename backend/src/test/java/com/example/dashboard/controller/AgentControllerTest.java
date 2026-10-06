package com.example.dashboard.controller;

import com.example.dashboard.dto.ChatResponse;
import com.example.dashboard.dto.ChartPayload;
import com.example.dashboard.service.MarketAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketAgentService agentService;

    @Test
    void returns503WhenAgentNotConfigured() throws Exception {
        when(agentService.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void rejectsBlankMessage() throws Exception {
        when(agentService.isEnabled()).thenReturn(true);

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsReplyWithCharts() throws Exception {
        when(agentService.isEnabled()).thenReturn(true);
        var candle = new com.example.dashboard.dto.Candle(LocalDate.of(2025, 9, 2),
                new BigDecimal("229.25"), new BigDecimal("230.85"),
                new BigDecimal("226.97"), new BigDecimal("229.72"), 1L);
        when(agentService.chat("Show NVDA", "default"))
                .thenReturn(new ChatResponse("Here is NVDA.",
                        List.of(new ChartPayload("NVDA", List.of(candle)))));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"Show NVDA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Here is NVDA."))
                .andExpect(jsonPath("$.charts[0].symbol").value("NVDA"))
                .andExpect(jsonPath("$.charts[0].candles[0].close").value(229.72));
    }

    @Test
    void forwardsSessionIdToAgentService() throws Exception {
        when(agentService.isEnabled()).thenReturn(true);
        when(agentService.chat("hi", "tab-42"))
                .thenReturn(new ChatResponse("hello", List.of()));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\", \"sessionId\": \"tab-42\"}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(agentService).chat("hi", "tab-42");
    }

    @Test
    void mapsAgentFailureToGeneric500() throws Exception {
        when(agentService.isEnabled()).thenReturn(true);
        when(agentService.chat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("provider exploded"));

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"hi\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to process the request"));
    }
}
