package com.example.dashboard.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAuthFilterTest {

    private static final String PATH = "/api/agent/tasks";

    private record Outcome(int status, boolean reachedController) {
    }

    private Outcome run(GatewayAuthFilter filter,
                        MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Outcome(response.getStatus(), chain.getRequest() != null);
    }

    private static MockHttpServletRequest req(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    @Test
    void passesThroughWhenNoKeyConfigured() throws Exception {
        GatewayAuthFilter filter = new GatewayAuthFilter("");
        assertTrue(run(filter, req("POST", PATH)).reachedController());
    }

    @Test
    void rejectsAgentRequestWithoutKey() throws Exception {
        GatewayAuthFilter filter = new GatewayAuthFilter("secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(req("POST", PATH), response, new MockFilterChain());
        assertEquals(401, response.getStatus());
        assertEquals("application/json", response.getContentType());
    }

    @Test
    void acceptsBearerTokenAndApiKeyHeader() throws Exception {
        GatewayAuthFilter filter = new GatewayAuthFilter("secret");

        MockHttpServletRequest bearer = req("POST", PATH);
        bearer.addHeader("Authorization", "Bearer secret");
        assertTrue(run(filter, bearer).reachedController());

        MockHttpServletRequest apiKey = req("POST", PATH);
        apiKey.addHeader("X-API-Key", "secret");
        assertTrue(run(filter, apiKey).reachedController());
    }

    @Test
    void rejectsWrongKey() throws Exception {
        GatewayAuthFilter filter = new GatewayAuthFilter("secret");
        MockHttpServletRequest request = req("POST", PATH);
        request.addHeader("Authorization", "Bearer wrong");
        Outcome outcome = run(filter, request);
        assertFalse(outcome.reachedController());
        assertEquals(401, outcome.status());
    }

    @Test
    void doesNotGuardNonAgentPaths() throws Exception {
        GatewayAuthFilter filter = new GatewayAuthFilter("secret");
        assertTrue(run(filter, req("GET", "/api/stocks/markets")).reachedController());
    }
}
