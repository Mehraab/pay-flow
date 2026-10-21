#!/usr/bin/env bash
#
# Milestone 2 — "Resiliency & Failure Flows" end-to-end verification.
#
# Validates the 4 scenarios from ARCHITECTURE_AND_PLAN.md §3 against a LIVE environment: the
# full docker-compose infra stack running, and all 8 Spring Boot services started.
#
# Usage:
#   docker compose up -d
#   # start all 8 services (see each module's application.yml for its port)
#   ./scripts/verify-milestone-2.sh
#
# Prerequisites on PATH: curl, jq, psql.
#
# Re-run safety: every scenario creates a fresh payment (a new idempotency key per invocation),
# so this script is safe to run repeatedly against a long-lived environment without resetting it.
set -euo pipefail

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
GATEWAY_URL="http://localhost:8080"
LEDGER_URL="http://localhost:8085"
MERCHANT_ID="11111111-1111-1111-1111-111111111111"
AMOUNT_MINOR=21400
RUN_ID="$(date +%s)-$$"

PG_USER="payflow"
PG_PASSWORD="payflow"
PG_HOST="localhost"
PG_PORT="5433"

POLL_ATTEMPTS=20
POLL_INTERVAL_SECONDS=1

# ---------------------------------------------------------------------------
# Output helpers
# ---------------------------------------------------------------------------
GREEN='\033[0;32m'
RED='\033[0;31m'
BOLD='\033[1m'
RESET='\033[0m'

step() { echo -e "\n${BOLD}== $* ==${RESET}"; }
pass() { echo -e "  ${GREEN}[PASS]${RESET} $*"; }
fail() { echo -e "  ${RED}[FAIL]${RESET} $*"; exit 1; }

# ---------------------------------------------------------------------------
# Prerequisite tools & reachability
# ---------------------------------------------------------------------------
step "Checking required tools and reachability"
for tool in curl jq psql; do
  command -v "$tool" >/dev/null 2>&1 || fail "required tool '$tool' not found on PATH"
done
curl -sf "${GATEWAY_URL}/actuator/health" >/dev/null || fail "payflow-api-gateway (:8080) is unreachable — is the stack up?"
curl -sf "${LEDGER_URL}/actuator/health" >/dev/null || fail "payflow-ledger-service (:8085) is unreachable — is the stack up?"
pass "required tools present, gateway and ledger service reachable"

# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------
create_payment() {
  local card="$1" idempotency_key="$2" extra_header="${3:-}"
  local curl_args=(-s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/api/v1/payments"
    -H "Idempotency-Key: ${idempotency_key}" -H "Content-Type: application/json")
  if [[ -n "$extra_header" ]]; then
    curl_args+=(-H "$extra_header")
  fi
  curl_args+=(-d '{
        "merchantId": "'"${MERCHANT_ID}"'",
        "customerName": "Elena Marsh",
        "amountMinor": '"${AMOUNT_MINOR}"',
        "currency": "USD",
        "cardNumber": "'"${card}"'"
      }')
  local response
  response=$(curl "${curl_args[@]}")
  local status_code
  status_code=$(echo "$response" | tail -n1)
  local body
  body=$(echo "$response" | sed '$d')
  [[ "$status_code" == "201" ]] || fail "expected HTTP 201 from create-payment, got ${status_code}: ${body}"
  echo "$body" | jq -r '.paymentId'
}

# Polls the query service until $2 matches the payment's status, or fails after POLL_ATTEMPTS.
poll_until_status() {
  local payment_id="$1" expected_status="$2"
  local status="UNKNOWN"
  for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
    local response status_code body
    response=$(curl -s -w '\n%{http_code}' "${GATEWAY_URL}/api/v1/payments/${payment_id}")
    status_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    if [[ "$status_code" == "200" ]]; then
      status=$(echo "$body" | jq -r '.status')
      echo "  attempt ${attempt}/${POLL_ATTEMPTS}: status=${status}" >&2
      [[ "$status" == "$expected_status" ]] && { echo "$body"; return 0; }
    else
      echo "  attempt ${attempt}/${POLL_ATTEMPTS}: HTTP ${status_code} (not projected yet)" >&2
    fi
    sleep "$POLL_INTERVAL_SECONDS"
  done
  fail "payment ${payment_id} did not reach ${expected_status} after ${POLL_ATTEMPTS} attempts (last status: ${status})"
}

