package com.example.dashboard.controller;

import com.example.dashboard.agent.TaskResult;
import com.example.dashboard.service.AgentOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentTaskController.class)
class AgentTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentOrchestratorService orchestrator;

    @Test
    void submitReturns202WithTaskId() throws Exception {
        when(orchestrator.submit(eq("QUOTE_LOOKUP"), any()))
                .thenReturn("task-1");

        mockMvc.perform(post("/api/agent/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"quote_lookup\",\"payload\":{\"symbol\":\"NVDA\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.taskId").value("task-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(orchestrator).submit(eq("QUOTE_LOOKUP"), any());
    }

    @Test
    void submitRejectsMissingType() throws Exception {
        mockMvc.perform(post("/api/agent/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusReturnsResultOr404() throws Exception {
        when(orchestrator.status("known"))
                .thenReturn(new TaskResult("known", "PASS", Map.of("price", 1), null, 5L));

        mockMvc.perform(get("/api/agent/tasks/known"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASS"))
                .andExpect(jsonPath("$.data.price").value(1));

        mockMvc.perform(get("/api/agent/tasks/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditReturnsEntries() throws Exception {
        mockMvc.perform(get("/api/agent/tasks/audit").param("n", "5"))
                .andExpect(status().isOk());
        verify(orchestrator).audit(5);
    }
}
