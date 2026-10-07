package com.example.dashboard.agent;

import com.example.dashboard.dto.QuoteSummary;
import com.example.dashboard.dto.RegionSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Rule-based evaluator: sanity-checks worker output per task type. A failed
 * verdict sends the task back through the queue (bounded retries) instead of
 * being served to the caller.
 */
@Component
public class DeterministicResultEvaluator implements ResultEvaluator {

    @Override
    public Verdict evaluate(AgentTask task, Object result) {
        if (result == null) {
            return Verdict.reject("worker returned null");
        }
        return switch (task.type()) {
            case DataAgentWorker.QUOTE_LOOKUP -> evaluateQuote(result);
            case DataAgentWorker.REGION_SNAPSHOT -> evaluateSnapshot(result);
            case DataAgentWorker.REGION_REFRESH -> evaluateRefresh(result);
            default -> Verdict.ok();
        };
    }

    private Verdict evaluateQuote(Object result) {
        if (!(result instanceof QuoteSummary q)) {
            return Verdict.reject("unexpected result type");
        }
        if (q.price() == null || q.price().compareTo(BigDecimal.ZERO) <= 0) {
            return Verdict.reject("missing or non-positive price");
        }
        if (q.currency() == null || q.currency().isBlank()) {
            return Verdict.reject("missing currency");
        }
        return Verdict.ok();
    }

    private Verdict evaluateSnapshot(Object result) {
        if (!(result instanceof RegionSnapshot s)) {
            return Verdict.reject("unexpected result type");
        }
        if (s.quotes() == null || s.quotes().isEmpty()) {
            return Verdict.reject("snapshot has no quotes");
        }
        if (s.stale()) {
            return Verdict.reject("snapshot is stale");
        }
        return Verdict.ok();
    }

    private Verdict evaluateRefresh(Object result) {
        if (!(result instanceof Map<?, ?> m) || !Boolean.TRUE.equals(m.get("accepted"))) {
            return Verdict.reject("refresh not accepted");
        }
        return Verdict.ok();
    }
}
