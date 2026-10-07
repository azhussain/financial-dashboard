package com.example.dashboard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Agent gateway: auth/policy boundary for everything under {@code /api/agent/**}.
 * When {@code agent.gateway.api-key} is set, callers must present it as a Bearer
 * token or {@code X-API-Key} header; when unset (dev default) the filter is a
 * pass-through so local development is unchanged.
 */
@Component
public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final String PREFIX = "/api/agent/";

    private final String apiKey;

    public GatewayAuthFilter(@Value("${agent.gateway.api-key:}") String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return apiKey.isBlank() || !request.getRequestURI().startsWith(PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String auth = request.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ")
                ? auth.substring(7) : request.getHeader("X-API-Key");
        if (!apiKey.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
