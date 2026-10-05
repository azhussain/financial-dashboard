# Financial Dashboard — High-Level Design

## 1. System Overview

A single-page application that renders historical US stock prices as candlestick charts. The frontend talks to a Spring Boot REST API, which fetches market data from the Yahoo Finance API — historical candles through the `yahoofinance-api` Java library, and quotes/search through Yahoo's HTTP endpoints directly (cookie + crumb authentication).

```mermaid
flowchart LR
    subgraph Client["Browser"]
        UI["React SPA<br/>(TypeScript, Tailwind)"]
        Chart["CandleChart<br/>(ApexCharts)"]
        Tick["TickerInput<br/>(autocomplete)"]
        Top["TopStocks<br/>(market-cap cards)"]
        UI --> Chart & Tick & Top
    end

    subgraph Vite["Vite Dev Server :5173"]
        Proxy["/api proxy → :8080"]
    end

    subgraph Backend["Spring Boot :8080"]
        Ctrl["StockController<br/>/api/stocks"]
        Svc["StockService"]
        Client_YF["YahooFinanceClient"]
        CORS["CorsConfig"]
        DTO["DTOs<br/>Candle · QuoteSummary · SearchResult"]
        Ctrl --> Svc --> Client_YF
        CORS -.-> Ctrl
    end

    subgraph Yahoo["Yahoo Finance API"]
        V8["v8 chart<br/>(OHLC history)"]
        V7["v7 quote<br/>(market cap, price)"]
        V1["v1 search<br/>(symbol lookup)"]
        FC["fc.yahoo.com +<br/>/v1/test/getcrumb"]
    end

    Lib["yahoofinance-api<br/>library"]

    UI -->|"fetch /api/*"| Proxy --> Ctrl
    Client_YF --> Lib --> V8
    Client_YF -->|"cookie + crumb"| V7
    Client_YF --> V1
    Client_YF -.->|"obtain crumb"| FC
```

## 2. Components

| Layer | Component | Responsibility |
|---|---|---|
| Frontend | `App` | Page state: ticker, date range, candles, loading/error |
| Frontend | `TickerInput` | Debounced (250 ms) autocomplete combobox; keyboard navigation (↑↓/Enter/Esc) |
| Frontend | `TopStocks` | Top-5-by-market-cap cards; click loads that ticker's chart |
| Frontend | `CandleChart` | ApexCharts candlestick rendering of OHLC data |
| Edge | Vite dev proxy | Forwards `/api/*` → `localhost:8080` (avoids CORS in dev) |
| Backend | `StockController` | REST endpoints, input validation, error mapping |
| Backend | `StockService` | Business logic: date-range handling, US-equity filtering, market-cap ranking |
| Backend | `YahooFinanceClient` | All Yahoo calls: history (library), quotes (crumb), search |
| Backend | `CorsConfig` | Allows `GET` from `http://localhost:5173` |

## 3. REST API

```
GET /api/stocks/{ticker}/history?from=YYYY-MM-DD&to=YYYY-MM-DD   → Candle[]
GET /api/stocks/top?limit=5                                      → QuoteSummary[]
GET /api/stocks/search?q=keyword                                 → SearchResult[]
```

## 4. Request Flows

### Load candlestick chart

```mermaid
sequenceDiagram
    actor U as User
    participant F as React SPA
    participant B as StockController
    participant S as StockService
    participant C as YahooFinanceClient
    participant L as yahoofinance-api
    participant Y as Yahoo v8 chart

    U->>F: Ticker + dates → "Load chart"
    F->>B: GET /api/stocks/AAPL/history?from=..&to=..
    B->>B: validate (from ≤ to)
    B->>S: getHistory("AAPL", from, to)
    S->>C: getHistory (to + 1 day, inclusive end)
    C->>L: HistQuotesQuery2V8Request
    L->>Y: GET /v8/finance/chart/AAPL?...
    Y-->>C: quotes[]
    C-->>S: HistoricalQuote[]
    S-->>B: Candle[] (null-close filtered)
    B-->>F: 200 JSON
    F->>U: Candlestick chart
```

### Ticker autocomplete

```mermaid
sequenceDiagram
    actor U as User
    participant F as TickerInput
    participant B as StockController
    participant C as YahooFinanceClient
    participant Y as Yahoo v1 search

    U->>F: types "micro"
    F->>F: debounce 250 ms
    F->>B: GET /api/stocks/search?q=micro
    B->>C: search("micro")
    C->>Y: GET /v1/finance/search?q=micro
    Y-->>C: quotes (mixed types/exchanges)
    C-->>B: SearchResult[]
    B->>B: filter EQUITY + US exchanges, limit 8
    B-->>F: [MU, AMD, MSFT, ...]
    F->>U: suggestion dropdown
```

### Top-5 quotes (crumb-authenticated)

```mermaid
sequenceDiagram
    participant F as TopStocks
    participant B as StockController
    participant C as YahooFinanceClient
    participant Y as Yahoo Finance

    F->>B: GET /api/stocks/top
    B->>C: getQuotes(12 candidates)
    alt no cached crumb
        C->>Y: GET fc.yahoo.com (obtain A1 cookie)
        C->>Y: GET /v1/test/getcrumb
        Y-->>C: crumb
    end
    C->>Y: GET /v7/finance/quote?symbols=..&crumb=..
    Y-->>C: quoteResponse (price, change%, marketCap)
    C-->>B: QuoteSummary[]
    B->>B: sort by marketCap desc, take 5
    B-->>F: top 5
```

## 5. Key Design Decisions

- **`HistQuotesQuery2V8Request` over `YahooFinance.get()`** — the library's default path hits Yahoo's deprecated v7 endpoints (429/401). The v8 chart endpoint still works unauthenticated.
- **Manual crumb flow for quotes** — v7/quote requires Yahoo's A1 cookie + crumb. `YahooFinanceClient` fetches them lazily and retries once on 401/403.
- **Browser User-Agent required** — Yahoo returns 429 to the default Java UA. Set via `http.agent` at startup (covers the library) and per-request header on `HttpClient` calls.
- **Inclusive end date** — Yahoo's `period2` is exclusive; backend adds one day so the user's end date is included.
- **Server-side filtering for search** — Yahoo returns futures, foreign listings, funds; the backend narrows to US equities (NMS/NYQ/ASE/NGM/NCM/PCX/BTS) and caps at 8.
- **Generic 500 responses** — internal exception messages are logged, never echoed to clients.

## 6. Testing & Quality

| Suite | Scope |
|---|---|
| `StockServiceTest` | Unit: mapping, inclusive-end-date, top-N sorting, US-equity filtering |
| `StockControllerTest` | `@WebMvcTest`: endpoint contract, validation, error mapping |
| `ApiSecurityTest` | `@SpringBootTest`: CORS policy, HTTP methods, injection payloads, error leakage |
| `App.test.tsx` | Vitest + RTL: form, autocomplete flow, card-click loading, error states |
| JaCoCo | Coverage report → `backend/target/site/jacoco/` |
