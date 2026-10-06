# financial-dashboard

**Global Stocks Dashboard** — renders historical stock prices as candlestick charts with a conversational **Virtual Agent**, powered by the Yahoo Finance API.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the high-level design.

## Features

- **Regional market overview** — top 5 stocks by live market cap for **Americas**, **EMEA**, and **APAC**, shown as watchlist-style vertical lists; click a row to load its chart
- **Candlestick chart** for any ticker over a selectable date range (ApexCharts), inside a collapsible "Stock details" panel that auto-expands on selection
- **Smart ticker autocomplete** — suggestions (symbol, company, exchange) as you type, with keyboard navigation
- **KPI strip + range presets** — last price, change %, day range, volume; 1M/3M/6M/1Y/YTD quick ranges; skeleton loaders
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
│       │   ├── controller/                        StockController · AgentController
│       │   ├── service/                           StockService · MarketAgentService
│       │   ├── agent/                             MarketAgent · StockTools (@Tool)
│       │   ├── dto/                               Candle · QuoteSummary · SearchResult · Chat* · ChartPayload
│       │   └── config/CorsConfig.java
│       └── test/java/com/example/dashboard/       unit + controller + security tests
├── frontend/         Vite + React single-page app
│   └── src/components/   TopStocks · TickerInput · CandleChart · KpiStrip · AgentChat
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

Open http://localhost:5173 — the landing view shows the three regional watchlists. Click any stock row (or expand **Stock details** and type in the ticker box — suggestions appear as you type) to load KPIs and the candlestick chart. The **Virtual Agent** launcher sits bottom-right.

The Vite dev server proxies `/api` requests to the backend, so no extra configuration is needed.

## API

| Endpoint | Description |
|---|---|
| `GET /api/stocks/{ticker}/history?from=YYYY-MM-DD&to=YYYY-MM-DD` | Daily OHLC candles (end date inclusive) |
| `GET /api/stocks/top?limit=5` | Top N US stocks by live market cap |
| `GET /api/stocks/markets?limit=5` | Top N stocks per region — `{AMERICAS, EMEA, APAC}` |
| `GET /api/stocks/search?q=keyword` | Up to 8 US-listed equities matching the query |
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

`/markets` returns a `QuoteSummary[]` per region, each with `symbol`, `name`, `price`, `changePercent`, `marketCap`, `currency` (local listing currency), and `marketTime` (quote epoch). Ranking is USD-normalized via Yahoo FX pairs.

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

## Notes

- Yahoo Finance rejects requests without a browser User-Agent (HTTP 429) — the backend sets one via `http.agent` at startup and on `HttpClient` requests.
- Quote data (top/markets) requires a Yahoo cookie + crumb; `YahooFinanceClient` obtains them automatically and retries on 401/403.
- `/api/stocks/markets` ranks curated large-cap candidate lists per region (~14 each) rather than a full index screen; market caps are normalized to USD using Yahoo FX rates (`{CCY}USD=X`, falling back to `USD{CCY}=X` inversion) before ranking.
- The selected end date is inclusive; the backend adds one day internally since Yahoo treats the range end as exclusive.
- London-listed tickers are excluded from the EMEA candidates because Yahoo quotes them in pence.
- Candles are split/dividend-adjusted (OHLC scaled by `adjClose/close`) and dated in UTC; ASX-listed candles may date one day early versus exchange date since the Yahoo history endpoint's exchange timezone isn't exposed.
- Quotes are cached for 15s server-side so the dashboard and the agent see the same snapshot within a fetch window.
