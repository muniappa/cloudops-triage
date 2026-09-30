import { useCallback, useEffect, useRef, useState } from 'react'

interface PollingState<T> {
  data: T | null
  loading: boolean
  error: string | null
  refresh: () => void
}

/**
 * Polls `fetcher` every `intervalMs` milliseconds.
 * - Shows loading=true only on the very first fetch (skeleton state).
 * - Subsequent poll refreshes update data silently.
 * - On error, preserves the last successful data so the UI stays usable.
 * - Cleans up the interval on unmount.
 */
export function usePolling<T>(
  fetcher: () => Promise<T>,
  intervalMs = 10_000,
): PollingState<T> {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetch = useCallback(async () => {
    try {
      const result = await fetcher()
      setData(result)
      setError(null)
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : 'Unknown error'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }, [fetcher])

  useEffect(() => {
    fetch()
    timerRef.current = setInterval(fetch, intervalMs)
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [fetch, intervalMs])

  return { data, loading, error, refresh: fetch }
}