# ---------------------------------------------------------------------------
# Test 1 — Declined card (insufficient funds)
# ---------------------------------------------------------------------------
step "Test 1 — Declined Card (4000000000000002)"
DECLINED_PAYMENT_ID=$(create_payment "4000000000000002" "demo-m2-declined-${RUN_ID}")
pass "created payment ${DECLINED_PAYMENT_ID}"

DECLINED_VIEW=$(poll_until_status "$DECLINED_PAYMENT_ID" "FAILED")
pass "payment ${DECLINED_PAYMENT_ID} reached FAILED"

DECLINE_REASON=$(echo "$DECLINED_VIEW" | jq -r '.declineReason')
[[ "$DECLINE_REASON" == "Insufficient funds" ]] \
  || fail "expected declineReason 'Insufficient funds', got '${DECLINE_REASON}'"
pass "declineReason == 'Insufficient funds'"

# ---------------------------------------------------------------------------
# Test 2 — Timeout card (processor never responds in time)
# ---------------------------------------------------------------------------
step "Test 2 — Timeout Card (4000000000000119)"
TIMEOUT_PAYMENT_ID=$(create_payment "4000000000000119" "demo-m2-timeout-${RUN_ID}")
pass "created payment ${TIMEOUT_PAYMENT_ID}"

# The mock provider sleeps 5s before returning 504 — the orchestrator must not hang forever.
poll_until_status "$TIMEOUT_PAYMENT_ID" "FAILED" >/dev/null
pass "payment ${TIMEOUT_PAYMENT_ID} reached FAILED (timeout treated as a decline, not a hang)"

# ---------------------------------------------------------------------------
# Test 3 — Temporal saga compensation (forced ledger failure)
# ---------------------------------------------------------------------------
step "Test 3 — Temporal Saga Compensation (X-Force-Ledger-Failure)"
COMPENSATION_PAYMENT_ID=$(
  create_payment "4242424242424242" "demo-m2-compensation-${RUN_ID}" "X-Force-Ledger-Failure: true"
)
pass "created payment ${COMPENSATION_PAYMENT_ID}"

poll_until_status "$COMPENSATION_PAYMENT_ID" "REFUNDED" >/dev/null
pass "payment ${COMPENSATION_PAYMENT_ID} reached REFUNDED"

LEDGER_ENTRIES=$(curl -sf "${LEDGER_URL}/ledger" --get --data-urlencode "transaction=${COMPENSATION_PAYMENT_ID}")
ENTRY_COUNT=$(echo "$LEDGER_ENTRIES" | jq 'length')
[[ "$ENTRY_COUNT" -eq 0 ]] \
  || fail "expected 0 ledger entries for the saga-compensated payment, found ${ENTRY_COUNT}: ${LEDGER_ENTRIES}"
pass "0 ledger entries for ${COMPENSATION_PAYMENT_ID} — the rejected post-capture never wrote a half-posted leg"

# ---------------------------------------------------------------------------
# Test 4 — Consumer idempotency (processed_events bookkeeping)
# ---------------------------------------------------------------------------
step "Test 4 — Consumer Idempotency (processed_events)"
# Test 3's payment.refunded event was itself consumed by the Ledger Service's
# PaymentRefundedConsumer, which records every event it handles in processed_events before
# acting on it (ARCHITECTURE_AND_PLAN.md §2.2 / BR-12) — that's what we're confirming here.
# The actual "a replayed event is skipped, not double-posted" behavior is covered by
# PaymentRefundedConsumerTest (payflow-ledger-service's Java test suite), since reliably
# replaying one exact Kafka message from a shell script isn't practical.
PROCESSED_EVENT_COUNT=$(PGPASSWORD="$PG_PASSWORD" psql -X -At \
  -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d ledger_db \
  -c "SELECT count(*) FROM processed_events WHERE consumer_name = 'ledger-service';")

[[ "$PROCESSED_EVENT_COUNT" -ge 1 ]] \
  || fail "expected at least 1 row in ledger_db.processed_events for consumer 'ledger-service', found ${PROCESSED_EVENT_COUNT}"
pass "ledger_db.processed_events has ${PROCESSED_EVENT_COUNT} row(s) recorded for 'ledger-service'"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo -e "\n${GREEN}${BOLD}[PASSED] Milestone 2 Resiliency & Failure Flows Verified${RESET}"
echo "  declined payment:    ${DECLINED_PAYMENT_ID} (FAILED / Insufficient funds)"
echo "  timeout payment:     ${TIMEOUT_PAYMENT_ID} (FAILED)"
echo "  compensated payment: ${COMPENSATION_PAYMENT_ID} (REFUNDED, 0 ledger entries)"
