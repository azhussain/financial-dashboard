// In dev the Vite proxy forwards /api to localhost:8080, so an empty base is
// fine. In production set VITE_API_BASE to the backend origin, e.g.
// VITE_API_BASE=https://api.example.com
export const API_BASE: string = import.meta.env.VITE_API_BASE ?? ''
