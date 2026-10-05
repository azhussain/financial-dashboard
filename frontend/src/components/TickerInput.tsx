import { useEffect, useRef, useState } from 'react'

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

  // Debounced lookup as the user types.
  useEffect(() => {
    if (justSelected.current) {
      // Skip the search triggered by programmatically setting the selected symbol.
      justSelected.current = false
      return
    }
    const query = value.trim()
    if (!query) {
      setSuggestions([])
      setOpen(false)
      return
    }
    const timer = setTimeout(async () => {
      try {
        const res = await fetch(`/api/stocks/search?q=${encodeURIComponent(query)}`)
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
    <label className="relative flex flex-col gap-1.5">
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
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        onFocus={() => suggestions.length > 0 && setOpen(true)}
        onBlur={() => setOpen(false)}
        placeholder="AAPL"
        autoComplete="off"
        className="w-48 rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm uppercase placeholder:text-gray-500 focus:border-emerald-500 focus:outline-none"
      />
      {open && (
        <ul
          id="ticker-suggestions"
          role="listbox"
          className="absolute top-full z-20 mt-1 max-h-64 w-80 overflow-y-auto rounded-lg border border-gray-700 bg-gray-800 py-1 shadow-xl"
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
