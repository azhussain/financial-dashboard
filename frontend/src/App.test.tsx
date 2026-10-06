import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

// ApexCharts renders into canvas/SVG that jsdom can't handle — stub it.
vi.mock('react-apexcharts', () => ({
  default: ({ series }: { series: { name?: string }[] }) => (
    <div
      data-testid="candlestick-chart"
      data-series={JSON.stringify(series)}
      data-symbol={series?.[0]?.name}
    />
  ),
}))

const MARKETS = {
  AMERICAS: [
    { symbol: 'NVDA', name: 'NVIDIA Corporation', price: 238.9, changePercent: 2.12, marketCap: 5.77e12, currency: 'USD' },
    { symbol: 'AAPL', name: 'Apple Inc.', price: 332.89, changePercent: -0.24, marketCap: 4.86e12, currency: 'USD' },
  ],
  EMEA: [
    { symbol: 'ASML.AS', name: 'ASML Holding', price: 850.5, changePercent: 1.1, marketCap: 3.3e11, currency: 'EUR' },
  ],
  APAC: [
    { symbol: '0700.HK', name: 'Tencent Holdings', price: 610.0, changePercent: 0.8, marketCap: 5.6e12, currency: 'HKD' },
  ],
}

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
    if (url.includes('/api/stocks/markets')) {
      return { ok: true, json: async () => MARKETS } as Response
    }
    if (url.includes('/api/stocks/search')) {
      return { ok: true, json: async () => SEARCH_RESULTS } as Response
    }
    if (url.includes('/api/agent/chat')) {
      return {
        ok: true,
        json: async () => ({ message: 'NVDA is up 10%.', charts: [{ symbol: 'NVDA', candles: CANDLES }] }),
      } as Response
    }
    if (!historyOk) {
      return { ok: false, status: 400, json: async () => historyBody } as Response
    }
    return { ok: true, json: async () => historyBody } as Response
  })
}

vi.stubGlobal('fetch', mockFetch)

async function expandDetails() {
  await userEvent.click(screen.getByRole('button', { name: /stock details/i }))
}

describe('App', () => {
  beforeEach(() => {
    mockFetch.mockReset()
    mockApi()
  })

  it('renders ticker input, date pickers and load button when expanded', async () => {
    render(<App />)

    // controls are hidden behind the collapsed details section
    expect(screen.queryByPlaceholderText('AAPL')).not.toBeInTheDocument()

    await expandDetails()

    expect(screen.getByPlaceholderText('AAPL')).toBeInTheDocument()
    expect(screen.getByLabelText(/start date/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/end date/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /load chart/i })).toBeInTheDocument()
  })

  it('renders regional top stocks panels', async () => {
    render(<App />)

    expect(await screen.findByText(/Americas — top 2 by market cap/i)).toBeInTheDocument()
    expect(await screen.findByText(/EMEA — top 1 by market cap/i)).toBeInTheDocument()
    expect(await screen.findByText(/APAC — top 1 by market cap/i)).toBeInTheDocument()
    expect(await screen.findByText('NVDA')).toBeInTheDocument()
    expect(screen.getByText('5.77T USD')).toBeInTheDocument()
    expect(screen.getByText('0700.HK')).toBeInTheDocument()
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
    await expandDetails()

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
    await expandDetails()

    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('Please enter a ticker symbol')).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledTimes(1) // only the top-stocks fetch
  })

  it('fetches candles and renders the chart on success', async () => {
    render(<App />)
    await expandDetails()
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/stocks/AAPL/history?from='),
    )
  })

  it('ignores stale responses when the ticker changes quickly', async () => {
    let resolveNvda: (v: unknown) => void = () => {}
    mockFetch.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/stocks/markets')) {
        return Promise.resolve({ ok: true, json: async () => MARKETS } as Response)
      }
      if (url.includes('/api/stocks/NVDA/history')) {
        return new Promise((r) => { resolveNvda = r }) // slow request
      }
      return Promise.resolve({ ok: true, json: async () => CANDLES } as Response)
    })

    render(<App />)
    await userEvent.click(await screen.findByText('NVDA'))   // slow fetch starts
    await userEvent.click(await screen.findByText('AAPL'))   // fast fetch wins

    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
    expect(screen.getByTestId('candlestick-chart')).toHaveAttribute('data-symbol', 'AAPL')

    // late NVDA response arrives — must not overwrite the AAPL chart
    resolveNvda({ ok: true, json: async () => CANDLES })
    await waitFor(() =>
      expect(screen.getByTestId('candlestick-chart')).toHaveAttribute('data-symbol', 'AAPL'),
    )
  })

  it('auto-expands details when a stock is selected', async () => {
    render(<App />)

    const toggle = screen.getByRole('button', { name: /stock details/i })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByTestId('candlestick-chart')).not.toBeInTheDocument()

    await userEvent.click(await screen.findByText('NVDA'))

    await waitFor(() => expect(toggle).toHaveAttribute('aria-expanded', 'true'))
    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
  })

  it('loads chart with preset range when 1M is clicked', async () => {
    render(<App />)
    await expandDetails()

    await userEvent.click(screen.getByRole('button', { name: '1M' }))

    const expectedFrom = new Date()
    expectedFrom.setMonth(expectedFrom.getMonth() - 1)
    const from = expectedFrom.toISOString().slice(0, 10)

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/stocks/AAPL/history?from=${from}&to=`),
      ),
    )
    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
  })

  it('shows the API error message on failure', async () => {
    mockApi({ error: 'Unknown ticker symbol: FAKE' }, false)

    render(<App />)
    await expandDetails()
    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.type(input, 'FAKE')
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('Unknown ticker symbol: FAKE')).toBeInTheDocument()
  })

  it('sends a chat message and renders reply with a chart', async () => {
    render(<App />)

    // chat window opens via the floating launcher
    await userEvent.click(screen.getByRole('button', { name: /virtual agent/i }))

    const chatInput = screen.getByPlaceholderText(/ask about a stock/i)
    await userEvent.type(chatInput, 'Show NVDA last month')
    await userEvent.click(screen.getByRole('button', { name: /send/i }))

    expect(await screen.findByText('NVDA is up 10%.')).toBeInTheDocument()
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/agent/chat',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument()
  })

  it('shows an error when the network request rejects', async () => {
    render(<App />)
    await expandDetails()
    mockFetch.mockRejectedValueOnce(new Error('network down'))

    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('network down')).toBeInTheDocument()
  })
})
