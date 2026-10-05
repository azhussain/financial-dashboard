import Chart from 'react-apexcharts'
import type { ApexOptions } from 'apexcharts'

export interface Candle {
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

interface Props {
  candles: Candle[]
  symbol: string
}

export default function CandleChart({ candles, symbol }: Props) {
  const series = [
    {
      name: symbol,
      data: candles.map((c) => ({
        x: new Date(c.date),
        y: [c.open, c.high, c.low, c.close],
      })),
    },
  ]

  const options: ApexOptions = {
    chart: {
      type: 'candlestick',
      background: 'transparent',
      toolbar: { show: true },
      zoom: { enabled: true },
    },
    theme: { mode: 'dark' },
    title: {
      text: `${symbol.toUpperCase()} — Daily Candlestick`,
      align: 'left',
      style: { color: '#e5e7eb' },
    },
    xaxis: {
      type: 'datetime',
      labels: { style: { colors: '#9ca3af' } },
    },
    yaxis: {
      tooltip: { enabled: true },
      labels: {
        style: { colors: '#9ca3af' },
        formatter: (v) => `$${v.toFixed(2)}`,
      },
    },
    plotOptions: {
      candlestick: {
        colors: {
          upward: '#10b981',
          downward: '#ef4444',
        },
      },
    },
    grid: { borderColor: '#374151' },
    tooltip: { theme: 'dark' },
  }

  return (
    <Chart options={options} series={series} type="candlestick" height={480} />
  )
}
