#!/usr/bin/env bash
#
# Milestone 1 — "The Golden Path" end-to-end verification.
#
# Validates the exit criteria from ARCHITECTURE_AND_PLAN.md §3 against a LIVE environment:
# the full docker-compose infra stack running, and all 8 Spring Boot services started
# (e.g. `JAVA_HOME=~/.sdkman/candidates/java/21-tem ./gradlew bootRun` per module, or your IDE).
#
# Usage:
#   docker compose up -d
#   # start all 8 services (see each module's application.yml for its port)
#   ./scripts/verify-milestone-1.sh
#
# Prerequisites on PATH: curl, jq, psql. The Temporal CLI (`temporal`) is optional — Step 5 is
# skipped with a warning (not a failure) if it isn't installed, since there's no simple REST
# fallback for workflow status against a Temporal server that doesn't have its HTTP API enabled.
#
# Re-run safety: this script creates a NEW payment every run (a fresh UUID per invocation), but
# always under the SAME demo merchant id, so ledger balances accumulate across runs against a
# long-lived environment. Step 3 therefore asserts the *delta* this run's capture produced, not
# an absolute balance — the script is safe to run repeatedly without tearing the stack down.
set -euo pipefail

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
GATEWAY_URL="http://localhost:8080"
LEDGER_URL="http://localhost:8085"
MERCHANT_ID="11111111-1111-1111-1111-111111111111"
AMOUNT_MINOR=21400
IDEMPOTENCY_KEY="demo-m1-$(date +%s)-$$"

PG_USER="payflow"
PG_PASSWORD="payflow"
PG_HOST="localhost"
PG_PORT="5432"

POLL_ATTEMPTS=10
POLL_INTERVAL_SECONDS=1

declare -A SERVICE_PORTS=(
  [payflow-api-gateway]=8080
  [payflow-payment-command-service]=8081
  [payflow-payment-query-service]=8082
  [payflow-provider-gateway-service]=8083
  [payflow-payment-orchestrator]=8084
  [payflow-ledger-service]=8085
  [payflow-notification-service]=8086
)

declare -A INFRA_PORTS=(
  [Postgres]=5432
  [Redis]=6379
  [Kafka]=9092
  [Temporal]=7233
)

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
BOLD='\033[1m'
RESET='\033[0m'

step() { echo -e "\n${BOLD}== $* ==${RESET}"; }
pass() { echo -e "  ${GREEN}[PASS]${RESET} $*"; }
warn() { echo -e "  ${YELLOW}[WARN]${RESET} $*"; }
fail() { echo -e "  ${RED}[FAIL]${RESET} $*"; exit 1; }

# ---------------------------------------------------------------------------
# Prerequisite tools
# ---------------------------------------------------------------------------
step "Checking required tools"
for tool in curl jq psql; do
  command -v "$tool" >/dev/null 2>&1 || fail "required tool '$tool' not found on PATH"
done
pass "curl, jq, psql available"

TEMPORAL_CLI_AVAILABLE=false
if command -v temporal >/dev/null 2>&1; then
  TEMPORAL_CLI_AVAILABLE=true
  pass "temporal CLI available"
else
  warn "temporal CLI not found — Step 5 (workflow status) will be skipped"
fi

# ---------------------------------------------------------------------------
# Step 0a — infra port probes
# ---------------------------------------------------------------------------
step "Health Check Probes — infrastructure ports"
check_tcp_port() {
  local name="$1" port="$2"
  if (exec 3<>"/dev/tcp/localhost/${port}") 2>/dev/null; then
    exec 3>&- 3<&-
    pass "${name} is listening on port ${port}"
  else
    fail "${name} is NOT listening on port ${port} — is docker compose up?"
  fi
}
for name in "${!INFRA_PORTS[@]}"; do
  check_tcp_port "$name" "${INFRA_PORTS[$name]}"
done

