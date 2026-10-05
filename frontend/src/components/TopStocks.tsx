import { useEffect, useState } from 'react'

export interface TopStock {
  symbol: string
  name: string
  price: number
  changePercent: number
  marketCap: number
}

interface Props {
  onSelect: (symbol: string) => void
  selected?: string
}

function formatMarketCap(v: number): string {
  if (v >= 1e12) return `$${(v / 1e12).toFixed(2)}T`
  if (v >= 1e9) return `$${(v / 1e9).toFixed(1)}B`
  return `$${(v / 1e6).toFixed(0)}M`
}

export default function TopStocks({ onSelect, selected }: Props) {
  const [stocks, setStocks] = useState<TopStock[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/stocks/top')
      .then((res) => {
        if (!res.ok) throw new Error(`Failed to load top stocks (${res.status})`)
        return res.json()
      })
      .then(setStocks)
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load top stocks'))
  }, [])

  if (error) return null

  return (
    <div className="mt-8">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-400">
        Top 5 US stocks by market cap
      </h2>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
        {stocks.map((s, i) => {
          const up = s.changePercent >= 0
          const active = selected?.toUpperCase() === s.symbol
          return (
            <button
              key={s.symbol}
              onClick={() => onSelect(s.symbol)}
              className={`rounded-xl p-4 text-left ring-1 transition ${
                active
                  ? 'bg-emerald-950/40 ring-emerald-600'
                  : 'bg-gray-900 ring-gray-800 hover:ring-gray-600'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-base font-bold">{s.symbol}</span>
                <span className="text-xs text-gray-500">#{i + 1}</span>
              </div>
              <div className="mt-0.5 truncate text-xs text-gray-400">{s.name}</div>
              <div className="mt-2 text-sm font-semibold">${s.price.toFixed(2)}</div>
              <div className={`text-xs font-medium ${up ? 'text-emerald-400' : 'text-red-400'}`}>
                {up ? '+' : ''}
                {s.changePercent.toFixed(2)}%
              </div>
              <div className="mt-1 text-xs text-gray-500">{formatMarketCap(s.marketCap)}</div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
