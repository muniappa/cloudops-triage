import type { IncidentSeverity, ServiceHealthStatus, SuggestionStatus } from '@/types/api'
import clsx from 'clsx'

// ── Health status badge ──────────────────────────────────────────────────────

const healthColors: Record<ServiceHealthStatus, string> = {
  HEALTHY:   'bg-green-100 text-green-800 border-green-200',
  DEGRADED:  'bg-red-100   text-red-800   border-red-200',
  RECOVERED: 'bg-yellow-100 text-yellow-800 border-yellow-200',
}

export function HealthBadge({ status }: { status: ServiceHealthStatus }) {
  return (
    <span className={clsx(
      'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
      healthColors[status]
    )}>
      {status}
    </span>
  )
}

// ── Severity badge ───────────────────────────────────────────────────────────

const severityColors: Record<IncidentSeverity, string> = {
  LOW:      'bg-blue-50   text-blue-700   border-blue-200',
  MEDIUM:   'bg-yellow-50 text-yellow-700 border-yellow-200',
  HIGH:     'bg-orange-50 text-orange-700 border-orange-200',
  CRITICAL: 'bg-red-50    text-red-700    border-red-200',
}

export function SeverityBadge({ severity }: { severity: IncidentSeverity }) {
  return (
    <span className={clsx(
      'inline-flex items-center rounded border px-2 py-0.5 text-xs font-semibold uppercase tracking-wide',
      severityColors[severity]
    )}>
      {severity}
    </span>
  )
}

// ── Auto-detected vs manual trigger badge ───────────────────────────────────

export function TriggerBadge({ autoDetected }: { autoDetected: boolean }) {
  return autoDetected ? (
    <span className="inline-flex items-center gap-1 rounded bg-purple-50 border border-purple-200 px-2 py-0.5 text-xs text-purple-700 font-medium">
      <span>⚡</span> Auto-detected
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 rounded bg-gray-100 border border-gray-200 px-2 py-0.5 text-xs text-gray-600 font-medium">
      Manual
    </span>
  )
}

// ── Suggestion status badge ──────────────────────────────────────────────────

const suggColors: Record<SuggestionStatus, string> = {
  PENDING:  'bg-blue-50   text-blue-700   border-blue-200',
  ACCEPTED: 'bg-green-50  text-green-700  border-green-200',
  REJECTED: 'bg-gray-100  text-gray-500   border-gray-200',
}

export function SuggestionBadge({ status }: { status: SuggestionStatus }) {
  return (
    <span className={clsx(
      'inline-flex items-center rounded border px-2 py-0.5 text-xs font-medium',
      suggColors[status]
    )}>
      {status}
    </span>
  )
}

// ── Confidence bar ───────────────────────────────────────────────────────────

export function ConfidenceBar({ value }: { value: number }) {
  const pct = Math.round(value * 100)
  const color = pct >= 80 ? 'bg-green-500' : pct >= 50 ? 'bg-yellow-500' : 'bg-red-400'
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-24 rounded-full bg-gray-200 overflow-hidden">
        <div className={clsx('h-full rounded-full', color)} style={{ width: `${pct}%` }} />
      </div>
      <span className="text-xs text-gray-500">{pct}%</span>
    </div>
  )
}

// ── Skeleton loader ──────────────────────────────────────────────────────────

export function Skeleton({ lines = 3 }: { lines?: number }) {
  return (
    <div className="animate-pulse space-y-2">
      {Array.from({ length: lines }).map((_, i) => (
        <div key={i} className="h-4 rounded bg-gray-200" style={{ width: `${60 + (i % 3) * 15}%` }} />
      ))}
    </div>
  )
}

// ── Error notice ─────────────────────────────────────────────────────────────

export function ErrorNotice({ message }: { message: string }) {
  return (
    <div className="rounded border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      ⚠ {message}
    </div>
  )
}

// ── Timestamp helper ─────────────────────────────────────────────────────────

export function RelativeTime({ iso }: { iso: string }) {
  const date = new Date(iso)
  const now = Date.now()
  const diff = Math.floor((now - date.getTime()) / 1000)
  let label: string
  if (diff < 60)        label = `${diff}s ago`
  else if (diff < 3600) label = `${Math.floor(diff / 60)}m ago`
  else if (diff < 86400)label = `${Math.floor(diff / 3600)}h ago`
  else                  label = date.toLocaleDateString()
  return <span className="text-xs text-gray-400" title={date.toISOString()}>{label}</span>
}
