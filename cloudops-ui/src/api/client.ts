import axios from 'axios'
import type {
  IncidentResponse,
  IncidentStatus,
  RegisterServiceRequest,
  ServiceResponse,
  SignalResponse,
  SuggestionResponse,
  UpdateStatusRequest,
  UpdateSuggestionRequest,
} from '@/types/api'

const http = axios.create({
  baseURL: '/',
  headers: { 'Content-Type': 'application/json' },
})

// ── Services ────────────────────────────────────────────────────────────────

export const registerService = (body: RegisterServiceRequest) =>
  http.post<ServiceResponse>('/services', body).then(r => r.data)

export const fetchServices = () =>
  http.get<ServiceResponse[]>('/services').then(r => r.data)

export const updateServiceStatus = (id: string, body: UpdateStatusRequest) =>
  http.patch<ServiceResponse>(`/services/${id}/status`, body).then(r => r.data)

export const fetchRecentSignals = (serviceId: string) =>
  http.get<SignalResponse[]>(`/services/${serviceId}/signals`).then(r => r.data)

// ── Incidents ────────────────────────────────────────────────────────────────

export const fetchIncidents = (status?: IncidentStatus) =>
  http
    .get<IncidentResponse[]>('/incidents', { params: status ? { status } : {} })
    .then(r => r.data)

export const analyzeIncident = (id: string) =>
  http.post<SuggestionResponse>(`/incidents/${id}/analyze`).then(r => r.data)

// ── Suggestions ──────────────────────────────────────────────────────────────

export const decideSuggestion = (id: string, body: UpdateSuggestionRequest) =>
  http.patch<SuggestionResponse>(`/suggestions/${id}`, body).then(r => r.data)
