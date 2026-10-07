import { memo, useEffect, useState } from 'react'
import { API_BASE } from '../api'

export interface TopStock {
  symbol: string
  name: string
  exchange?: string
  price: number
  changePercent: number
  marketCap: number
  currency: string
  marketStatus?: string
  stale?: boolean
}

interface RegionMeta {
  stale: boolean
  lastSuccessAt: number
  marketOpen?: boolean
}

interface RegionState {
  quotes: TopStock[]
  meta: RegionMeta
}

interface RegionSnapshot {
  region: string
  quotes: TopStock[]
  lastSuccessAt: number
  stale: boolean
  marketOpen: boolean
}

interface MarketUpdate {
  region: string
  lastSuccessAt: number
  stale: boolean
  changed: TopStock[]
  order: string[]
  marketOpen: boolean
}

interface Props {
  onSelect: (symbol: string) => void
  selected?: string
}

const REGION_LABELS: Record<string, string> = {
  AMERICAS: 'Americas',
  APAC: 'APAC',
  EMEA: 'EMEA',
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

const StockRow = memo(function StockRow({
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
          {stock.marketStatus && stock.marketStatus !== 'REGULAR' && (
            <span className="ml-1.5 rounded bg-gray-800 px-1 py-0.5 text-[10px] font-medium uppercase tracking-wide text-gray-400">
              {stock.marketStatus}
            </span>
          )}
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
})

export default function TopStocks({ onSelect, selected }: Props) {
  const [regions, setRegions] = useState<Record<string, RegionState>>({})
  const [pending, setPending] = useState<ReadonlySet<string>>(new Set())

  function refreshRegion(region: string) {
    setPending((p) => new Set(p).add(region))
    fetch(`${API_BASE}/api/markets/${region}/refresh`, { method: 'POST' })
      .catch(() => setPending((p) => {
        const n = new Set(p)
        n.delete(region)
        return n
      }))
  }

  useEffect(() => {
    const source = new EventSource(`${API_BASE}/api/markets/stream`)

    source.addEventListener('snapshot', (e) => {
      const snapshots = JSON.parse((e as MessageEvent).data) as Record<string, RegionSnapshot>
      setRegions(() =>
        Object.fromEntries(
          Object.entries(snapshots).map(([region, s]) => [
            region,
            {
              quotes: s.quotes,
              meta: { stale: s.stale, lastSuccessAt: s.lastSuccessAt, marketOpen: s.marketOpen },
            },
          ]),
        ),
      )
    })

    source.addEventListener('market-update', (e) => {
      const update = JSON.parse((e as MessageEvent).data) as MarketUpdate
      setPending((p) => {
        const n = new Set(p)
        n.delete(update.region)
        return n
      })
      setRegions((prev) => {
        const current = prev[update.region]
        const bySymbol = new Map((current?.quotes ?? []).map((q) => [q.symbol, q]))
        update.changed.forEach((q) => bySymbol.set(q.symbol, q))
        const ordered = update.order.length
          ? update.order.map((s) => bySymbol.get(s)).filter((q): q is TopStock => q != null)
          : Array.from(bySymbol.values())
        // identity-preserved quotes + memoized rows mean unchanged rows don't re-render
        return {
          ...prev,
          [update.region]: {
            quotes: ordered,
            meta: {
              stale: update.stale,
              lastSuccessAt: update.lastSuccessAt,
              marketOpen: update.marketOpen,
            },
          },
        }
      })
    })

    source.onerror = () => {
      // stream interrupted or backend down — keep last values, mark them stale
      setRegions((prev) =>
        Object.fromEntries(
          Object.entries(prev).map(([k, v]) => [k, { ...v, meta: { ...v.meta, stale: true } }]),
        ),
      )
    }

    return () => source.close()
  }, [])

  // Fixed column order AMERICAS → APAC → EMEA; any extra regions appended after.
  const empty: RegionState = { quotes: [], meta: { stale: false, lastSuccessAt: 0 } }
  const labels: [string, RegionState][] = [
    ...Object.keys(REGION_LABELS).map(
      (k) => [k, regions[k] ?? empty] as [string, RegionState],
    ),
    ...Object.keys(regions)
      .filter((r) => !(r in REGION_LABELS))
      .map((r) => [r, regions[r]] as [string, RegionState]),
  ]

  return (
    <div className="grid gap-4 md:grid-cols-3">
      {labels.map(([region, { quotes, meta }]) => (
        <div
          key={region}
          className="flex max-h-[32rem] flex-col rounded-xl bg-gray-900/60 p-4 ring-1 ring-gray-800"
        >
          <div className="flex items-baseline justify-between gap-2">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-emerald-300">
              {REGION_LABELS[region] ?? region}
            </h2>
            <div className="flex shrink-0 items-center gap-1.5">
              {meta.lastSuccessAt > 0 && (
                <span className="text-[10px] tabular-nums text-gray-500">
                  {meta.stale && (
                    <span className="mr-1.5 rounded bg-amber-900/50 px-1 py-0.5 font-semibold text-amber-300">
                      STALE
                    </span>
                  )}
                  {meta.marketOpen === false && (
                    <span className="mr-1.5 rounded bg-gray-800 px-1 py-0.5 font-medium text-gray-400">
                      closed
                    </span>
                  )}
                  {new Date(meta.lastSuccessAt).toLocaleTimeString()}
                </span>
              )}
              <button
                type="button"
                aria-label={`Refresh ${REGION_LABELS[region] ?? region}`}
                title={meta.marketOpen === false ? 'Market closed' : 'Refresh now'}
                onClick={() => refreshRegion(region)}
                disabled={pending.has(region) || meta.marketOpen === false}
                className="rounded p-1 text-gray-500 transition hover:bg-gray-800 hover:text-emerald-300 disabled:opacity-50"
              >
                <svg
                  className={`h-3.5 w-3.5 ${pending.has(region) ? 'animate-spin' : ''}`}
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M4 4v5h5M20 20v-5h-5M5.5 9A8 8 0 0 1 19 8M18.5 15A8 8 0 0 1 5 16"
                  />
                </svg>
              </button>
            </div>
          </div>
          <div className="mt-3 flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto pr-1">
            {quotes.length === 0
              ? Array.from({ length: 20 }).map((_, i) => (
                  <div key={i} className="h-14 shrink-0 animate-pulse rounded-lg bg-gray-900 ring-1 ring-gray-800" />
                ))
              : quotes.map((s, i) => (
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
