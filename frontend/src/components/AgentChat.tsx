import { useEffect, useRef, useState } from 'react'
import CandleChart, { type Candle } from './CandleChart'
import { API_BASE } from '../api'

interface ChartPayload {
  symbol: string
  candles: Candle[]
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'error'
  text: string
  charts?: ChartPayload[]
}

export default function AgentChat() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const scrollRef = useRef<HTMLDivElement>(null)
  // Stable per-tab session id so the backend keeps this conversation's
  // memory isolated from other tabs/users.
  const sessionId = useRef(
    sessionStorage.getItem('agentSessionId') ??
      (() => {
        const id = crypto.randomUUID()
        sessionStorage.setItem('agentSessionId', id)
        return id
      })(),
  )

  useEffect(() => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }, [messages, loading])

  async function send() {
    const text = input.trim()
    if (!text || loading) return
    setInput('')
    setLoading(true)
    setMessages((m) => [...m, { role: 'user', text }])
    try {
      const res = await fetch(`${API_BASE}/api/agent/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: text, sessionId: sessionId.current }),
      })
      const body = await res.json().catch(() => null)
      if (!res.ok) {
        setMessages((m) => [
          ...m,
          { role: 'error', text: body?.error ?? `Request failed (${res.status})` },
        ])
      } else {
        setMessages((m) => [
          ...m,
          { role: 'assistant', text: body.message, charts: body.charts },
        ])
      }
    } catch (e) {
      setMessages((m) => [
        ...m,
        { role: 'error', text: e instanceof Error ? e.message : 'Request failed' },
      ])
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col items-end gap-3 sm:bottom-6 sm:right-6">
      {open && (
        // Full-screen sheet on phones (keyboard-safe); floating card on sm+.
        <div className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-gray-900 sm:static sm:z-auto sm:h-[560px] sm:w-[min(400px,calc(100vw-3rem))] sm:rounded-xl sm:shadow-2xl sm:ring-1 sm:ring-gray-700">
          <div className="flex items-start justify-between border-b border-gray-800 px-4 py-3">
            <div>
              <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-400">
                Virtual Agent
              </h2>
              <p className="mt-0.5 text-xs text-gray-500">
                e.g. "Compare NVDA and AMD over the last 3 months"
              </p>
            </div>
            <button
              onClick={() => setOpen(false)}
              aria-label="Close assistant"
              className="rounded-md p-1 text-gray-400 transition hover:bg-gray-800 hover:text-gray-200"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div ref={scrollRef} className="min-h-24 flex-1 space-y-3 overflow-y-auto p-4">
            {messages.length === 0 && (
              <div className="rounded-lg border border-dashed border-gray-700 px-3 py-4 text-center text-xs text-gray-500">
                Ask about any stock — I'll fetch the data and chart it here.
              </div>
            )}
            {messages.map((m, i) => (
              <div
                key={i}
                className={
                  m.role === 'user'
                    ? 'ml-auto w-fit max-w-[85%] rounded-lg bg-emerald-900/50 px-3 py-2 text-sm'
                    : m.role === 'error'
                      ? 'rounded-lg border border-red-800 bg-red-950/50 px-3 py-2 text-sm text-red-300'
                      : 'w-fit max-w-[95%] whitespace-pre-wrap rounded-lg bg-gray-800 px-3 py-2 text-sm'
                }
              >
                {m.text}
                {m.charts?.map((c) => (
                  <div key={c.symbol} className="mt-3 w-full min-w-72">
                    <CandleChart candles={c.candles} symbol={c.symbol} height={260} />
                  </div>
                ))}
              </div>
            ))}
            {loading && (
              <div className="w-fit rounded-lg bg-gray-800 px-3 py-2 text-sm text-gray-400">
                Thinking…
              </div>
            )}
          </div>

          <div className="flex gap-2 border-t border-gray-800 p-3">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send()}
              placeholder="Ask about a stock…"
              className="flex-1 rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm placeholder:text-gray-500 focus:border-emerald-500 focus:outline-none"
            />
            <button
              onClick={send}
              disabled={loading || !input.trim()}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-semibold transition hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Send
            </button>
          </div>
        </div>
      )}

      <button
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="flex items-center gap-2 rounded-full bg-emerald-600 px-4 py-3 text-sm font-semibold text-white shadow-lg transition hover:bg-emerald-500"
      >
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
          <path strokeLinecap="round" d="M12 8V6" />
          <circle cx="12" cy="4" r="1.6" />
          <rect x="4" y="8" width="16" height="11" rx="3" />
          <circle cx="9" cy="13" r="1.2" fill="currentColor" stroke="none" />
          <circle cx="15" cy="13" r="1.2" fill="currentColor" stroke="none" />
          <path strokeLinecap="round" d="M9.5 16.5h5" />
        </svg>
        Virtual Agent
      </button>
    </div>
  )
}
