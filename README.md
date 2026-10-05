# financial-dashboard

A basic financial dashboard that renders historical stock prices as a candlestick chart, powered by the Yahoo Finance API.

## Tech stack

- **Backend:** Java 17, Spring Boot, [yahoofinance-api](https://github.com/sstrickx/yahoofinance-api)
- **Frontend:** React, TypeScript, Vite, Tailwind CSS, ApexCharts

## Project structure

```
financial-dashboard/
├── backend/    Spring Boot REST API
└── frontend/   Vite + React single-page app
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

Open http://localhost:5173, enter a ticker symbol (e.g. `AAPL`), pick a start and end date, and press **Load chart**.

The Vite dev server proxies `/api` requests to the backend, so no extra configuration is needed.

## API

```
GET /api/stocks/{ticker}/history?from=YYYY-MM-DD&to=YYYY-MM-DD
```

Returns a list of daily candles:

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

## Notes

- Yahoo Finance rejects requests without a browser User-Agent (HTTP 429). The backend sets one via the `http.agent` system property at startup — see `DashboardApplication.java`.
- The selected end date is inclusive; the backend adds one day internally since Yahoo treats the range end as exclusive.
