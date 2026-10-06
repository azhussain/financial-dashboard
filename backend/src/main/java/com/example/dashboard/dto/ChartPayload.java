package com.example.dashboard.dto;

import java.util.List;

public record ChartPayload(
        String symbol,
        List<Candle> candles
) {
}
