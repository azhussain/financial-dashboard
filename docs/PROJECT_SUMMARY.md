# Financial Dashboard — Project Summary

## Purpose

A single-page dashboard for viewing historical US stock prices as candlestick charts, with a live top-5-by-market-cap panel, ticker autocomplete, and a LangChain4j-powered AI assistant that can answer questions and render charts from natural language. Personal/demo-grade tool, single user, no persistence.

## Feature list

- Candlestick chart (ApexCharts) for any US ticker over a date range
- Ticker autocomplete with company name + exchange suggestions, keyboard navigable
- Top 5 US stocks ranked by live market cap; clicking a card loads its chart
- AI chat assistant: text answers, plus inline candlestick charts when the agent fetches history
- Split-screen layout: assistant docked left, stocks workspace right (stacks on mobile)

## Architecture

React 19 + TypeScript + Vite + Tailwind v4 frontend on :5173, proxying `/api/*` to a Spring Boot 3.5 (Java 17) backend on :8080. Backend layers: `StockController`/`AgentController` (REST + validation) → `StockService`/`MarketAgentService` (business logic) → `YahooFinanceClient` (all Yahoo calls). DTOs: `Candle`, `QuoteSummary`, `SearchResult`, `ChartPayload`, `ChatRequest`/`ChatResponse`.

Flows: chart load = `GET /{ticker}/history` → `HistQuotesQuery2V8Request` (yahoofinance-api) → Yahoo v8 chart → mapped `Candle[]`. Autocomplete = `GET /search` → Yahoo v1 search → filtered to US equities. Top-5 = `GET /top` → crumb-authenticated v7 quotes → sorted by `marketCap`. Chat = `POST /api/agent/chat` → LLM tool-calling loop → text + chart payloads.

## API surface

| Endpoint | Returns |
|---|---|
| `GET /api/stocks/{ticker}/history?from&to` (ISO dates) | `Candle[]` — date, open, high, low, close, volume |
| `GET /api/stocks/top?limit=5` | `QuoteSummary[]` — symbol, name, price, changePercent, marketCap |
| `GET /api/stocks/search?q=` | `SearchResult[]` — symbol, name, exchange, quoteType (max 8, US equities only) |
| `POST /api/agent/chat` `{message}` | `{message, charts[]}` — charts are `ChartPayload` (symbol + `Candle[]`) |

## External integrations

- **Yahoo v8 chart** (`query2.finance.yahoo.com/v8/finance/chart`) via the `yahoofinance-api` package — historical OHLC; works without crumb but requires a browser User-Agent (429 otherwise; set via `http.agent` in `DashboardApplication`).
- **Yahoo v7 quote** — market cap/price; requires cookie + crumb, obtained lazily in `YahooFinanceClient` (`fc.yahoo.com` → `/v1/test/getcrumb`), retried once on 401/403.
- **Yahoo v1 search** — symbol lookup, no crumb needed.
- **OpenAI** — `gpt-4o-mini` via LangChain4j `OpenAiChatModel`; enabled only when `OPENAI_API_KEY` is set.

## AI agent

`MarketAgentService` lazily builds an `AiServices` agent (`MarketAgent` interface) with `StockTools` — three `@Tool` methods (`searchStocks`, `getTopStocks`, `getStockHistory`) delegating to `StockService`. The system prompt injects today's date. The LLM sees compact summaries (e.g. "63 trading days, first close $X, last close $Y (-3.2%)"), while full candle series are captured in a per-request `ThreadLocal` collector and returned to the UI as `charts[]` — so the model never has to serialize raw rows and the frontend renders real charts. No key → `isEnabled()` false → HTTP 503; rest of app unaffected.

## Testing strategy

Backend: `StockServiceTest`/`MarketAgentServiceTest`/`StockToolsTest` (Mockito units), `StockControllerTest`/`AgentControllerTest` (`@WebMvcTest` contract + validation), `ApiSecurityTest` (`@SpringBootTest` — CORS, HTTP methods, injection payloads, error leakage). JaCoCo report in `target/site/jacoco/`. Frontend: Vitest + Testing Library (`App.test.tsx`), ApexCharts stubbed for jsdom.

## Security posture

CORS restricted to `http://localhost:5173` (GET/POST). Server-side validation on all params (date order/format, query length 1–50, chat 1–1000, limit 1–20). 500s return generic messages; internals logged only. API key read from env var, never stored in repo.

## Known limitations / trade-offs

- All Yahoo endpoints are unofficial and fragile — they can break or rate-limit without notice; the v7 crumb flow may need rework if Yahoo rotates requirements.
- Top-5 ranks a hardcoded ~12-ticker candidate list, not a true S&P 500 screen.
- `ThreadLocal` chart collector assumes sequential tool calls per request thread (true for `AiServices` sync path, but fragile if execution ever parallelizes).
- No persistence, auth, or rate limiting — single-user local app.
- No streaming in chat; long agent calls block until done.

## Development history

1. Basic dashboard (ticker + dates → candlestick; v8 history, `http.agent` UA fix)
2. Tests & hardening (suites + JaCoCo; client extraction; generic 500s)
3. Top-5 panel (crumb-authenticated quotes)
4. Ticker autocomplete (search endpoint, combobox)
5. AI agent (LangChain4j tools over `StockService`, structured chart payloads)
6. Split-screen UI (assistant left, stocks right, inline chat charts)
