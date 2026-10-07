import { useEffect, useRef, useState } from 'react'
import { API_BASE } from '../api'

interface Suggestion {
  symbol: string
  name: string
  exchange: string
}

interface Props {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
}

const EXCHANGE_LABELS: Record<string, string> = {
  NMS: 'NASDAQ',
  NGM: 'NASDAQ',
  NCM: 'NASDAQ',
  NYQ: 'NYSE',
  ASE: 'NYSE American',
  PCX: 'NYSE Arca',
  BTS: 'CBOE BZX',
}

export default function TickerInput({ value, onChange, onSubmit }: Props) {
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [open, setOpen] = useState(false)
  const [highlighted, setHighlighted] = useState(-1)
  const justSelected = useRef(false)
  const typed = useRef(false)

  // Debounced lookup as the user types. Programmatic value changes — e.g.
  // selecting a market tile — must not trigger a search or open the dropdown.
  useEffect(() => {
    if (justSelected.current) {
      justSelected.current = false
      return
    }
    if (!typed.current) {
      setOpen(false)
      setSuggestions([])
      return
    }
    typed.current = false
    const query = value.trim()
    if (!query) {
      setSuggestions([])
      setOpen(false)
      return
    }
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`${API_BASE}/api/stocks/search?q=${encodeURIComponent(query)}`)
        if (!res.ok) return
        const data: Suggestion[] = await res.json()
        setSuggestions(data)
        setHighlighted(-1)
        setOpen(data.length > 0)
      } catch {
        // suggestions are best-effort; keep the manual input usable
      }
    }, 250)
    return () => clearTimeout(timer)
  }, [value])

  function select(s: Suggestion) {
    justSelected.current = true
    onChange(s.symbol)
    setOpen(false)
    setSuggestions([])
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'ArrowDown' && suggestions.length > 0) {
      e.preventDefault()
      setOpen(true)
      setHighlighted((h) => (h + 1) % suggestions.length)
    } else if (e.key === 'ArrowUp' && suggestions.length > 0) {
      e.preventDefault()
      setHighlighted((h) => (h <= 0 ? suggestions.length - 1 : h - 1))
    } else if (e.key === 'Enter') {
      if (open && highlighted >= 0) {
        select(suggestions[highlighted])
      } else {
        setOpen(false)
        onSubmit()
      }
    } else if (e.key === 'Escape') {
      setOpen(false)
    }
  }

  return (
    <label className="relative flex w-full flex-col gap-1.5 sm:w-auto">
      <span className="text-xs font-medium uppercase tracking-wide text-gray-400">
        Ticker
      </span>
      <input
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        aria-controls="ticker-suggestions"
        value={value}
        onChange={(e) => {
          typed.current = true
          onChange(e.target.value)
        }}
        onKeyDown={onKeyDown}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        onBlur={() => setOpen(false)}
        placeholder="AAPL"
        autoComplete="off"
        className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm uppercase placeholder:text-gray-500 focus:border-emerald-500 focus:outline-none sm:w-48"
      />
      {open && (
        <ul
          id="ticker-suggestions"
          role="listbox"
          className="absolute top-full z-20 mt-1 max-h-64 w-full overflow-y-auto rounded-lg border border-gray-700 bg-gray-800 py-1 shadow-xl sm:w-80"
        >
          {suggestions.map((s, i) => (
            <li
              key={s.symbol}
              role="option"
              aria-selected={i === highlighted}
              onMouseDown={(e) => {
                e.preventDefault()
                select(s)
              }}
              onMouseEnter={() => setHighlighted(i)}
              className={`flex cursor-pointer items-center justify-between gap-3 px-3 py-2 text-sm ${
                i === highlighted ? 'bg-emerald-900/40' : ''
              }`}
            >
              <span className="font-semibold">{s.symbol}</span>
              <span className="flex-1 truncate text-gray-400">{s.name}</span>
              <span className="text-xs text-gray-500">
                {EXCHANGE_LABELS[s.exchange] ?? s.exchange}
              </span>
            </li>
          ))}
        </ul>
      )}
    </label>
  )
}
