import { useCallback } from 'react'
import type { ServiceResponse, SignalResponse } from '@/types/api'
import { fetchRecentSignals, fetchServices } from '@/api/client'
import { usePolling } from '@/hooks/usePolling'
import { ErrorNotice, HealthBadge, RelativeTime, Skeleton } from '@/components/Badges'

interface Props {
  onSelectService: (id: string) => void
  selectedServiceId: string | null
}

export default function ServiceHealthList({ onSelectService, selectedServiceId }: Props) {
  const fetcher = useCallback(() => fetchServices(), [])
  const { data: services, loading, error } = usePolling(fetcher, 10_000)

  if (loading) return <Skeleton lines={6} />
  if (error)   return <ErrorNotice message={`Failed to load services: ${error}`} />
  if (!services?.length) return (
    <p className="text-sm text-gray-400 px-2">No services registered.</p>
  )

  const degraded  = services.filter(s => s.healthStatus === 'DEGRADED')
  const recovered = services.filter(s => s.healthStatus === 'RECOVERED')
  const healthy   = services.filter(s => s.healthStatus === 'HEALTHY')
  const ordered   = [...degraded, ...recovered, ...healthy]

  return (
    <div className="space-y-1">
      {ordered.map(svc => (
        <ServiceRow
          key={svc.id}
          service={svc}
          selected={svc.id === selectedServiceId}
          onClick={() => onSelectService(svc.id)}
        />
      ))}
    </div>
  )
}

function ServiceRow({
  service,
  selected,
  onClick,
}: {
  service: ServiceResponse
  selected: boolean
  onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      className={`w-full text-left rounded-lg px-3 py-2.5 flex items-center justify-between gap-2 border transition-colors
        ${selected
          ? 'bg-white border-accent shadow-sm'
          : 'bg-transparent border-transparent hover:bg-white hover:border-border'}`}
    >
      <div className="flex flex-col min-w-0">
        <span className="font-medium text-sm truncate">{service.name}</span>
        <span className="text-xs text-gray-400 truncate">{service.teamOwner}</span>
      </div>
      <HealthBadge status={service.healthStatus} />
    </button>
  )
}

// ── Signal table for selected service ───────────────────────────────────────

export function SignalTable({ serviceId }: { serviceId: string }) {
  const fetcher = useCallback(() => fetchRecentSignals(serviceId), [serviceId])
  const { data: signals, loading, error } = usePolling(fetcher, 10_000)

  if (loading) return <Skeleton lines={4} />
  if (error)   return <ErrorNotice message={`Signals unavailable: ${error}`} />
  if (!signals?.length) return <p className="text-sm text-gray-400">No signals yet.</p>

  const metricLabel: Record<string, string> = {
    ERROR_RATE:  'Error rate (%)',
    LATENCY_P99: 'Latency p99 (ms)',
    CPU:         'CPU (%)',
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs text-gray-400">
            <th className="pb-2 pr-4 font-medium">Metric</th>
            <th className="pb-2 pr-4 font-medium">Value</th>
            <th className="pb-2 pr-4 font-medium">Breached</th>
            <th className="pb-2 font-medium">Recorded</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {signals.map(sig => (
            <SignalRow key={sig.id} signal={sig} metricLabel={metricLabel} />
          ))}
        </tbody>
      </table>
    </div>
  )
}

function SignalRow({
  signal,
  metricLabel,
}: {
  signal: SignalResponse
  metricLabel: Record<string, string>
}) {
  return (
    <tr className="hover:bg-surface">
      <td className="py-1.5 pr-4 text-gray-600">{metricLabel[signal.metricType] ?? signal.metricType}</td>
      <td className="py-1.5 pr-4 font-mono">{signal.value.toFixed(2)}</td>
      <td className="py-1.5 pr-4">
        {signal.thresholdBreached ? (
          <span className="text-red-600 font-medium text-xs">● BREACHED</span>
        ) : (
          <span className="text-green-600 text-xs">✓ OK</span>
        )}
      </td>
      <td className="py-1.5"><RelativeTime iso={signal.recordedAt} /></td>
    </tr>
  )
}
