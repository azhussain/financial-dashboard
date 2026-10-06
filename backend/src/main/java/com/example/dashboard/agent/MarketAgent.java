package com.example.dashboard.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

public interface MarketAgent {

    String chat(@MemoryId String sessionId, @UserMessage String message);
}
