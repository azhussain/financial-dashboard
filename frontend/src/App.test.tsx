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

const mockFetch = vi.fn()
vi.stubGlobal('fetch', mockFetch)

describe('App', () => {
  beforeEach(() => {
    mockFetch.mockReset()
  })

  it('renders ticker input, date pickers and load button', () => {
    render(<App />)

    expect(screen.getByPlaceholderText('AAPL')).toBeInTheDocument()
    expect(screen.getByLabelText(/start date/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/end date/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /load chart/i })).toBeInTheDocument()
  })

  it('shows an error when ticker is empty', async () => {
    render(<App />)

    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('Please enter a ticker symbol')).toBeInTheDocument()
    expect(mockFetch).not.toHaveBeenCalled()
  })

  it('fetches candles and renders the chart on success', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => [
        { date: '2025-09-02', open: 229.25, high: 230.85, low: 226.97, close: 229.72, volume: 44075600 },
      ],
    })

    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    await waitFor(() => expect(screen.getByTestId('candlestick-chart')).toBeInTheDocument())
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/stocks/AAPL/history?from='),
    )
  })

  it('shows the API error message on failure', async () => {
    mockFetch.mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ error: 'Unknown ticker symbol: FAKE' }),
    })

    render(<App />)
    const input = screen.getByPlaceholderText('AAPL')
    await userEvent.clear(input)
    await userEvent.type(input, 'FAKE')
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('Unknown ticker symbol: FAKE')).toBeInTheDocument()
  })

  it('shows an error when the network request rejects', async () => {
    mockFetch.mockRejectedValueOnce(new Error('network down'))

    render(<App />)
    await userEvent.click(screen.getByRole('button', { name: /load chart/i }))

    expect(await screen.findByText('network down')).toBeInTheDocument()
  })
})
