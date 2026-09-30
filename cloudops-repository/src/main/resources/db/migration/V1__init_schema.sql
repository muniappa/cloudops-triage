-- ============================================================
-- V1__init_schema.sql
-- CloudOps Triage Platform — initial schema
-- ============================================================

-- Monitored services ─────────────────────────────────────────
CREATE TABLE monitored_services (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   VARCHAR(255) NOT NULL UNIQUE,
    team_owner             VARCHAR(255) NOT NULL,
    description            TEXT,
    health_status          VARCHAR(20)  NOT NULL DEFAULT 'HEALTHY'
                               CHECK (health_status IN ('HEALTHY','DEGRADED','RECOVERED')),
    last_status_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    registered_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Health signals ─────────────────────────────────────────────
CREATE TABLE health_signals (
    id                UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id        UUID             NOT NULL REFERENCES monitored_services(id) ON DELETE CASCADE,
    metric_type       VARCHAR(20)       NOT NULL
                          CHECK (metric_type IN ('ERROR_RATE','LATENCY_P99','CPU')),
    value             DOUBLE PRECISION NOT NULL,
    threshold_breached BOOLEAN         NOT NULL DEFAULT FALSE,
    recorded_at       TIMESTAMPTZ      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_signal_service_recorded ON health_signals (service_id, recorded_at DESC);
CREATE INDEX idx_signal_metric_type      ON health_signals (metric_type);

-- Incidents ──────────────────────────────────────────────────
CREATE TABLE incidents (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    service_id       UUID        NOT NULL REFERENCES monitored_services(id) ON DELETE CASCADE,
    title            VARCHAR(500) NOT NULL,
    summary          TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                         CHECK (status IN ('DRAFT','ACKNOWLEDGED','RESOLVED')),
    severity         VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM'
                         CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    auto_detected    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    acknowledged_at  TIMESTAMPTZ,
    resolved_at      TIMESTAMPTZ
);

CREATE INDEX idx_incident_service_status ON incidents (service_id, status);
CREATE INDEX idx_incident_created_at     ON incidents (created_at DESC);

-- Remediation suggestions ────────────────────────────────────
CREATE TABLE remediation_suggestions (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_id           UUID        NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    recommended_action    TEXT        NOT NULL,
    root_cause_hypothesis TEXT        NOT NULL,
    confidence            DOUBLE PRECISION NOT NULL CHECK (confidence BETWEEN 0.0 AND 1.0),
    reasoning             TEXT,
    status                VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    trigger_reason        VARCHAR(20)  NOT NULL
                              CHECK (trigger_reason IN ('AUTO_DEGRADED','MANUAL','SIGNAL_SPIKE')),
    on_call_note          TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    decided_at            TIMESTAMPTZ
);

CREATE INDEX idx_suggestion_incident ON remediation_suggestions (incident_id);
CREATE INDEX idx_suggestion_status   ON remediation_suggestions (status);
