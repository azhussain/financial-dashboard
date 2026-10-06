import type { Candle } from './CandleChart'

interface Props {
  candles: Candle[]
}

function fmtPrice(v: number): string {
  return `$${v.toFixed(2)}`
}

function fmtVolume(v: number): string {
  if (v >= 1e9) return `${(v / 1e9).toFixed(2)}B`
  if (v >= 1e6) return `${(v / 1e6).toFixed(1)}M`
  if (v >= 1e3) return `${(v / 1e3).toFixed(0)}K`
  return String(v)
}

function Item({ label, value, accent }: { label: string; value: string; accent?: 'up' | 'down' }) {
  const color = accent === 'up' ? 'text-emerald-400' : accent === 'down' ? 'text-red-400' : ''
  return (
    <div className="rounded-lg bg-gray-800/60 px-4 py-3">
      <div className="text-xs font-medium uppercase tracking-wide text-gray-500">{label}</div>
      <div className={`mt-1 text-lg font-semibold tabular-nums ${color}`}>{value}</div>
    </div>
  )
}

export default function KpiStrip({ candles }: Props) {
  const last = candles[candles.length - 1]
  const prev = candles.length > 1 ? candles[candles.length - 2] : null
  const change = prev ? last.close - prev.close : 0
  const pct = prev ? (change / prev.close) * 100 : 0
  const up = change >= 0
  const periodHigh = Math.max(...candles.map((c) => c.high))
  const periodLow = Math.min(...candles.map((c) => c.low))

  return (
    <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 xl:grid-cols-5">
      <Item label="Last price" value={fmtPrice(last.close)} />
      <Item
        label="Day change"
        value={`${up ? '+' : ''}${change.toFixed(2)} (${up ? '+' : ''}${pct.toFixed(2)}%)`}
        accent={up ? 'up' : 'down'}
      />
      <Item label="Day range" value={`${fmtPrice(last.low)} – ${fmtPrice(last.high)}`} />
      <Item label="Volume" value={fmtVolume(last.volume)} />
      <Item label="Period range" value={`${fmtPrice(periodLow)} – ${fmtPrice(periodHigh)}`} />
    </div>
  )
}
