-- ============================================================
-- V2__expand_metric_types.sql
-- Widens the metric_type CHECK constraint to include
-- CPU_USAGE, MEMORY_USAGE, and REQUEST_RATE.
-- ============================================================

ALTER TABLE health_signals
    DROP CONSTRAINT IF EXISTS health_signals_metric_type_check;

ALTER TABLE health_signals
    ADD CONSTRAINT health_signals_metric_type_check
        CHECK (metric_type IN ('ERROR_RATE','LATENCY_P99','CPU_USAGE','MEMORY_USAGE','REQUEST_RATE'));
