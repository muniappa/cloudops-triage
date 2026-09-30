#!/usr/bin/env bash
# Seed script — registers 40 microservices and injects health signals
# to trigger realistic DEGRADED/HEALTHY states for demo purposes.
set -euo pipefail

BASE="http://localhost:8080"

echo "==> Registering 40 microservices..."

# Format: "name|teamOwner|description"
SERVICES=(
  "api-gateway|platform-team|Edge router handling all inbound traffic"
  "auth-service|identity-team|Authentication and authorization service"
  "user-service|identity-team|User profile and account management"
  "payment-service|billing-team|Payment processing and transactions"
  "order-service|commerce-team|Order lifecycle management"
  "inventory-service|commerce-team|Real-time inventory tracking"
  "shipping-service|logistics-team|Shipping and delivery coordination"
  "notification-service|messaging-team|Multi-channel push notifications"
  "email-service|messaging-team|Transactional email delivery"
  "sms-service|messaging-team|SMS and OTP delivery"
  "search-service|discovery-team|Full-text and faceted search"
  "recommendation-engine|ml-team|ML-based product recommendations"
  "product-catalog|commerce-team|Product data and catalogue management"
  "cart-service|commerce-team|Shopping cart and session state"
  "checkout-service|commerce-team|Checkout flow and confirmation"
  "pricing-service|commerce-team|Dynamic pricing and promotions"
  "review-service|content-team|User reviews and ratings"
  "media-service|content-team|Image and video asset management"
  "cdn-router|platform-team|CDN traffic routing and failover"
  "rate-limiter|platform-team|API rate limiting and throttling"
  "feature-flags|platform-team|Feature toggle and A/B configuration"
  "config-service|platform-team|Centralised configuration management"
  "audit-log-service|compliance-team|Immutable audit trail"
  "analytics-service|data-team|Real-time event analytics"
  "reporting-service|data-team|Scheduled report generation"
  "data-pipeline|data-team|Streaming data ingestion pipeline"
  "fraud-detection|security-team|Real-time transaction fraud signals"
  "kyc-service|compliance-team|Know-Your-Customer identity checks"
  "session-service|identity-team|Distributed session management"
  "token-service|identity-team|JWT issuance and rotation"
  "webhook-service|integrations-team|Outbound webhook delivery"
  "scheduler-service|platform-team|Distributed cron and job scheduling"
  "cache-service|platform-team|Distributed cache proxy"
  "queue-worker|messaging-team|Async job queue processing"
  "file-storage|content-team|Object storage abstraction"
  "export-service|data-team|Bulk data export"
  "import-service|data-team|Bulk data import and validation"
  "health-check-aggregator|platform-team|Service health rollup aggregator"
  "service-mesh-proxy|platform-team|Sidecar proxy and mTLS termination"
  "telemetry-collector|observability-team|Metrics and traces collection"
)

# Register every service; collect IDs in order
SERVICE_IDS=()
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r svc_name teamOwner description <<< "$entry"
  response=$(curl -s -w "\n%{http_code}" -X POST "$BASE/services" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$svc_name\",\"teamOwner\":\"$teamOwner\",\"description\":\"$description\"}")
  http_code=$(echo "$response" | tail -1)
  body=$(echo "$response" | head -1)
  if [[ "$http_code" != "201" ]]; then
    echo "  WARN: $svc_name → HTTP $http_code: $body"
    SERVICE_IDS+=("")
  else
    id=$(echo "$body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    SERVICE_IDS+=("$id")
    echo "  Registered: $svc_name (id=$id)"
  fi
done

echo ""
echo "==> Injecting healthy baseline signals for all services..."

inject_signal() {
  local svc_id=$1
  local metric=$2
  local value=$3
  [[ -z "$svc_id" ]] && return
  curl -s -X POST "$BASE/services/$svc_id/signals" \
    -H "Content-Type: application/json" \
    -d "{\"metricType\":\"$metric\",\"value\":$value}" > /dev/null
}

for id in "${SERVICE_IDS[@]}"; do
  inject_signal "$id" "ERROR_RATE"    0.8
  inject_signal "$id" "LATENCY_P99"   180.0
  inject_signal "$id" "CPU_USAGE"     35.0
  inject_signal "$id" "MEMORY_USAGE"  55.0
  inject_signal "$id" "REQUEST_RATE"  420.0
done

echo ""
echo "==> Triggering DEGRADED states on 5 services (error rate + latency spikes)..."

# api-gateway (index 0) — cascading error spike
GW_ID="${SERVICE_IDS[0]}"
echo "  Spiking api-gateway ($GW_ID)..."
inject_signal "$GW_ID" "ERROR_RATE"   12.5
inject_signal "$GW_ID" "LATENCY_P99"  3200.0
inject_signal "$GW_ID" "ERROR_RATE"   18.3
inject_signal "$GW_ID" "LATENCY_P99"  4800.0
inject_signal "$GW_ID" "CPU_USAGE"    88.0

# payment-service (index 3) — high error rate
PAY_ID="${SERVICE_IDS[3]}"
echo "  Spiking payment-service ($PAY_ID)..."
inject_signal "$PAY_ID" "ERROR_RATE"   22.1
inject_signal "$PAY_ID" "LATENCY_P99"  6100.0
inject_signal "$PAY_ID" "ERROR_RATE"   31.4
inject_signal "$PAY_ID" "MEMORY_USAGE" 91.0

# fraud-detection (index 26) — CPU saturation
FRAUD_ID="${SERVICE_IDS[26]}"
echo "  Spiking fraud-detection ($FRAUD_ID)..."
inject_signal "$FRAUD_ID" "CPU_USAGE"    94.0
inject_signal "$FRAUD_ID" "LATENCY_P99"  5500.0
inject_signal "$FRAUD_ID" "CPU_USAGE"    97.0

# recommendation-engine (index 11) — memory pressure
RECO_ID="${SERVICE_IDS[11]}"
echo "  Spiking recommendation-engine ($RECO_ID)..."
inject_signal "$RECO_ID" "MEMORY_USAGE"  93.0
inject_signal "$RECO_ID" "LATENCY_P99"   2900.0
inject_signal "$RECO_ID" "ERROR_RATE"    8.7

# data-pipeline (index 25) — latency degradation
PIPE_ID="${SERVICE_IDS[25]}"
echo "  Spiking data-pipeline ($PIPE_ID)..."
inject_signal "$PIPE_ID" "LATENCY_P99"   7200.0
inject_signal "$PIPE_ID" "ERROR_RATE"    14.2
inject_signal "$PIPE_ID" "CPU_USAGE"     82.0

echo ""
echo "==> Seed complete. Checking incident queue..."
sleep 2
echo "--- Incidents ---"
curl -s "$BASE/incidents" | python3 -m json.tool 2>/dev/null || curl -s "$BASE/incidents"
echo ""
echo "--- Services (DEGRADED first) ---"
curl -s "$BASE/services" | python3 -c "
import json,sys
data=json.load(sys.stdin)
data.sort(key=lambda x: (x['healthStatus']!='DEGRADED', x['name']))
for s in data[:10]:
    print(f\"  {s['healthStatus']:12} {s['name']:35} team={s['teamOwner']}\")
print(f'  ... ({len(data)} total)')
" 2>/dev/null || echo "(python3 not available for pretty-print)"
echo ""
echo "✓ Done. Open http://localhost:5173 to view the dashboard."
