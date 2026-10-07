import { act, render, screen, waitFor } from '@testing-library/react'
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

// Fake SSE endpoint — TopStocks is driven by EventSource, not fetch.
class FakeEventSource extends EventTarget {
  static instances: FakeEventSource[] = []
  url: string
  onerror: (() => void) | null = null

  constructor(url: string) {
    super()
    this.url = url
    FakeEventSource.instances.push(this)
  }

  emit(name: string, data: unknown) {
    this.dispatchEvent(new MessageEvent(name, { data: JSON.stringify(data) }))
  }

  emitError() {
    this.onerror?.()
  }

  close() {}
}
vi.stubGlobal('EventSource', FakeEventSource)

function latestSource() {
  return FakeEventSource.instances[FakeEventSource.instances.length - 1]
}

function emit(name: string, data: unknown) {
  act(() => latestSource().emit(name, data))
}

function marketQuote(symbol: string, name: string, price: number, changePercent: number, marketCap: number, currency: string) {
  return {
    symbol, name, exchange: 'NasdaqGS', price, changePercent, marketCap,
    currency, sourceTimestamp: 1_700_000_000, lastFetchTime: 1_700_000_000_000,
    marketStatus: 'REGULAR', stale: false,
  }
}

function snapshot(quotes: ReturnType<typeof marketQuote>[]) {
  return {
    region: '', quotes, lastSuccessAt: 1_700_000_000_000, lastAttemptAt: 1_700_000_000_000,
    consecutiveFailures: 0, stale: false, marketOpen: true,
  }
}

const SNAPSHOTS = {
  AMERICAS: { ...snapshot([]), quotes: [
    marketQuote('NVDA', 'NVIDIA Corporation', 238.9, 2.12, 5.77e12, 'USD'),
    marketQuote('AAPL', 'Apple Inc.', 332.89, -0.24, 4.86e12, 'USD'),
  ] },
  EMEA: { ...snapshot([]), quotes: [
    marketQuote('ASML.AS', 'ASML Holding', 850.5, 1.1, 3.3e11, 'EUR'),
  ] },
  APAC: { ...snapshot([]), quotes: [
    marketQuote('0700.HK', 'Tencent Holdings', 610.0, 0.8, 5.6e12, 'HKD'),
  ] },
}

const CANDLES = [
  { date: '2025-09-02', open: 229.25, high: 230.85, low: 226.97, close: 229.72, volume: 44075600 },
]

const QUOTE = {
  symbol: 'AAPL',
  name: 'Apple Inc.',
  exchange: 'NasdaqGS',
  price: 332.89,
  changePercent: -0.24,
  marketCap: 4.86e12,
  currency: 'USD',
  marketTime: 1_700_000_000,
  marketState: 'REGULAR',
}

const SEARCH_RESULTS = [
  { symbol: 'MSFT', name: 'Microsoft Corporation', exchange: 'NMS', quoteType: 'EQUITY' },
  { symbol: 'MS', name: 'Morgan Stanley', exchange: 'NYQ', quoteType: 'EQUITY' },
]

const mockFetch = vi.fn()

