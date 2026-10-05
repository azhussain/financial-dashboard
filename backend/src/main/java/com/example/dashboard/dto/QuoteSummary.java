package com.example.dashboard.dto;

import java.math.BigDecimal;

public record QuoteSummary(
        String symbol,
        String name,
        BigDecimal price,
        BigDecimal changePercent,
        BigDecimal marketCap
) {
}
