import CandleChart, { type Candle } from './CandleChart'

export interface Quote {
  symbol: string
  name?: string
  exchange?: string
  price: number
  changePercent: number
  marketCap?: number
  currency: string
  marketTime?: number
  marketState?: string
}

interface Props {
  symbol: string
  quote: Quote | null
  candles: Candle[]
}

function fmtNumber(v: number, currency?: string): string {
  if (currency) {
    try {
      return new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
      }).format(v)
    } catch {
      /* fall through */
    }
  }
  return v.toLocaleString('en-US', { maximumFractionDigits: 2 })
}

function fmtCompact(v: number, currency?: string): string {
  const scaled =
    v >= 1e12 ? `${(v / 1e12).toFixed(2)} T` : v >= 1e9 ? `${(v / 1e9).toFixed(2)} B` : `${(v / 1e6).toFixed(2)} M`
  return currency ? `${scaled}${currency}` : scaled
}

function fmtVolume(v: number): string {
  return v >= 1e9
    ? `${(v / 1e9).toFixed(2)} B`
    : v >= 1e6
      ? `${(v / 1e6).toFixed(2)} M`
      : v.toLocaleString('en-US')
}

function marketStatusLabel(state?: string): string {
  switch (state) {
    case 'REGULAR':
      return 'Market Open'
    case 'PRE':
    case 'PREPRE':
      return 'Pre-market'
    case 'POST':
    case 'POSTPOST':
      return 'After hours'
    case 'CLOSED':
      return 'Market closed'
    default:
      return ''
  }
}

function fmtSourceTime(epochSec?: number): string {
  if (!epochSec) return ''
  const d = new Date(epochSec * 1000)
  const date = d.toLocaleDateString('en-US', { month: 'long', day: 'numeric' })
  const time = d.toLocaleTimeString('en-US', {
    hour: 'numeric',
    minute: '2-digit',
    timeZoneName: 'short',
  })
  return `${date}, ${time}`
}

export default function StockDetails({ symbol, quote, candles }: Props) {
  const last = candles[candles.length - 1]
  const price = quote?.price ?? last?.close ?? 0
  const currency = quote?.currency ?? ''

  // approximate absolute change from the reported percent
  const changeAbs =
    quote != null ? price - price / (1 + quote.changePercent / 100) : 0
  const changePct = quote?.changePercent ?? 0
  const up = changePct >= 0

  const status = marketStatusLabel(quote?.marketState)
  const stamp = fmtSourceTime(quote?.marketTime)

  const yearSpan = candles.length >= 200
  const stats: [string, string][] = last
    ? [
        ['Open', fmtNumber(last.open, currency)],
        ['Vol', fmtVolume(last.volume)],
        ['High', fmtNumber(last.high, currency)],
        ['Avg Vol', fmtVolume(candles.reduce((s, c) => s + c.volume, 0) / candles.length)],
        ['Low', fmtNumber(last.low, currency)],
        [yearSpan ? '52wk High' : 'Period High', fmtNumber(Math.max(...candles.map((c) => c.high)), currency)],
        ['Mkt Cap', quote?.marketCap ? fmtCompact(quote.marketCap, currency) : '—'],
        [yearSpan ? '52wk Low' : 'Period Low', fmtNumber(Math.min(...candles.map((c) => c.low)), currency)],
      ]
    : []

  return (
    <div>
      <div className="flex items-baseline gap-2">
        <span className="text-sm font-semibold text-gray-400">{symbol.toUpperCase()}</span>
        {quote?.exchange && <span className="text-xs text-gray-500">{quote.exchange}</span>}
      </div>
      <div className="mt-1 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="text-4xl font-semibold tabular-nums tracking-tight">
          {fmtNumber(price, undefined)}
          <span className="ml-1.5 text-base font-normal text-gray-400">{currency}</span>
        </span>
        <span
          className={`text-sm font-semibold tabular-nums ${up ? 'text-emerald-400' : 'text-red-400'}`}
        >
          {up ? '▲' : '▼'} {up ? '+' : ''}
          {changeAbs.toFixed(2)} ({up ? '+' : ''}
          {changePct.toFixed(2)}%){status === 'Market Open' ? ' today' : ''}
        </span>
      </div>
      <p className="mt-1 text-xs text-gray-500">
        {stamp}
        {status ? ` · ${status}` : ''}
      </p>

      <div className="mt-4">
        <CandleChart candles={candles} symbol={symbol} />
      </div>

      {stats.length > 0 && (
        <div className="mt-5 grid grid-cols-2 gap-x-8 border-t border-gray-800 pt-4 sm:grid-cols-2 lg:grid-cols-2">
          {stats.map(([label, value]) => (
            <div
              key={label}
              className="flex items-center justify-between border-b border-gray-800/60 py-2.5"
            >
              <span className="text-sm text-gray-400">{label}</span>
              <span className="text-sm font-semibold tabular-nums">{value}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