function mockApi(historyBody: unknown = CANDLES, historyOk = true) {
  mockFetch.mockImplementation(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.includes('/api/stocks/search')) {
      return { ok: true, json: async () => SEARCH_RESULTS } as Response
    }
    if (url.includes('/quote')) {
      return { ok: true, json: async () => QUOTE } as Response
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
    FakeEventSource.instances = []
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
    emit('snapshot', SNAPSHOTS)

    expect(await screen.findByText('Americas')).toBeInTheDocument()
    expect(await screen.findByText('EMEA')).toBeInTheDocument()
    expect(await screen.findByText('APAC')).toBeInTheDocument()

    // fixed column order: Americas → APAC → EMEA
    const [americas, apac, emea] = ['Americas', 'APAC', 'EMEA'].map((r) => screen.getByText(r))
    expect(americas.compareDocumentPosition(apac)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    expect(apac.compareDocumentPosition(emea)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
    expect(await screen.findByText('NVDA')).toBeInTheDocument()
    expect(screen.getByText('5.77T USD')).toBeInTheDocument()
    expect(screen.getByText('0700.HK')).toBeInTheDocument()
    expect(screen.getByText('HKD')).toBeInTheDocument() // currency chip on the ticker row
  })

  it('loads the chart when a top stock card is clicked', async () => {
    render(<App />)
    emit('snapshot', SNAPSHOTS)

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
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('fetches candles and renders the chart on success', async () => {
    render(<App />)
    await expandDetails()
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/stocks/AAPL/history?from='),
    )

    // Google-Finance-style header + stats grid
    expect(await screen.findByText(/Market Open/)).toBeInTheDocument()
    expect(screen.getByText(/332\.89/)).toBeInTheDocument()
    expect(screen.getByText('Mkt Cap')).toBeInTheDocument()
    expect(screen.getByText('Avg Vol')).toBeInTheDocument()
    for (const chip of ['1D', '5D', '1M', '1Y', '5Y', 'Max']) {
      expect(screen.getByRole('button', { name: chip })).toBeInTheDocument()
    }
  })

  it('ignores stale responses when the ticker changes quickly', async () => {
    let resolveNvda: (v: unknown) => void = () => {}
    mockFetch.mockImplementation((input: RequestInfo | URL) => {
      const url = String(input)
      if (url.includes('/api/stocks/NVDA/history')) {
        return new Promise((r) => { resolveNvda = r }) // slow request
      }
      return Promise.resolve({ ok: true, json: async () => CANDLES } as Response)
    })

    render(<App />)
    emit('snapshot', SNAPSHOTS)
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

    emit('snapshot', SNAPSHOTS)
    await userEvent.click(await screen.findByText('NVDA'))

    await waitFor(() => expect(toggle).toHaveAttribute('aria-expanded', 'true'))
    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
  })

  it('loads chart with preset range when 1M is clicked', async () => {
    render(<App />)
    await expandDetails()
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))
    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())

    // preset chips live inside the loaded stock panel
    await userEvent.click(screen.getByRole('button', { name: '1M' }))

    const expectedFrom = new Date()
    expectedFrom.setDate(expectedFrom.getDate() - 30)
    const from = expectedFrom.toISOString().slice(0, 10)

    await waitFor(() =>
      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining(`/api/stocks/AAPL/history?from=${from}&to=`),
      ),
    )
  })

  it('does not open the ticker dropdown when a market tile is clicked', async () => {
    render(<App />)
    emit('snapshot', SNAPSHOTS)

    await userEvent.click(await screen.findByText('NVDA'))
    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())

    // wait past the 250ms search debounce
    await act(() => new Promise((r) => setTimeout(r, 400)))

    expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    expect(
      mockFetch.mock.calls.filter(([u]) => String(u).includes('/api/stocks/search')),
    ).toHaveLength(0)
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

  it('applies only changed quotes from market-update events', async () => {
    render(<App />)
    emit('snapshot', SNAPSHOTS)

    expect(await screen.findByText('NVDA')).toBeInTheDocument()
    expect(screen.getByText('$238.90')).toBeInTheDocument()

    emit('market-update', {
      region: 'AMERICAS',
      lastSuccessAt: 1_700_000_100_000,
      stale: false,
      consecutiveFailures: 0,
      marketOpen: true,
      changed: [marketQuote('NVDA', 'NVIDIA Corporation', 300.0, 4.5, 6e12, 'USD')],
      order: ['NVDA', 'AAPL'],
    })

    // the changed row updates; the unchanged AAPL row is preserved
    expect(await screen.findByText('$300.00')).toBeInTheDocument()
    expect(screen.getByText('$332.89')).toBeInTheDocument()
  })

  it('manually refreshes a region on demand', async () => {
    render(<App />)
    emit('snapshot', SNAPSHOTS)
    await screen.findByText('NVDA')

    await userEvent.click(screen.getByRole('button', { name: /refresh apac/i }))

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/markets/APAC/refresh'),
      expect.objectContaining({ method: 'POST' }),
    )
  })

  it('disables the refresh button while the market is closed', async () => {
    render(<App />)
    emit('snapshot', {
      ...SNAPSHOTS,
      AMERICAS: { ...SNAPSHOTS.AMERICAS, marketOpen: false },
    })
    await screen.findByText('NVDA')

    expect(screen.getByRole('button', { name: /refresh americas/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /refresh apac/i })).toBeEnabled()
  })

  it('marks region data stale when the stream is interrupted', async () => {
    render(<App />)
    emit('snapshot', SNAPSHOTS)

    expect(await screen.findByText('NVDA')).toBeInTheDocument()
    expect(screen.queryByText('STALE')).not.toBeInTheDocument()

    act(() => latestSource().emitError())

    // outage: values retained but clearly marked stale
    expect(await screen.findAllByText('STALE')).not.toHaveLength(0)
    expect(screen.getByText('NVDA')).toBeInTheDocument()
  })

  it('shows an error when the network request rejects', async () => {
    render(<App />)
    await expandDetails()
    mockFetch.mockRejectedValueOnce(new Error('network down'))

    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('network down')).toBeInTheDocument()
  })
})
