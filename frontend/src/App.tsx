import { useState } from 'react'
import CandleChart, { type Candle } from './components/CandleChart'

function toISODate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

export default function App() {
  const today = new Date()
  const threeMonthsAgo = new Date()
  threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3)

  const [ticker, setTicker] = useState('AAPL')
  const [startDate, setStartDate] = useState(toISODate(threeMonthsAgo))
  const [endDate, setEndDate] = useState(toISODate(today))
  const [candles, setCandles] = useState<Candle[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function fetchData() {
    if (!ticker.trim()) {
      setError('Please enter a ticker symbol')
      return
    }
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(
        `/api/stocks/${encodeURIComponent(ticker.trim().toUpperCase())}/history?from=${startDate}&to=${endDate}`,
      )
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        throw new Error(body?.error ?? `Request failed with status ${res.status}`)
      }
      const data: Candle[] = await res.json()
      if (data.length === 0) {
        setError('No data found for this ticker in the selected range')
      }
      setCandles(data)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to fetch data')
      setCandles([])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="mx-auto max-w-6xl px-6 py-10">
        <h1 className="text-3xl font-bold tracking-tight">Financial Dashboard</h1>
        <p className="mt-1 text-sm text-gray-400">
          Historical stock prices powered by Yahoo Finance
        </p>

        <div className="mt-8 flex flex-wrap items-end gap-4 rounded-xl bg-gray-900 p-5 ring-1 ring-gray-800">
          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium uppercase tracking-wide text-gray-400">
              Ticker
            </span>
            <input
              type="text"
              value={ticker}
              onChange={(e) => setTicker(e.target.value)}
              placeholder="AAPL"
              className="w-32 rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm uppercase placeholder:text-gray-500 focus:border-emerald-500 focus:outline-none"
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium uppercase tracking-wide text-gray-400">
              Start date
            </span>
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm [color-scheme:dark] focus:border-emerald-500 focus:outline-none"
            />
          </label>

          <label className="flex flex-col gap-1.5">
            <span className="text-xs font-medium uppercase tracking-wide text-gray-400">
              End date
            </span>
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm [color-scheme:dark] focus:border-emerald-500 focus:outline-none"
            />
          </label>

          <button
            onClick={fetchData}
            disabled={loading}
            className="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {loading ? 'Loading…' : 'Load chart'}
          </button>
        </div>

        {error && (
          <div className="mt-4 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
            {error}
          </div>
        )}

        <div className="mt-6 rounded-xl bg-gray-900 p-5 ring-1 ring-gray-800">
          {candles.length > 0 ? (
            <CandleChart candles={candles} symbol={ticker.trim()} />
          ) : (
            !error && (
              <div className="flex h-96 items-center justify-center text-sm text-gray-500">
                Enter a ticker and date range, then press "Load chart"
              </div>
            )
          )}
        </div>
      </div>
    </div>
  )
}
