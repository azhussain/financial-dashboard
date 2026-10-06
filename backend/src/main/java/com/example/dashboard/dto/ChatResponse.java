package com.example.dashboard.dto;

import java.util.List;

public record ChatResponse(
        String message,
        List<ChartPayload> charts
) {
}
