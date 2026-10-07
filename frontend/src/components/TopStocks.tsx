import { useEffect, useState } from 'react'
import { API_BASE } from '../api'

export interface TopStock {
  symbol: string
  name: string
  price: number
  changePercent: number
  marketCap: number
  currency: string
}

interface Props {
  onSelect: (symbol: string) => void
  selected?: string
}

const REGION_LABELS: Record<string, string> = {
  AMERICAS: 'Americas',
  EMEA: 'EMEA',
  APAC: 'APAC',
}

function money(v: number, currency: string): string {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(v)
  } catch {
    return v.toFixed(2)
  }
}

function formatMarketCap(v: number, currency: string): string {
  const scaled = v >= 1e12 ? `${(v / 1e12).toFixed(2)}T` : v >= 1e9 ? `${(v / 1e9).toFixed(1)}B` : `${(v / 1e6).toFixed(0)}M`
  return `${scaled} ${currency}`
}

function StockRow({
  stock,
  rank,
  active,
  onSelect,
}: {
  stock: TopStock
  rank: number
  active: boolean
  onSelect: (symbol: string) => void
}) {
  const up = stock.changePercent >= 0
  return (
    <button
      onClick={() => onSelect(stock.symbol)}
      className={`flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2.5 text-left ring-1 transition ${
        active
          ? 'bg-emerald-950/40 ring-emerald-600'
          : 'bg-gray-900 ring-gray-800 hover:ring-gray-600'
      }`}
    >
      <div className="flex min-w-0 items-center gap-3">
        <span className="w-4 shrink-0 text-xs text-gray-500">{rank}</span>
        <div className="min-w-0">
          <span className="text-sm font-bold">{stock.symbol}</span>
          <span className="ml-1.5 rounded bg-gray-800 px-1 py-0.5 text-[10px] font-medium uppercase tracking-wide text-emerald-300/90">
            {stock.currency}
          </span>
          <div className="truncate text-xs text-gray-400">{stock.name}</div>
        </div>
      </div>
      <div className="shrink-0 text-right">
        <div className="text-sm font-semibold tabular-nums">
          {money(stock.price, stock.currency)}
        </div>
        <div
          className={`text-xs font-medium tabular-nums ${up ? 'text-emerald-400' : 'text-red-400'}`}
        >
          {up ? '+' : ''}
          {stock.changePercent.toFixed(2)}%
          <span className="ml-1.5 text-gray-500">
            {formatMarketCap(stock.marketCap, stock.currency)}
          </span>
        </div>
      </div>
    </button>
  )
}

export default function TopStocks({ onSelect, selected }: Props) {
  const [regions, setRegions] = useState<Record<string, TopStock[]>>({})
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch(`${API_BASE}/api/stocks/markets?limit=20`)
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load top stocks (${res.status})`)
        return res.json()
      })
      .then(setRegions)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load top stocks'))
  }, [])

  if (error) return null

  const entries = Object.entries(regions)
  const labels = entries.length ? entries : Object.keys(REGION_LABELS).map((k) => [k, []] as [string, TopStock[]])

  return (
    <div className="grid gap-4 md:grid-cols-3">
      {labels.map(([region, stocks]) => (
        <div
          key={region}
          className="flex max-h-[32rem] flex-col rounded-xl bg-gray-900/60 p-4 ring-1 ring-gray-800"
        >
          <h2 className="text-sm font-semibold uppercase tracking-wider text-emerald-300">
            {REGION_LABELS[region] ?? region}
          </h2>
          <div className="mt-3 flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pr-1">
            {stocks.length === 0
              ? Array.from({ length: 20 }).map((_, i) => (
                  <div key={i} className="h-14 shrink-0 animate-pulse rounded-lg bg-gray-900 ring-1 ring-gray-800" />
                ))
              : stocks.map((s, i) => (
                  <StockRow
                    key={s.symbol}
                    stock={s}
                    rank={i + 1}
                    active={selected?.toUpperCase() === s.symbol}
                    onSelect={onSelect}
                  />
                ))}
          </div>
        </div>
      ))}
    </div>
  )
}
