package com.example.dashboard.service;

import com.example.dashboard.agent.MarketAgent;
import com.example.dashboard.agent.McpToolsService;
import com.example.dashboard.agent.StockTools;
import com.example.dashboard.dto.ChartPayload;
import com.example.dashboard.dto.ChatResponse;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class MarketAgentService {

    private static final String SYSTEM_MESSAGE = """
            You are a financial data assistant embedded in a stock dashboard.
            You remember the conversation — refer back to earlier questions,
            tickers and budgets the user mentioned instead of asking again.
            The dashboard shows regional top stocks (Americas, EMEA, APAC) and
            candlestick charts; your tools read the same live data the user
            sees, so always ground answers in tool data — never quote prices,
            rankings or performance from memory. Resolve company names to
            tickers with the search tool when unsure.

            News: for headline/news questions use the news tool — it returns
            fresh Yahoo Finance headlines; quote title and date, link the
            source, and say when the feed has nothing recent rather than
            guessing.

            Format: answer in short markdown bullet points with **bold**
            tickers/figures, a one-line lead-in, and no long paragraphs.

            Currency: every price/market cap from the tools carries a currency
            code — always show the listing's local currency (e.g. "SAR 25.80",
            "€1,636", "HK$610", "₩272,000") and never use $ unless the currency
            is USD. When the user's budget is in a different currency than the
            listing, say so and convert only approximately, noting it's an
            estimate.

            For investment questions like "I have $X, where should I invest":
            - call the regional/top-stock tools for current prices first
            - compare share prices against the user's budget (respecting each
              listing's currency); say which shares are or aren't affordable as
              whole shares, and mention fractional shares or cheaper large caps
              when relevant
            - prefer suggesting a small diversified set over one name
            - end with a brief "not financial advice" disclaimer.

            Charts: call the price-history tool only when the user asks for a
            chart/graph/plot, or when you genuinely need the trend to answer
            (e.g. performance questions). A chart is rendered for the user only
            when they asked for one — do not promise a chart otherwise.
            """;

    // Charts are attached to the reply only when the user asks for one.
    private static final Pattern CHART_INTENT = Pattern.compile(
            "\\b(charts?|graphs?|plots?|candlesticks?|visuali[sz]e)\\b",
            Pattern.CASE_INSENSITIVE);

    static boolean wantsCharts(String message) {
        return CHART_INTENT.matcher(message).find();
    }

    // Appended to the system message only when MCP servers are configured —
    // their tools (news, filings, fetch, …) appear alongside the stock tools.
    private static final String MCP_HINT = """

            You may also have external MCP tools (news, SEC filings, web fetch);
            prefer them for anything the stock tools don't cover, and still
            ground every claim in a tool result — never memory.
            """;

    private final StockTools stockTools;
    private final McpToolsService mcpTools;
    private final String apiKey;
    private final String modelName;
    private volatile MarketAgent agent;

    public MarketAgentService(StockTools stockTools,
                              McpToolsService mcpTools,
                              @Value("${agent.openai.api-key:}") String apiKey,
                              @Value("${agent.openai.model:gpt-4o-mini}") String modelName) {
        this.stockTools = stockTools;
        this.mcpTools = mcpTools;
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(apiKey);
    }

    public ChatResponse chat(String message, String sessionId) {
        if (!isEnabled()) {
            throw new IllegalStateException("Agent is not configured");
        }
        stockTools.beginCollecting();
        try {
            String reply = agent().chat(sessionId, message);
            List<ChartPayload> charts = wantsCharts(message)
                    ? stockTools.collectedCharts()
                    : List.of();
            return new ChatResponse(reply, charts);
        } finally {
            stockTools.endCollecting();
        }
    }

    private MarketAgent agent() {
        if (agent == null) {
            synchronized (this) {
                if (agent == null) {
                    var builder = AiServices.builder(MarketAgent.class)
                            .chatModel(OpenAiChatModel.builder()
                                    .apiKey(apiKey)
                                    .modelName(modelName)
                                    .temperature(0.2)
                                    .build())
                            .tools(stockTools)
                            // Per-session memory so concurrent users/tabs get
                            // isolated conversations.
                            .chatMemoryProvider(id ->
                                    MessageWindowChatMemory.withMaxMessages(20));
                    var provider = mcpTools.toolProvider();
                    if (provider != null) {
                        builder.toolProvider(provider);
                    }
                    agent = builder
                            .systemMessageProvider(id -> SYSTEM_MESSAGE
                                    + (provider != null ? MCP_HINT : "")
                                    + " Today's date is " + LocalDate.now() + ".")
                            .build();
                }
            }
        }
        return agent;
    }
}
