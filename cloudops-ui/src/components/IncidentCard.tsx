import { useState } from 'react'
import type { IncidentResponse, SuggestionResponse } from '@/types/api'
import { analyzeIncident, decideSuggestion } from '@/api/client'
import {
  ConfidenceBar,
  RelativeTime,
  SeverityBadge,
  SuggestionBadge,
  TriggerBadge,
} from '@/components/Badges'
import clsx from 'clsx'

interface Props {
  incident: IncidentResponse
  onUpdated: () => void
}

export default function IncidentCard({ incident, onUpdated }: Props) {
  const [expanded, setExpanded] = useState(true)
  const [analyzing, setAnalyzing] = useState(false)
  const [analyzeError, setAnalyzeError] = useState<string | null>(null)

  const pendingSuggestion = incident.suggestions.find(s => s.status === 'PENDING')

  async function handleAnalyze() {
    setAnalyzing(true)
    setAnalyzeError(null)
    try {
      await analyzeIncident(incident.id)
      onUpdated()
    } catch (e: unknown) {
      setAnalyzeError(e instanceof Error ? e.message : 'Analysis failed')
    } finally {
      setAnalyzing(false)
    }
  }

  const statusBorder: Record<string, string> = {
    DRAFT:        'border-l-4 border-l-blue-400',
    ACKNOWLEDGED: 'border-l-4 border-l-yellow-400',
    RESOLVED:     'border-l-4 border-l-green-400',
  }

  return (
    <div className={clsx(
      'rounded-lg border border-border bg-white shadow-sm overflow-hidden',
      statusBorder[incident.status]
    )}>
      {/* ── Header ─────────────────────────────────────────────────────── */}
      <button
        className="w-full text-left px-4 py-3 flex items-start justify-between gap-3 hover:bg-surface transition-colors"
        onClick={() => setExpanded(e => !e)}
      >
        <div className="flex flex-col gap-1 min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <SeverityBadge severity={incident.severity} />
            <TriggerBadge autoDetected={incident.autoDetected} />
            <span className="text-xs text-gray-400">{incident.status}</span>
          </div>
          <span className="font-medium text-sm leading-snug">{incident.title}</span>
          <div className="flex items-center gap-3 text-xs text-gray-400">
            <span>{incident.serviceName}</span>
            <RelativeTime iso={incident.createdAt} />
          </div>
        </div>
        <span className="text-gray-400 text-xs mt-1 shrink-0">{expanded ? '▲' : '▼'}</span>
      </button>

      {/* ── Body ───────────────────────────────────────────────────────── */}
      {expanded && (
        <div className="px-4 pb-4 space-y-4 border-t border-border pt-3">

          {/* Summary */}
          {incident.summary && (
            <p className="text-sm text-gray-600 leading-relaxed whitespace-pre-line">
              {incident.summary}
            </p>
          )}

          {/* AI hypothesis block */}
          {pendingSuggestion ? (
            <AiHypothesisBlock
              suggestion={pendingSuggestion}
              onDecided={onUpdated}
            />
          ) : (
            <div className="flex items-center gap-3">
              {incident.suggestions.length === 0 && (
                <button
                  onClick={handleAnalyze}
                  disabled={analyzing}
                  className="rounded bg-accent px-3 py-1.5 text-xs text-white hover:bg-blue-600 disabled:opacity-50 transition-colors"
                >
                  {analyzing ? 'Analyzing…' : 'Run AI Analysis'}
                </button>
              )}
              {incident.suggestions.length > 0 && (
                <PastSuggestions suggestions={incident.suggestions} />
              )}
              {analyzeError && (
                <span className="text-xs text-red-600">{analyzeError}</span>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// ── AI hypothesis + accept/reject ────────────────────────────────────────────

function AiHypothesisBlock({
  suggestion,
  onDecided,
}: {
  suggestion: SuggestionResponse
  onDecided: () => void
}) {
  const [note, setNote] = useState('')
  const [submitting, setSubmitting] = useState<'ACCEPTED' | 'REJECTED' | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function decide(action: 'ACCEPTED' | 'REJECTED') {
    setSubmitting(action)
    setError(null)
    try {
      await decideSuggestion(suggestion.id, { action, onCallNote: note || undefined })
      onDecided()
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Failed to submit decision')
    } finally {
      setSubmitting(null)
    }
  }

  return (
    <div className="rounded-lg bg-surface border border-border p-3 space-y-3">
      {/* Header */}
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
            AI Hypothesis
          </span>
          <SuggestionBadge status={suggestion.status} />
          <span className="text-xs text-gray-400">
            trigger: {suggestion.triggerReason}
          </span>
        </div>
        <ConfidenceBar value={suggestion.confidence} />
      </div>

      {/* Root cause */}
      <div>
        <p className="text-xs font-semibold text-gray-400 mb-0.5">Root cause hypothesis</p>
        <p className="text-sm text-gray-700">{suggestion.rootCauseHypothesis}</p>
      </div>

      {/* Recommended action */}
      <div className="rounded bg-blue-50 border border-blue-100 px-3 py-2">
        <p className="text-xs font-semibold text-blue-500 mb-0.5">Recommended action</p>
        <p className="text-sm text-blue-900">{suggestion.recommendedAction}</p>
      </div>

      {/* Reasoning (collapsible) */}
      <details className="group">
        <summary className="cursor-pointer text-xs text-gray-400 hover:text-gray-600 select-none">
          Show reasoning ▸
        </summary>
        <p className="mt-1 text-xs text-gray-500 leading-relaxed">{suggestion.reasoning}</p>
      </details>

      {/* On-call note */}
      <textarea
        value={note}
        onChange={e => setNote(e.target.value)}
        placeholder="Optional note for audit log…"
        rows={2}
        className="w-full rounded border border-border bg-white px-2.5 py-1.5 text-sm text-gray-700 placeholder-gray-300 resize-none focus:outline-none focus:ring-1 focus:ring-accent"
      />

      {/* Accept / Reject */}
      <div className="flex items-center gap-2">
        <button
          onClick={() => decide('ACCEPTED')}
          disabled={submitting !== null}
          className="rounded bg-green-600 px-4 py-1.5 text-xs font-semibold text-white hover:bg-green-700 disabled:opacity-50 transition-colors"
        >
          {submitting === 'ACCEPTED' ? 'Accepting…' : '✓ Accept'}
        </button>
        <button
          onClick={() => decide('REJECTED')}
          disabled={submitting !== null}
          className="rounded bg-white border border-border px-4 py-1.5 text-xs font-semibold text-gray-600 hover:bg-red-50 hover:border-red-200 hover:text-red-700 disabled:opacity-50 transition-colors"
        >
          {submitting === 'REJECTED' ? 'Rejecting…' : '✕ Reject'}
        </button>
        {error && <span className="text-xs text-red-600">{error}</span>}
      </div>
    </div>
  )
}

// ── Past (decided) suggestions ───────────────────────────────────────────────

function PastSuggestions({ suggestions }: { suggestions: SuggestionResponse[] }) {
  return (
    <div className="space-y-1 w-full">
      {suggestions.map(s => (
        <div key={s.id} className="flex items-center gap-2 text-xs text-gray-500">
          <SuggestionBadge status={s.status} />
          <span className="truncate">{s.recommendedAction}</span>
          {s.decidedAt && <RelativeTime iso={s.decidedAt} />}
        </div>
      ))}
    </div>
  )
}
