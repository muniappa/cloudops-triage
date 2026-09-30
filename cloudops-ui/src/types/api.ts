// ── Enums (mirror backend exactly) ─────────────────────────────────────────

export type ServiceHealthStatus = 'HEALTHY' | 'DEGRADED' | 'RECOVERED'
export type MetricType = 'ERROR_RATE' | 'LATENCY_P99' | 'CPU_USAGE' | 'MEMORY_USAGE' | 'REQUEST_RATE'
export type IncidentStatus = 'DRAFT' | 'ACKNOWLEDGED' | 'RESOLVED'
export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'
export type TriggerReason = 'AUTO_DEGRADED' | 'MANUAL' | 'SIGNAL_SPIKE'

// ── API response types ──────────────────────────────────────────────────────

export interface ServiceResponse {
  id: string
  name: string
  teamOwner: string
  description: string | null
  healthStatus: ServiceHealthStatus
  lastStatusChangedAt: string
  registeredAt: string
}

export interface SignalResponse {
  id: string
  serviceId: string
  metricType: MetricType
  value: number
  thresholdBreached: boolean
  recordedAt: string
}

export interface SuggestionResponse {
  id: string
  incidentId: string
  recommendedAction: string
  rootCauseHypothesis: string
  confidence: number
  reasoning: string
  status: SuggestionStatus
  triggerReason: TriggerReason
  onCallNote: string | null
  createdAt: string
  decidedAt: string | null
}

export interface IncidentResponse {
  id: string
  serviceId: string
  serviceName: string
  title: string
  summary: string | null
  status: IncidentStatus
  severity: IncidentSeverity
  autoDetected: boolean
  createdAt: string
  acknowledgedAt: string | null
  resolvedAt: string | null
  suggestions: SuggestionResponse[]
}

// ── API request types ───────────────────────────────────────────────────────

export interface RegisterServiceRequest {
  name: string
  teamOwner: string
  description?: string
}

export interface UpdateStatusRequest {
  status: ServiceHealthStatus
}

export interface UpdateSuggestionRequest {
  action: 'ACCEPTED' | 'REJECTED'
  onCallNote?: string
}
