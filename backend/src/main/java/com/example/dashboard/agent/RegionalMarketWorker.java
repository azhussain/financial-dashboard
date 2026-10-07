package com.example.dashboard.agent;

import java.util.List;

/**
 * A regional market worker owned by the Markets Agent. Each worker defines the
 * coverage of one region (which exchanges/securities belong to it) and the
 * candidate pool that is refreshed and ranked every cycle.
 *
 * Extend with additional workers (e.g. EmeaMarketWorker, ApacMarketWorker) —
 * the scheduler iterates every registered worker.
 */
public interface RegionalMarketWorker {

    /** Region key, e.g. "AMERICAS". */
    String region();

    /**
     * Candidate symbols covering the region. The full pool is refreshed every
     * cycle — not just the current top 20 — so new entrants can appear.
     */
    List<String> candidates();

    /**
     * Trading sessions of the exchanges covered by this region, used for
     * adaptive cadence: the scheduler refreshes at full rate while any window
     * is open and at a reduced rate when all are closed. An empty list means
     * "always open" (safe default for new workers).
     */
    default List<TradingWindow> tradingWindows() {
        return List.of();
    }
}
