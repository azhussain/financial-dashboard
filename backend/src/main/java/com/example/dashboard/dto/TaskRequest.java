package com.example.dashboard.dto;

import java.util.Map;

public record TaskRequest(String type, Map<String, Object> payload) {
}
