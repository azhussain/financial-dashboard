import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

// ApexCharts renders into canvas/SVG that jsdom can't handle — stub it.
vi.mock('react-apexcharts', () => ({
  default: ({ series }: { series: unknown[] }) => (
    <div data-testid="candlestick-chart" data-series={JSON.stringify(series)} />
  ),
}))

const TOP_STOCKS = [
  { symbol: 'NVDA', name: 'NVIDIA Corporation', price: 238.9, changePercent: 2.12, marketCap: 5.77e12 },
  { symbol: 'AAPL', name: 'Apple Inc.', price: 332.89, changePercent: -0.24, marketCap: 4.86e12 },
]

const CANDLES = [
  { date: '2025-09-02', open: 229.25, high: 230.85, low: 226.97, close: 229.72, volume: 44075600 },
]

const SEARCH_RESULTS = [
  { symbol: 'MSFT', name: 'Microsoft Corporation', exchange: 'NMS', quoteType: 'EQUITY' },
  { symbol: 'MS', name: 'Morgan Stanley', exchange: 'NYQ', quoteType: 'EQUITY' },
]

const mockFetch = vi.fn()

function mockApi(historyBody: unknown = CANDLES, historyOk = true) {
  mockFetch.mockImplementation(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.includes('/api/stocks/top')) {
      return { ok: true, json: async () => TOP_STOCKS } as Response
    }
    if (url.includes('/api/stocks/search')) {
      return { ok: true, json: async () => SEARCH_RESULTS } as Response
    }
    if (!historyOk) {
      return { ok: false, status: 400, json: async () => historyBody } as Response
    }
    return { ok: true, json: async () => historyBody } as Response
  })
}

vi.stubGlobal('fetch', mockFetch)

describe('App', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    mockApi()
  })

  it('renders ticker input, date pickers and load button', () => {
    render(<App />)

    expect(screen.getByPlaceholderText('AAPL')).toBeInTheDocument()
    expect(screen.getByLabelText(/start date/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/end date/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /load chart/i })).toBeInTheDocument()
  })

  it('renders the top 5 US stocks panel', async () => {
    render(<App />)

    expect(
      await screen.findByText('Top 5 US stocks by market cap'),
    ).toBeInTheDocument()
    expect(await screen.findByText('NVDA')).toBeInTheDocument()
    expect(screen.getByText('$5.77T')).toBeInTheDocument()
  })

  it('loads the chart when a top stock card is clicked', async () => {
    render(<App />)

    await userEvent.click(await screen.findByText('NVDA'))

    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/stocks/NVDA/history?from='),
    )
  })

  it('shows suggestions while typing and selecting one fills the ticker', async () => {
    render(<App />)

    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.type(input, 'micro')

    expect(await screen.findByText('Microsoft Corporation')).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/stocks/search?q=micro'),
    )

    await userEvent.click(screen.getByText('Microsoft Corporation'))
    expect(input).toHaveValue('MSFT')
  })

  it('shows an error when ticker is empty', async () => {
    render(<App />)

    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('Please enter a ticker symbol')).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledTimes(1) // only the top-stocks fetch
  })

  it('fetches candles and renders the chart on success', async () => {
    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/stocks/AAPL/history?from='),
    )
  })

  it('shows the API error message on failure', async () => {
    mockApi({ error: 'Unknown ticker symbol: FAKE' }, false)

    render(<App />)
    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.type(input, 'FAKE')
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('Unknown ticker symbol: FAKE')).toBeInTheDocument()
  })

  it('shows an error when the network request rejects', async () => {
    render(<App />)
    mockFetch.mockRejectedValueOnce(new Error('network down'))

    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('network down')).toBeInTheDocument()
  })
})
