# Stocks Explorer — Project Summary

## Purpose

A live global market dashboard rendering the top 20 stocks per region (Americas / APAC / EMEA) and candlestick charts for any ticker, plus a conversational Virtual Agent powered by LangChain4j + OpenAI. Personal/demo-grade tool; deployed on AWS at `https://d34dt5lk8nd7oj.cloudfront.net`.

## Feature list

- Regional watchlists: top 20 per region ranked by USD-normalized market cap, currency chips, market status, last-updated timestamp, STALE markers, per-region manual refresh (disabled while the region's markets are closed)
- Stock details panel: live price/change/status header, `1D 5D 1M 1Y 5Y Max` preset chips, ApexCharts candlestick, two-column stats grid (Open/High/Low, Vol/Avg Vol, Mkt Cap, 52wk High/Low)
- Ticker autocomplete: debounced `/api/stocks/search`, keyboard-navigable ARIA combobox; programmatic selections never open the dropdown
- Virtual Agent: floating chat (full-screen sheet on mobile) with per-session memory, markdown bullets, local-currency prices, inline charts only on request
- Mobile-first responsive: stacked header, full-width controls, scrollable presets, `overflow-x: clip` guard
- Agent tasks API: submit/poll typed background tasks (`/api/agent/tasks`) processed by the in-process agent mesh

## Architecture

React 19 + TS + Vite + Tailwind v4 frontend → Spring Boot 3.5 (Java 17) backend. Layers: controllers (`StockController`, `AgentController`, `AgentTaskController`, `MarketStreamController`, `HealthController`) → services (`StockService`, `MarketAgentService`, `MarketsAgentService`, `AgentOrchestratorService`, `MarketDataCache`) → `YahooFinanceClient` (all Yahoo calls, cookie+crumb, UA header).

**Markets Agent**: per-region `RegionalMarketWorker`s (Americas/EMEA/APAC) refresh candidate pools (~25 each) on an adaptive cadence — 5 min while any covered exchange is open, 30 min closed, bounded retry backoff. `MarketDataCache` diffs each refresh and `MarketStreamController` publishes change-only `market-update` SSE events (+ immediate `snapshot` on connect, 25s heartbeat).

**Agent mesh (Stage 1)**: `GatewayAuthFilter` guards `/api/agent/**` (Bearer/`X-API-Key` when `AGENT_API_KEY` set) → `AgentOrchestratorService` routes `AgentTask`s by type over a `TaskQueue` (in-memory, SQS-shaped) → `DataAgentWorker` (`QUOTE_LOOKUP`, `REGION_SNAPSHOT`, `REGION_REFRESH`) → `DeterministicResultEvaluator` verdicts (one requeue on FAIL) → `AuditStore` (in-memory ring or Redis via `AUDIT_STORE=redis`).

## API surface

| Endpoint | Returns |
|---|---|
| `GET /api/stocks/{symbol}/history?from&to` | `Candle[]` |
| `GET /api/stocks/{symbol}/quote` | `QuoteSummary` |
| `GET /api/stocks/search?q=` | `SearchResult[]` (US equities, ≤8) |
| `GET /api/stocks/top`, `GET /api/stocks/top/regions` | `QuoteSummary[]` / per-region map |
| `GET /api/markets/stream` (SSE) | `snapshot` + `market-update` events |
| `POST /api/markets/{region}/refresh` | triggers off-schedule refresh |
| `POST /api/agent/chat` `{message, sessionId}` | `{message, charts[]}` |
| `POST /api/agent/tasks`, `GET /api/agent/tasks/{id}`, `GET /api/agent/tasks/audit` | task lifecycle + audit |
| `GET /api/health` | `{"status":"ok"}` |

## External integrations

- **Yahoo v8 chart** via `yahoofinance-api` — historical OHLC; needs browser User-Agent (`http.agent`).
- **Yahoo v7 quote** — market cap/price; cookie + crumb obtained lazily, retried on 401/403.
- **Yahoo v1 search** — symbol lookup, no crumb.
- **Yahoo FX** (`{CCY}USD=X`) — normalizes market caps for cross-region ranking.
- **OpenAI** `gpt-4o-mini` via LangChain4j — enabled only with `OPENAI_API_KEY`.

## AI components

`MarketAgentService` builds an `AiServices` agent with `StockTools` (`searchStocks`, `getTopStocks`, `getRegionalTopStocks`, `getStockHistory`). The LLM sees compact summaries; full candle series go through a per-request collector into `charts[]`. No key → 503; rest unaffected. The mesh is separate and deterministic — it needs no LLM.

## Deployment

CloudFront serves the private S3 SPA (OAC) on `/` and proxies `/api/*` — uncached, uncompressed (SSE-safe) — to a Docker container on EC2 (t3.small; SG allows 8080 only from CloudFront's prefix list; `OPENAI_API_KEY` from SSM SecureString via instance role). `deploy/aws-setup.sh` provisions everything idempotently; GitHub Actions OIDC (no stored keys) deploys on push — only `AWS_DEPLOY_ROLE_ARN` secret required.

## Testing strategy

80 backend tests (Mockito units, `@WebMvcTest` contracts, `@SpringBootTest` security, cache diffing, cadence, orchestrator/evaluator/gateway) + JaCoCo. 17 frontend tests (Vitest + Testing Library; SSE snapshot/update/stale, autocomplete, details, race guards).

## Security posture

Env-driven CORS allowlist; server-side param validation; generic 500s; secrets only via env/SSM; `AGENT_API_KEY` gate on all `/api/agent/**`; S3 private behind OAC; SG locked to CloudFront prefix list.

## Known limitations / trade-offs

- Unofficial Yahoo endpoints — fragile, rate-limited; mitigated by backoff + STALE retention.
- Curated candidate pools — not a true index screen.
- No holiday calendars — holidays poll at open cadence.
- In-memory cache/chat/task state — single replica; Redis audit is the first externalization.
- Single EC2 = SPOF; ALB+ASG is the documented next step.
- Chat is non-streaming — long answers can approach edge timeouts.
- ~1.2 MB JS bundle (ApexCharts) — code-splitting is queued.

## Development history

18 iterations: basic candlestick dashboard → tests/hardening → top-5 → autocomplete → LangChain4j agent → UX passes → regional watchlists → Virtual Agent → currency-normalized correctness → top-20 branding → Markets Agent + SSE → details redesign → adaptive cadence → agent mesh → AWS deployment → mobile UI.
