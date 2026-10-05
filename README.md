# financial-dashboard

A financial dashboard that renders historical US stock prices as a candlestick chart, powered by the Yahoo Finance API.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the high-level design.

## Features

- **Candlestick chart** for any US ticker over a selectable date range (ApexCharts)
- **Smart ticker autocomplete** — suggestions (symbol, company, exchange) as you type, with keyboard navigation
- **Top 5 US stocks panel** — ranked by live market cap; click a card to load its chart

## Tech stack

- **Backend:** Java 17, Spring Boot 3.5, [yahoofinance-api](https://github.com/sstrickx/yahoofinance-api), JUnit 5, Mockito, JaCoCo
- **Frontend:** React 19, TypeScript, Vite, Tailwind CSS v4, ApexCharts, Vitest + Testing Library

## Project structure

```
financial-dashboard/
├── backend/          Spring Boot REST API
│   └── src/
│       ├── main/java/com/example/dashboard/
│       │   ├── client/YahooFinanceClient.java    all Yahoo API calls
│       │   ├── controller/StockController.java   REST endpoints + validation
│       │   ├── service/StockService.java         filtering, ranking, mapping
│       │   ├── dto/                              Candle · QuoteSummary · SearchResult
│       │   └── config/CorsConfig.java
│       └── test/java/com/example/dashboard/      unit + security tests
├── frontend/         Vite + React single-page app
│   └── src/components/                           TickerInput · TopStocks · CandleChart
└── docs/ARCHITECTURE.md
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

Open http://localhost:5173, type in the ticker box (suggestions appear as you type), pick a start and end date, and press **Load chart** — or click one of the top-5 cards to load it directly.

The Vite dev server proxies `/api` requests to the backend, so no extra configuration is needed.

## API

| Endpoint | Description |
|---|---|
| `GET /api/stocks/{ticker}/history?from=YYYY-MM-DD&to=YYYY-MM-DD` | Daily OHLC candles (end date inclusive) |
| `GET /api/stocks/top?limit=5` | Top N US stocks by live market cap |
| `GET /api/stocks/search?q=keyword` | Up to 8 US-listed equities matching the query |

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

## Testing

```bash
cd backend   && mvn test            # unit + API + security tests, JaCoCo → target/site/jacoco/
cd frontend  && npm run test        # Vitest + Testing Library
cd frontend  && npm run test:coverage
```

## Development iterations

| # | Iteration | Highlights |
|---|---|---|
| 1 | Basic dashboard | Ticker textbox, two date pickers, candlestick chart; Yahoo v8 history via `yahoofinance-api`; `http.agent` User-Agent workaround for Yahoo's 429s |
| 2 | Tests & hardening | Backend unit/MockMvc/security suite + JaCoCo; frontend Vitest suite; extracted `YahooFinanceClient` for mockability; generic 500s (no internal leakage) |
| 3 | Top-5 panel | `/api/stocks/top` ranks large-caps by live market cap; cookie+crumb flow for Yahoo v7 quotes with auto-refresh |
| 4 | Ticker autocomplete | `/api/stocks/search` filtered to US equities; `TickerInput` combobox with debounce, keyboard nav, and ARIA roles |

## Notes

- Yahoo Finance rejects requests without a browser User-Agent (HTTP 429) — the backend sets one via `http.agent` at startup and on `HttpClient` requests.
- Quote data (top-5) requires a Yahoo cookie + crumb; `YahooFinanceClient` obtains them automatically and retries on 401/403.
- `/api/stocks/top` ranks a curated list of ~12 mega-cap candidates rather than a full S&P 500 screen — the ordering rarely changes and it's far cheaper.
- The selected end date is inclusive; the backend adds one day internally since Yahoo treats the range end as exclusive.
