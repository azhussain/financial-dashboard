package com.example.dashboard.agent;

/**
 * The Evaluator contract: pass/fail judgment on a worker's output before the
 * orchestrator accepts it. Stage 1 implementation is deterministic
 * ({@link DeterministicResultEvaluator}); an LLM-backed evaluator can replace
 * it later without touching the orchestrator.
 */
public interface ResultEvaluator {

    record Verdict(boolean pass, String reason) {
        public static Verdict ok() {
            return new Verdict(true, null);
        }

        public static Verdict reject(String reason) {
            return new Verdict(false, reason);
        }
    }

    Verdict evaluate(AgentTask task, Object result);
}