# ---------------------------------------------------------------------------
# Step 0b — service health probes
# ---------------------------------------------------------------------------
step "Health Check Probes — microservice /actuator/health"
for service in "${!SERVICE_PORTS[@]}"; do
  port="${SERVICE_PORTS[$service]}"
  status=$(curl -sf "http://localhost:${port}/actuator/health" | jq -r '.status' 2>/dev/null || echo "UNREACHABLE")
  if [[ "$status" == "UP" ]]; then
    pass "${service} (:${port}) — UP"
  else
    fail "${service} (:${port}) — ${status}. Is it started?"
  fi
done

# ---------------------------------------------------------------------------
# Step 1 — create payment
# ---------------------------------------------------------------------------
step "Step 1 — Create Payment"
CREATE_RESPONSE=$(curl -s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/api/v1/payments" \
  -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
  -H "Content-Type: application/json" \
  -d '{
        "merchantId": "'"${MERCHANT_ID}"'",
        "customerName": "Elena Marsh",
        "amountMinor": '"${AMOUNT_MINOR}"',
        "currency": "USD",
        "cardNumber": "4242424242424242"
      }')
CREATE_STATUS=$(echo "$CREATE_RESPONSE" | tail -n1)
CREATE_BODY=$(echo "$CREATE_RESPONSE" | sed '$d')

[[ "$CREATE_STATUS" == "201" ]] || fail "expected HTTP 201 from create-payment, got ${CREATE_STATUS}: ${CREATE_BODY}"
pass "HTTP 201 Created"

PAYMENT_ID=$(echo "$CREATE_BODY" | jq -r '.paymentId')
[[ -n "$PAYMENT_ID" && "$PAYMENT_ID" != "null" ]] || fail "no paymentId in response: ${CREATE_BODY}"
pass "paymentId = ${PAYMENT_ID}"

# ---------------------------------------------------------------------------
# Baseline ledger balances — read BEFORE polling for capture, so Step 3 can assert the delta
# this specific payment produced rather than an absolute value (see header comment).
# ---------------------------------------------------------------------------
fetch_balance() {
  curl -sf "${LEDGER_URL}/accounts/${1}/balance" | jq -r '.balanceMinor'
}
PLATFORM_BANK_BEFORE=$(fetch_balance "platform-bank")
MERCHANT_PAYABLE_BEFORE=$(fetch_balance "merchant-payable")

# ---------------------------------------------------------------------------
# Step 2 — poll until captured
# ---------------------------------------------------------------------------
step "Step 2 — Poll Until Captured"
STATUS="UNKNOWN"
for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
  QUERY_RESPONSE=$(curl -s -w '\n%{http_code}' "${GATEWAY_URL}/api/v1/payments/${PAYMENT_ID}")
  QUERY_STATUS_CODE=$(echo "$QUERY_RESPONSE" | tail -n1)
  QUERY_BODY=$(echo "$QUERY_RESPONSE" | sed '$d')

  if [[ "$QUERY_STATUS_CODE" == "200" ]]; then
    STATUS=$(echo "$QUERY_BODY" | jq -r '.status')
    echo "  attempt ${attempt}/${POLL_ATTEMPTS}: status=${STATUS}"
    [[ "$STATUS" == "CAPTURED" ]] && break
  else
    echo "  attempt ${attempt}/${POLL_ATTEMPTS}: HTTP ${QUERY_STATUS_CODE} (not projected yet)"
  fi
  sleep "$POLL_INTERVAL_SECONDS"
done

[[ "$STATUS" == "CAPTURED" ]] || fail "payment did not reach CAPTURED after ${POLL_ATTEMPTS} attempts (last status: ${STATUS})"
pass "payment ${PAYMENT_ID} is CAPTURED"

# ---------------------------------------------------------------------------
# Step 3 — confirm ledger balances
# ---------------------------------------------------------------------------
step "Step 3 — Confirm Ledger Balances"
# 2.9% platform fee (LedgerPostingService.PLATFORM_FEE_RATE), rounded half-up — NOT the "97.1%
# / $6.14 fee" figure in the architect directive: Math.round(21400 * 0.029) = 621, not 614, so
# the merchant's share is 20779, not 20786. Verified against the actual implementation and its
# own passing test (LedgerPostingServiceTest) rather than taken on faith from the directive.
EXPECTED_FEE_MINOR=$(( (AMOUNT_MINOR * 29 + 500) / 1000 ))   # round-half-up of amount * 0.029
EXPECTED_MERCHANT_SHARE=$(( AMOUNT_MINOR - EXPECTED_FEE_MINOR ))

