# financial-dashboard

**Stocks Explorer** — a live global market dashboard that renders the top 20 stocks per region and candlestick charts for any ticker, with a conversational **Virtual Agent**, powered by the Yahoo Finance API.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the high-level design.

## Features

- **Markets Agent** — a backend agent refreshes each region's top 20 on a shared schedule (single-threaded, no overlap), keeps a shared cache of the latest valid snapshot, and pushes **only changed values** to dashboards over **server-sent events**; freshness metadata always advances, failed refreshes retain last-good data marked **STALE**
- **Adaptive cadence** — each region refreshes every **5 minutes while its exchanges are open** and every **30 minutes when closed**, driven by per-exchange `TradingWindow`s (including Tadawul's Sun–Thu week); a `closed` chip appears in the column header and the manual refresh button is disabled while closed
- **Regional watchlists** — Americas / APAC / EMEA columns with currency chips, market status, last-updated timestamps, and per-region manual **refresh buttons**; reconnecting viewers get the latest snapshot instantly
- **Stock details panel** — Google-Finance-style: live price + currency, change chip, source timestamp + market status, `1D 5D 1M 1Y 5Y Max` preset chips, candlestick chart (ApexCharts), and a two-column stats grid (Open/High/Low, Vol/Avg Vol, Mkt Cap, 52wk High/Low)
- **Smart ticker autocomplete** — suggestions (symbol, company, exchange) as you type, with keyboard navigation; programmatic selections never open the dropdown
- **Virtual Agent** — floating chat widget (bottom-right) powered by LangChain4j + OpenAI tool calling. Context-aware conversation memory, bullet-formatted answers grounded in the same live data the dashboard shows, and inline candlestick charts only when you ask for one ("show me a chart of NVDA")

## Tech stack

- **Backend:** Java 17, Spring Boot 3.5, [yahoofinance-api](https://github.com/sstrickx/yahoofinance-api), LangChain4j (OpenAI), JUnit 5, Mockito, JaCoCo
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4, ApexCharts, Vitest + Testing Library

## Project structure

```
financial-dashboard/
├── backend/          Spring Boot REST API
│   └── src/
│       ├── main/java/com/example/dashboard/
│       │   ├── client/YahooFinanceClient.java     all Yahoo API calls (cookie+crumb, UA)
│       │   ├── controller/                        StockController · AgentController · MarketStreamController · HealthController
│       │   ├── service/                           StockService · MarketAgentService · MarketsAgentService · MarketDataCache
│       │   ├── agent/                             MarketAgent · StockTools (@Tool) · RegionalMarketWorker · {Americas,Emea,Apac}MarketWorker
│       │   ├── dto/                               Candle · QuoteSummary · MarketQuote · RegionSnapshot · SearchResult · Chat* · ChartPayload
│       │   └── config/CorsConfig.java
│       └── test/java/com/example/dashboard/       unit + controller + security tests
├── frontend/         Vite + React single-page app
│   └── src/components/   TopStocks · TickerInput · StockDetails · CandleChart · AgentChat
└── docs/               ARCHITECTURE.md · PROJECT_SUMMARY.md · PROJECT_SUMMARY_PROMPT.md
```

## Prerequisites

- Java 17+
- Maven 3.9+
- Node.js 20+ and npm

## Running the app

Start the backend (http://localhost:8080):

```bash
cd backend
mvn spring-boot:run
```

Start the frontend (http://localhost:5173):

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 — the landing view shows the three regional watchlists, kept live by the Markets Agent's SSE stream. Click any stock row to auto-scroll into **Stock details** (price header, preset chips, candlestick chart, stats grid), or expand it and type in the ticker box. The **Virtual Agent** launcher sits bottom-right.

The Vite dev server proxies `/api` requests to the backend, so no extra configuration is needed.

## API

| Endpoint | Description |
|---|---|
| `GET /api/stocks/{ticker}/history?from=YYYY-MM-DD&to=YYYY-MM-DD` | Daily OHLC candles (end date inclusive) |
| `GET /api/stocks/{ticker}/quote` | Live `QuoteSummary` — price, change%, market cap, currency, exchange, `marketState`, `marketTime` |
| `GET /api/stocks/top?limit=5` | Top N US stocks by live market cap |
| `GET /api/stocks/markets?limit=20` | Top N stocks per region — `{AMERICAS, EMEA, APAC}` (legacy; SSE is the live path) |
| `GET /api/stocks/search?q=keyword` | Up to 8 US-listed equities matching the query |
| `GET /api/markets/stream` | **SSE** — `snapshot` event on connect, then `market-update` events with changed quotes + ranked order |
| `POST /api/markets/{region}/refresh` | Queue an out-of-band refresh for one region (202; 404 for unknown regions) |
| `GET /api/health` | `{"status":"ok"}` probe |
| `POST /api/agent/chat` `{message, sessionId}` | Virtual Agent — returns `{message, charts[]}` |

`/history` response example:

```json
[
  {
    "date": "2025-09-02",
    "open": 229.25,
    "high": 230.85,
    "low": 226.97,
    "close": 229.72,
    "volume": 44075600
  }
]
```

`/markets` returns a `QuoteSummary[]` per region, each with `symbol`, `name`, `price`, `changePercent`, `marketCap`, `currency` (local listing currency), `marketTime` (quote epoch), `exchange`, and `marketState`. Ranking is USD-normalized via Yahoo FX pairs.

### Markets Agent / SSE

A `snapshot` event (`{REGION: RegionSnapshot}`) is sent on connect so new or reconnected viewers immediately get the shared cache. Each `RegionSnapshot` carries ranked `MarketQuote[]` (`symbol`, `name`, `exchange`, `price`, `changePercent`, `marketCap`, `currency`, `sourceTimestamp`, `lastFetchTime`, `marketStatus`, `stale`) plus `lastSuccessAt`, `lastAttemptAt`, `consecutiveFailures`, and `stale`. After each refresh, a `market-update` event publishes `{region, lastSuccessAt, lastAttemptAt, stale, consecutiveFailures, changed[], order[], marketOpen}` — `changed` holds only rows whose displayed values moved, `order` the ranked symbols, and `marketOpen` whether any of the region's exchanges is in session (drives the adaptive cadence and the UI's closed/disabled-refresh state). A failed refresh publishes `stale: true` with an empty `changed` list so bad data can never overwrite valid values.

## Production deployment (Cloudflare + container host)

Cloudflare Pages/Workers cannot run the JVM, so the app deploys split: static frontend on **Cloudflare Pages**, Spring Boot container on any host (Fly.io, Render, Railway, VPS) fronted by a Cloudflare-proxied `api.` subdomain.

**Backend** — `backend/Dockerfile` is a multi-stage Maven → JRE build:

```bash
cd backend && docker build -t stocks-api .
docker run -e OPENAI_API_KEY=sk-... -e CORS_ALLOWED_ORIGINS=https://your-app.pages.dev -p 8080:8080 stocks-api
```

Environment variables: `PORT` (default 8080), `OPENAI_API_KEY` (enables the agent), `CORS_ALLOWED_ORIGINS` (comma-separated). Health probe: `GET /api/health`.

**Frontend** — Cloudflare Pages project: build command `npm run build`, output `dist`, root `frontend/`. Set the API origin at build time:

```bash
VITE_API_BASE=https://api.yourdomain.com npm run build
```

(`frontend/public/_redirects` ships the SPA fallback.) Then DNS-proxy `api.yourdomain.com` through Cloudflare and add a WAF rate-limit rule on `/api/agent/chat` (e.g. 10 req/min per IP) to protect the OpenAI quota. Note: long agent answers (~30–120s) can approach Cloudflare's ~100s proxy timeout — consider streaming if this becomes an issue.

## Testing

```bash
cd backend   && mvn test            # unit + API + security tests, JaCoCo → target/site/jacoco/
cd frontend  && npm run test        # Vitest + Testing Library
cd frontend  && npm run test:coverage
```

## Virtual Agent

Set `OPENAI_API_KEY` to enable the chat widget (model configurable via `agent.openai.model`):

```bash
export OPENAI_API_KEY=sk-...   # backend picks it up on startup
```

Without a key, `POST /api/agent/chat` returns 503 and the rest of the app works normally.

Agent tools (`StockTools`): `searchStocks`, `getTopStocks`, `getRegionalTopStocks`, `getStockHistory`. The agent keeps a 20-message conversation memory **per session** — the frontend sends a `sessionId` (random UUID stored in `sessionStorage`, so each browser tab gets an isolated conversation). Answers are markdown bullets, prices render in each listing's local currency, and charts are attached only when the message explicitly asks for one (chart / graph / plot / candlestick / visualize).

## Development iterations

| # | Iteration | Highlights |
|---|---|---|
| 1 | Basic dashboard | Ticker textbox, two date pickers, candlestick chart; Yahoo v8 history via `yahoofinance-api`; `http.agent` User-Agent workaround for Yahoo's 429s |
| 2 | Tests & hardening | Backend unit/MockMvc/security suite + JaCoCo; frontend Vitest suite; extracted `YahooFinanceClient` for mockability; generic 500s (no internal leakage) |
| 3 | Top-5 panel | `/api/stocks/top` ranks large-caps by live market cap; cookie+crumb flow for Yahoo v7 quotes with auto-refresh |
| 4 | Ticker autocomplete | `/api/stocks/search` filtered to US equities; `TickerInput` combobox with debounce, keyboard nav, and ARIA roles |
| 5 | AI agent | LangChain4j `AiServices` + OpenAI with `@Tool`s over `StockService`; charts fetched by tools returned as structured payloads |
| 6 | Split-screen UI | Assistant chat docked left; top-5 cards + controls + chart right; compact inline charts in chat |
| 7 | Modern UX | KPI strip, 1M–YTD range presets, skeleton loaders, `tabular-nums` |
| 8 | Selective details | Single-page layout — details + chart revealed only after a stock is chosen; collapsible panel that auto-expands on selection |
| 9 | Regional markets | `/api/stocks/markets` — Americas/EMEA/APAC top-5 ranked by market cap; `currency` field on quotes; watchlist-style vertical lists; renamed to Global Stocks Dashboard |
| 10 | Virtual Agent | Floating chat widget (bottom-right, bot icon); conversation memory; bulleted answers; charts only on request; local-currency pricing |
| 11 | Correctness pass | USD-normalized cross-currency ranking via Yahoo FX pairs; same-company dedupe; split-adjusted candles (`adjClose` ratio); UTC candle dates; 15s quote cache + `marketTime` for dashboard/agent consistency; stale-request guard against rapid ticker changes; per-session agent memory |
| 12 | Top-20 + branding | Candidate pools expanded (~25/region); scrollable watchlist columns; Global Stock Explorer hero + disclaimer/contact footer; Cloudflare deploy readiness (Dockerfile, env CORS/PORT, `/api/health`, `VITE_API_BASE`, Pages `_redirects`) |
| 13 | Markets Agent + SSE | `RegionalMarketWorker` abstraction + Americas/EMEA/APAC workers; shared 5-min scheduler with bounded backoff and no overlap; `MarketDataCache` diffing publishes only changed quotes; SSE `snapshot`/`market-update` events + heartbeat; memoized rows so unchanged values never re-render; STALE markers on outage; per-region manual refresh (`POST /api/markets/{region}/refresh`) |
| 14 | Stock details redesign | Google-Finance-style panel — live quote header (price/currency/change/status/timestamp via `GET /{symbol}/quote`), `1D–Max` chips, stats grid (Open/High/Low, Vol/Avg Vol, Mkt Cap, 52wk); single controls row; scroll-to-details on tile click; dropdown no longer opens on programmatic ticker sets; renamed Stocks Explorer |
| 15 | Adaptive cadence | Per-region `TradingWindow`s (NYSE/TSX, Tokyo/HK/Seoul/Taipei/Sydney, Continental Europe + Tadawul Sun–Thu); 5-min refresh while open, 30-min while closed; `marketOpen` flows through snapshot/SSE events → `closed` chip + disabled refresh button |

## Notes

- Yahoo Finance rejects requests without a browser User-Agent (HTTP 429) — the backend sets one via `http.agent` at startup and on `HttpClient` requests.
- Quote data (top/markets) requires a Yahoo cookie + crumb; `YahooFinanceClient` obtains them automatically and retries on 401/403.
- `/api/stocks/markets` and the Markets Agent rank curated large-cap candidate pools per region (~25 each) rather than a full index screen — the whole pool is refreshed each cycle so new entrants can displace the incumbent top 20; market caps are normalized to USD using Yahoo FX rates (`{CCY}USD=X`, falling back to `USD{CCY}=X` inversion) before ranking.
- The Markets Agent uses regular-session prices (`regularMarketPrice`); extended-hours prices are not blended in. `marketState` (REGULAR/PRE/POST/CLOSED) comes from the quote and drives the UI's market status labels.
- Regional market hours come from `TradingWindow` declarations on each worker — a region is "open" when any covered exchange is in session. Holidays are not modeled: on exchange holidays the agent refreshes at the open cadence and simply finds unchanged values.
- The selected end date is inclusive; the backend adds one day internally since Yahoo treats the range end as exclusive.
- London-listed tickers are excluded from the EMEA candidates because Yahoo quotes them in pence.
- Candles are split/dividend-adjusted (OHLC scaled by `adjClose/close`) and dated in UTC; ASX-listed candles may date one day early versus exchange date since the Yahoo history endpoint's exchange timezone isn't exposed.
- Quotes are cached for 15s server-side so the dashboard and the agent see the same snapshot within a fetch window.
