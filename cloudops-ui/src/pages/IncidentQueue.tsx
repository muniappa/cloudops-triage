import { useCallback, useState } from 'react'
import type { IncidentStatus } from '@/types/api'
import { fetchIncidents } from '@/api/client'
import { usePolling } from '@/hooks/usePolling'
import { ErrorNotice, Skeleton } from '@/components/Badges'
import IncidentCard from '@/components/IncidentCard'

const FILTERS: { label: string; value: IncidentStatus | 'ALL' }[] = [
  { label: 'All open',     value: 'ALL' },
  { label: 'Draft',        value: 'DRAFT' },
  { label: 'Acknowledged', value: 'ACKNOWLEDGED' },
  { label: 'Resolved',     value: 'RESOLVED' },
]

export default function IncidentQueue() {
  const [filter, setFilter] = useState<IncidentStatus | 'ALL'>('ALL')

  const fetcher = useCallback(
    () => fetchIncidents(filter === 'ALL' ? undefined : filter),
    [filter]
  )
  const { data: incidents, loading, error, refresh } = usePolling(fetcher, 10_000)

  const openCount = incidents?.filter(
    i => i.status === 'DRAFT' || i.status === 'ACKNOWLEDGED'
  ).length ?? 0

  return (
    <div className="space-y-4">
      {/* ── Header ───────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h2 className="text-base font-semibold">Incident Queue</h2>
          {openCount > 0 && (
            <span className="rounded-full bg-red-100 text-red-700 border border-red-200 px-2 py-0.5 text-xs font-bold">
              {openCount} open
            </span>
          )}
        </div>
        <button
          onClick={refresh}
          className="text-xs text-gray-400 hover:text-accent transition-colors"
        >
          ↻ Refresh
        </button>
      </div>

      {/* ── Filter tabs ───────────────────────────────────────────────── */}
      <div className="flex gap-1 border-b border-border">
        {FILTERS.map(f => (
          <button
            key={f.value}
            onClick={() => setFilter(f.value)}
            className={`px-3 py-1.5 text-xs font-medium border-b-2 -mb-px transition-colors ${
              filter === f.value
                ? 'border-accent text-accent'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* ── Content ──────────────────────────────────────────────────── */}
      {loading ? (
        <Skeleton lines={5} />
      ) : error ? (
        <ErrorNotice message={`Could not load incidents: ${error}`} />
      ) : !incidents?.length ? (
        <p className="py-8 text-center text-sm text-gray-400">
          {filter === 'ALL' ? 'No incidents.' : `No ${filter.toLowerCase()} incidents.`}
        </p>
      ) : (
        <div className="space-y-3">
          {incidents.map(incident => (
            <IncidentCard
              key={incident.id}
              incident={incident}
              onUpdated={refresh}
            />
          ))}
        </div>
      )}
    </div>
  )
}