PLATFORM_BANK_AFTER=$(fetch_balance "platform-bank")
MERCHANT_PAYABLE_AFTER=$(fetch_balance "merchant-payable")

PLATFORM_BANK_DELTA=$(( PLATFORM_BANK_AFTER - PLATFORM_BANK_BEFORE ))
MERCHANT_PAYABLE_DELTA=$(( MERCHANT_PAYABLE_AFTER - MERCHANT_PAYABLE_BEFORE ))

[[ "$PLATFORM_BANK_DELTA" -eq "$AMOUNT_MINOR" ]] \
  || fail "Platform Bank balance moved by ${PLATFORM_BANK_DELTA}, expected ${AMOUNT_MINOR}"
pass "Platform Bank balance increased by ${PLATFORM_BANK_DELTA} (= amount captured)"

[[ "$MERCHANT_PAYABLE_DELTA" -eq "$EXPECTED_MERCHANT_SHARE" ]] \
  || fail "Merchant Payable balance moved by ${MERCHANT_PAYABLE_DELTA}, expected ${EXPECTED_MERCHANT_SHARE}"
pass "Merchant Payable balance increased by ${MERCHANT_PAYABLE_DELTA} (= amount minus ${EXPECTED_FEE_MINOR} fee)"

# ---------------------------------------------------------------------------
# Step 4 — confirm webhook delivery
# ---------------------------------------------------------------------------
step "Step 4 — Confirm Webhook Delivery"
# webhook_deliveries has no merchant_id column (only a nullable endpoint_id, which is NULL here
# since this demo merchant has no webhook_endpoints row configured) — the payment id embedded
# in the payload JSON is the only reliable way to match this run's own delivery row.
DELIVERY_COUNT=$(PGPASSWORD="$PG_PASSWORD" psql -X -At \
  -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d webhook_db \
  -c "SELECT count(*) FROM webhook_deliveries WHERE event_type = 'payment.captured' AND payload::text LIKE '%${PAYMENT_ID}%' AND status = 'DELIVERED';")

[[ "$DELIVERY_COUNT" -ge 1 ]] || fail "no DELIVERED payment.captured webhook_deliveries row found for payment ${PAYMENT_ID}"
pass "found ${DELIVERY_COUNT} DELIVERED payment.captured webhook delivery row(s) for this payment"

# ---------------------------------------------------------------------------
# Step 5 — verify Temporal workflow status
# ---------------------------------------------------------------------------
step "Step 5 — Verify Temporal Workflow Status"
if [[ "$TEMPORAL_CLI_AVAILABLE" == true ]]; then
  WORKFLOW_STATUS=$(temporal workflow show --workflow-id "${PAYMENT_ID}" --address localhost:7233 --output json 2>/dev/null \
    | jq -r '.workflowExecutionInfo.status // .status // "UNKNOWN"')
  [[ "$WORKFLOW_STATUS" == "COMPLETED" || "$WORKFLOW_STATUS" == "WORKFLOW_EXECUTION_STATUS_COMPLETED" ]] \
    || fail "Temporal workflow ${PAYMENT_ID} status is '${WORKFLOW_STATUS}', expected COMPLETED"
  pass "Temporal workflow ${PAYMENT_ID} is COMPLETED"
else
  warn "skipped — temporal CLI not installed"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo -e "\n${GREEN}${BOLD}[PASSED] Milestone 1 Golden Path Verified${RESET}"
echo "  paymentId: ${PAYMENT_ID}"
echo "  amountMinor: ${AMOUNT_MINOR} | fee: ${EXPECTED_FEE_MINOR} | merchantShare: ${EXPECTED_MERCHANT_SHARE}"
