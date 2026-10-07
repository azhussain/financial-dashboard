import { useRef, useState } from 'react'
import { API_BASE } from './api'
import AgentChat from './components/AgentChat'
import CandleChart, { type Candle } from './components/CandleChart'
import KpiStrip from './components/KpiStrip'
import TickerInput from './components/TickerInput'
import TopStocks from './components/TopStocks'

function toISODate(d: Date): string {
  return d.toISOString().slice(0, 10)
}

const PRESETS: { label: string; getDates: () => [string, string] }[] = [
  { label: '1M', getDates: () => monthsAgo(1) },
  { label: '3M', getDates: () => monthsAgo(3) },
  { label: '6M', getDates: () => monthsAgo(6) },
  { label: '1Y', getDates: () => monthsAgo(12) },
  { label: 'YTD', getDates: () => [`${new Date().getFullYear()}-01-01`, toISODate(new Date())] },
]

function monthsAgo(n: number): [string, string] {
  const d = new Date()
  d.setMonth(d.getMonth() - n)
  return [toISODate(d), toISODate(new Date())]
}

export default function App() {
  const today = new Date()
  const threeMonthsAgo = new Date()
  threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3)

  const [ticker, setTicker] = useState('AAPL')
  const [startDate, setStartDate] = useState(toISODate(threeMonthsAgo))
  const [endDate, setEndDate] = useState(toISODate(today))
  const [candles, setCandles] = useState<Candle[]>([])
  const [selected, setSelected] = useState(false)
  const [expanded, setExpanded] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Monotonic id: stale in-flight responses must not overwrite a newer selection.
  const requestSeq = useRef(0)

  async function fetchData(symbol?: string, from?: string, to?: string) {
    const target = (symbol ?? ticker).trim()
    if (!target) {
      setError('Please enter a ticker symbol')
      return
    }
    if (symbol) setTicker(symbol)
    setSelected(true)
    setExpanded(true)
    const f = from ?? startDate
    const t = to ?? endDate
    const seq = ++requestSeq.current
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(
        `${API_BASE}/api/stocks/${encodeURIComponent(target.toUpperCase())}/history?from=${f}&to=${t}`,
      )
      if (!res.ok) {
        const body = await res.json().catch(() => null)
        throw new Error(body?.error ?? `Request failed with status ${res.status}`)
      }
      const data: Candle[] = await res.json()
      if (seq !== requestSeq.current) return // a newer request superseded this one
      if (data.length === 0) {
        setError('No data found for this ticker in the selected range')
      }
      setCandles(data)
    } catch (e) {
      if (seq !== requestSeq.current) return
      setError(e instanceof Error ? e.message : 'Failed to fetch data')
      setCandles([])
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <div className="mx-auto max-w-[1600px] px-6 py-8">
        <header className="max-w-3xl">
          <div className="inline-flex items-center gap-2 rounded-full border border-emerald-500/30 bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-300">
            <span className="relative flex h-2 w-2">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-60" />
              <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-400" />
            </span>
            Powered by Yahoo Finance
          </div>
          <h1 className="mt-4 bg-gradient-to-r from-emerald-300 via-emerald-400 to-cyan-400 bg-clip-text text-4xl font-bold tracking-tight text-transparent sm:text-5xl">
            Global Stock Explorer
          </h1>
          <p className="mt-4 text-base leading-relaxed text-gray-400">
            Explore the top 20 stocks across global markets with interactive
            charts and key performance metrics. Ask the Virtual Agent to explain
            price movements, compare stocks, and uncover insights.
          </p>
        </header>

        <div className="mt-8">
          <div className="min-w-0">
            <TopStocks onSelect={(s) => fetchData(s)} selected={ticker} />

            <div className="mt-6 rounded-xl bg-gray-900 ring-1 ring-gray-800">
              <button
                type="button"
                aria-expanded={expanded}
                onClick={() => setExpanded((v) => !v)}
                className="flex w-full items-center justify-between px-5 py-4 text-left transition hover:bg-gray-800/50"
              >
                <div>
                  <span className="text-sm font-semibold uppercase tracking-wide text-gray-400">
                    Stock details
                  </span>
                  {!expanded && (
                    <p className="mt-1 text-xs font-normal normal-case tracking-normal text-gray-500">
                      Select a stock to view its chart
                    </p>
                  )}
                </div>
                <svg
                  className={`h-5 w-5 shrink-0 text-gray-400 transition-transform ${expanded ? 'rotate-180' : ''}`}
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              {expanded && (
                <div className="border-t border-gray-800 p-5">
                  <div className="flex flex-wrap items-end gap-4">
                    <TickerInput
                      value={ticker}
                      onChange={setTicker}
                      onSubmit={() => fetchData()}
                    />

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
                      onClick={() => fetchData()}
                      disabled={loading}
                      className="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-semibold text-white transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {loading ? 'Loading…' : 'Load chart'}
                    </button>

                    <div className="flex flex-col gap-1.5">
                      <span className="text-xs font-medium uppercase tracking-wide text-gray-400">
                        Range
                      </span>
                      <div className="flex gap-1">
                        {PRESETS.map((p) => (
                          <button
                            key={p.label}
                            onClick={() => {
                              const [f, t] = p.getDates()
                              setStartDate(f)
                              setEndDate(t)
                              fetchData(undefined, f, t)
                            }}
                            className="rounded-md border border-gray-700 bg-gray-800 px-2.5 py-2 text-xs font-medium text-gray-300 transition hover:border-emerald-600 hover:text-emerald-400"
                          >
                            {p.label}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  {error && (
                    <div className="mt-4 rounded-lg border border-red-800 bg-red-950/50 px-4 py-3 text-sm text-red-300">
                      {error}
                    </div>
                  )}

                  {selected ? (
                    <div className="mt-5">
                      {loading ? (
                        <div className="animate-pulse space-y-4">
                          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
                            {Array.from({ length: 5 }).map((_, i) => (
                              <div key={i} className="h-16 rounded-lg bg-gray-800" />
                            ))}
                          </div>
                          <div className="h-96 rounded-lg bg-gray-800" />
                        </div>
                      ) : (
                        candles.length > 0 && (
                          <>
                            <KpiStrip candles={candles} />
                            <div className="mt-4">
                              <CandleChart candles={candles} symbol={ticker.trim()} />
                            </div>
                          </>
                        )
                      )}
                    </div>
                  ) : (
                    <div className="mt-5 flex h-40 items-center justify-center rounded-lg border border-dashed border-gray-800 text-sm text-gray-500">
                      Select a stock above, or enter a ticker and press "Load chart"
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      <AgentChat />

      <footer className="mx-auto mt-12 max-w-[1600px] border-t border-gray-800 px-6 py-8">
        <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
          <div className="max-w-2xl space-y-2 text-xs leading-relaxed text-gray-500">
            <p className="font-semibold uppercase tracking-wide text-gray-400">
              Disclaimer
            </p>
            <p>
              Global Stock Explorer is provided for informational and educational
              purposes only and does not constitute investment, financial, legal,
              or tax advice, nor a recommendation or solicitation to buy or sell
              any security. Market data is sourced from Yahoo Finance and may be
              delayed, incomplete, or inaccurate; no warranty is made as to its
              accuracy or completeness. Virtual Agent responses are generated by
              AI and may contain errors. Past performance does not guarantee
              future results. Always do your own research and consult a licensed
              financial advisor before making investment decisions. Use of this
              application is at your own risk.
            </p>
          </div>
          <div className="shrink-0 text-xs text-gray-500">
            <p className="font-semibold uppercase tracking-wide text-gray-400">
              Contact
            </p>
            <p className="mt-2">Syed A Hussain</p>
            <a
              href="mailto:azhussain1@gmail.com"
              className="mt-1 inline-block text-emerald-400 transition hover:text-emerald-300"
            >
              azhussain1@gmail.com
            </a>
            <p className="mt-4 text-gray-600">
              © {new Date().getFullYear()} Global Stock Explorer
            </p>
          </div>
        </div>
      </footer>
    </div>
  )
}
