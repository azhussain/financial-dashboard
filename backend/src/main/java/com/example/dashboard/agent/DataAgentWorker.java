package com.example.dashboard.agent;

import com.example.dashboard.dto.RegionSnapshot;
import com.example.dashboard.service.MarketDataCache;
import com.example.dashboard.service.MarketsAgentService;
import com.example.dashboard.service.StockService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * The Data Agent: executes market-data tasks by delegating to the same
 * services the REST layer uses. Task types:
 * <ul>
 *   <li>{@code QUOTE_LOOKUP} — payload {@code {symbol}} → live QuoteSummary</li>
 *   <li>{@code REGION_SNAPSHOT} — payload {@code {region}} → cached RegionSnapshot</li>
 *   <li>{@code REGION_REFRESH} — payload {@code {region}} → triggers the shared
 *       refresh, returns {@code {accepted: bool}}</li>
 * </ul>
 */
@Component
public class DataAgentWorker implements AgentWorker {

    public static final String QUOTE_LOOKUP = "QUOTE_LOOKUP";
    public static final String REGION_SNAPSHOT = "REGION_SNAPSHOT";
    public static final String REGION_REFRESH = "REGION_REFRESH";

    private final StockService stockService;
    private final MarketDataCache cache;
    private final MarketsAgentService marketsAgent;

    public DataAgentWorker(StockService stockService, MarketDataCache cache,
                           MarketsAgentService marketsAgent) {
        this.stockService = stockService;
        this.cache = cache;
        this.marketsAgent = marketsAgent;
    }

    @Override
    public Set<String> taskTypes() {
        return Set.of(QUOTE_LOOKUP, REGION_SNAPSHOT, REGION_REFRESH);
    }

    @Override
    public Object execute(AgentTask task) throws Exception {
        return switch (task.type()) {
            case QUOTE_LOOKUP -> stockService.getQuote(required(task, "symbol"));
            case REGION_SNAPSHOT -> {
                RegionSnapshot snapshot = cache.snapshot(required(task, "region").toUpperCase());
                if (snapshot == null) {
                    throw new IllegalArgumentException("no snapshot for region");
                }
                yield snapshot;
            }
            case REGION_REFRESH -> Map.of("accepted",
                    marketsAgent.refreshAsync(required(task, "region").toUpperCase()));
            default -> throw new IllegalArgumentException("unsupported task type: " + task.type());
        };
    }

    private static String required(AgentTask task, String key) {
        Object value = task.payload().get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("missing payload field: " + key);
        }
        return value.toString();
    }
}
