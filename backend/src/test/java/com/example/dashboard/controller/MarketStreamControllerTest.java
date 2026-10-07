package com.example.dashboard.controller;

import com.example.dashboard.service.MarketsAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MarketStreamController.class)
class MarketStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MarketsAgentService marketsAgentService;

    @Test
    void acceptsManualRefreshForKnownRegion() throws Exception {
        when(marketsAgentService.refreshAsync("AMERICAS")).thenReturn(true);

        mockMvc.perform(post("/api/markets/AMERICAS/refresh"))
                .andExpect(status().isAccepted());

        verify(marketsAgentService).refreshAsync("AMERICAS");
    }

    @Test
    void rejectsManualRefreshForUnknownRegion() throws Exception {
        when(marketsAgentService.refreshAsync("MARS")).thenReturn(false);

        mockMvc.perform(post("/api/markets/MARS/refresh"))
                .andExpect(status().isNotFound());
    }
}
