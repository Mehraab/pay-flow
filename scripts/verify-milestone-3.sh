#!/usr/bin/env bash
#
# Milestone 3 — "Payout & Refund Lifecycles" end-to-end verification.
#
# Validates the 4 scenarios from ARCHITECTURE_AND_PLAN.md §3 against a LIVE environment: the
# full docker-compose infra stack running, and all 8 Spring Boot services started.
#
# Usage:
#   docker compose up -d
#   # start all 8 services (see each module's application.yml for its port)
#   ./scripts/verify-milestone-3.sh
#
# Prerequisites on PATH: curl, jq.
#
# Re-run safety: Scenario 1 reuses the fixed demo merchant from earlier milestones' scripts (its
# own payment id is always fresh, so re-running is safe). Scenarios 2 and 3 generate a BRAND NEW
# merchant UUID every run specifically so their balance-is-now-zero assertions never have to
# account for state left over by a previous run.
set -euo pipefail

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
GATEWAY_URL="http://localhost:8080"
LEDGER_URL="http://localhost:8085"
DEMO_MERCHANT_ID="11111111-1111-1111-1111-111111111111"
AMOUNT_MINOR=21400
RUN_ID="$(date +%s)-$$"

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

generate_uuid() {
  if command -v uuidgen >/dev/null 2>&1; then
    uuidgen
  else
    cat /proc/sys/kernel/random/uuid
  fi
}

# ---------------------------------------------------------------------------
# Prerequisite tools & reachability
# ---------------------------------------------------------------------------
step "Checking required tools and reachability"
for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || fail "required tool '$tool' not found on PATH"
done
curl -sf "${GATEWAY_URL}/actuator/health" >/dev/null || fail "payflow-api-gateway (:8080) is unreachable — is the stack up?"
curl -sf "${LEDGER_URL}/actuator/health" >/dev/null || fail "payflow-ledger-service (:8085) is unreachable — is the stack up?"
pass "required tools present, gateway and ledger service reachable"

# ---------------------------------------------------------------------------
# Shared helpers
# ---------------------------------------------------------------------------
create_payment() {
  local merchant_id="$1" card="$2" idempotency_key="$3"
  local response status_code body
  response=$(curl -s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/api/v1/payments" \
    -H "Idempotency-Key: ${idempotency_key}" -H "Content-Type: application/json" \
    -d '{
          "merchantId": "'"${merchant_id}"'",
          "customerName": "Elena Marsh",
          "amountMinor": '"${AMOUNT_MINOR}"',
          "currency": "USD",
          "cardNumber": "'"${card}"'"
        }')
  status_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')
  [[ "$status_code" == "201" ]] || fail "expected HTTP 201 from create-payment, got ${status_code}: ${body}"
  echo "$body" | jq -r '.paymentId'
}

# Polls the query service (via the gateway) until $2 matches the payment's status.
poll_payment_until_status() {
  local payment_id="$1" expected_status="$2"
  for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
    local response status_code body status
    response=$(curl -s -w '\n%{http_code}' "${GATEWAY_URL}/api/v1/payments/${payment_id}")
    status_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    if [[ "$status_code" == "200" ]]; then
      status=$(echo "$body" | jq -r '.status')
      echo "  attempt ${attempt}/${POLL_ATTEMPTS}: payment status=${status}" >&2
      [[ "$status" == "$expected_status" ]] && return 0
    else
      echo "  attempt ${attempt}/${POLL_ATTEMPTS}: HTTP ${status_code} (not projected yet)" >&2
    fi
    sleep "$POLL_INTERVAL_SECONDS"
  done
  fail "payment ${payment_id} did not reach ${expected_status} after ${POLL_ATTEMPTS} attempts"
}

# Polls the ledger service's payout endpoint until $2 matches its status.
poll_payout_until_status() {
  local payout_id="$1" expected_status="$2"
  for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
    local response status_code body status
    response=$(curl -s -w '\n%{http_code}' "${LEDGER_URL}/payouts/${payout_id}")
    status_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    if [[ "$status_code" == "200" ]]; then
      status=$(echo "$body" | jq -r '.status')
      echo "  attempt ${attempt}/${POLL_ATTEMPTS}: payout status=${status}" >&2
      [[ "$status" == "$expected_status" ]] && return 0
    else
      echo "  attempt ${attempt}/${POLL_ATTEMPTS}: HTTP ${status_code} (payout not found yet)" >&2
    fi
    sleep "$POLL_INTERVAL_SECONDS"
  done
  fail "payout ${payout_id} did not reach ${expected_status} after ${POLL_ATTEMPTS} attempts"
}

# Polls GET /ledger?transaction= until at least $2 entries exist — the merchant-initiated
# refund's reversal is posted asynchronously (outbox -> Kafka -> PaymentRefundedConsumer), so it
# can lag slightly behind the payment's own status flipping to REFUNDED.
poll_ledger_entry_count() {
  local transaction_ref="$1" expected_count="$2"
  for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
    local count
    count=$(curl -sf "${LEDGER_URL}/ledger" --get --data-urlencode "transaction=${transaction_ref}" | jq 'length')
    echo "  attempt ${attempt}/${POLL_ATTEMPTS}: ${count} ledger entries for ${transaction_ref}" >&2
    [[ "$count" -eq "$expected_count" ]] && return 0
    sleep "$POLL_INTERVAL_SECONDS"
  done
  fail "transaction ${transaction_ref} never reached ${expected_count} ledger entries"
}

fetch_merchant_balance() {
  curl -sf "${LEDGER_URL}/accounts/${1}/${2}/balance" | jq -r '.balanceMinor'
}

# ---------------------------------------------------------------------------
# Scenario 1 — Merchant Refund (BR-16)
# ---------------------------------------------------------------------------
step "Scenario 1 — Merchant Refund (BR-16)"
REFUND_PAYMENT_ID=$(create_payment "$DEMO_MERCHANT_ID" "4242424242424242" "demo-m3-refund-${RUN_ID}")
pass "created payment ${REFUND_PAYMENT_ID}"

poll_payment_until_status "$REFUND_PAYMENT_ID" "CAPTURED"
pass "payment ${REFUND_PAYMENT_ID} reached CAPTURED"

REFUND_RESPONSE=$(curl -s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/api/v1/payments/${REFUND_PAYMENT_ID}/refund")
REFUND_STATUS_CODE=$(echo "$REFUND_RESPONSE" | tail -n1)
[[ "$REFUND_STATUS_CODE" == "200" ]] || fail "expected HTTP 200 from refund, got ${REFUND_STATUS_CODE}"
pass "refund request accepted"

poll_payment_until_status "$REFUND_PAYMENT_ID" "REFUNDED"
pass "payment ${REFUND_PAYMENT_ID} reached REFUNDED"

poll_ledger_entry_count "$REFUND_PAYMENT_ID" 4
pass "4 ledger entries exist for ${REFUND_PAYMENT_ID} — 2 capture legs + 2 reversing legs"

# ---------------------------------------------------------------------------
# Scenario 2 — Manual Payout & Settlement (BR-15)
# ---------------------------------------------------------------------------
step "Scenario 2 — Manual Payout & Settlement (BR-15)"
PAYOUT_MERCHANT_ID=$(generate_uuid)
PAYOUT_PAYMENT_ID=$(create_payment "$PAYOUT_MERCHANT_ID" "4242424242424242" "demo-m3-payout-${RUN_ID}")
pass "created payment ${PAYOUT_PAYMENT_ID} for fresh merchant ${PAYOUT_MERCHANT_ID}"

poll_payment_until_status "$PAYOUT_PAYMENT_ID" "CAPTURED"
pass "payment ${PAYOUT_PAYMENT_ID} reached CAPTURED"

PAYOUT_RESPONSE=$(curl -s -w '\n%{http_code}' -X POST "${LEDGER_URL}/payouts" \
  -H "Content-Type: application/json" -d '{"merchantId":"'"${PAYOUT_MERCHANT_ID}"'"}')
PAYOUT_STATUS_CODE=$(echo "$PAYOUT_RESPONSE" | tail -n1)
PAYOUT_BODY=$(echo "$PAYOUT_RESPONSE" | sed '$d')
[[ "$PAYOUT_STATUS_CODE" == "201" ]] || fail "expected HTTP 201 from create-payout, got ${PAYOUT_STATUS_CODE}: ${PAYOUT_BODY}"
PAYOUT_ID=$(echo "$PAYOUT_BODY" | jq -r '.id')
PAYOUT_INITIAL_STATUS=$(echo "$PAYOUT_BODY" | jq -r '.status')
[[ "$PAYOUT_INITIAL_STATUS" == "IN_TRANSIT" ]] || fail "expected payout status IN_TRANSIT, got ${PAYOUT_INITIAL_STATUS}"
pass "payout ${PAYOUT_ID} created IN_TRANSIT"

sleep 4 # the SettlementSimulator only settles a payout at least 2s after it was created

poll_payout_until_status "$PAYOUT_ID" "SETTLED"
pass "payout ${PAYOUT_ID} reached SETTLED"

PAYOUT_IN_TRANSIT_BALANCE=$(fetch_merchant_balance "$PAYOUT_MERCHANT_ID" "payout-in-transit")
[[ "$PAYOUT_IN_TRANSIT_BALANCE" -eq 0 ]] \
  || fail "expected Payout In-Transit balance 0 for merchant ${PAYOUT_MERCHANT_ID}, got ${PAYOUT_IN_TRANSIT_BALANCE}"
pass "Payout In-Transit balance for ${PAYOUT_MERCHANT_ID} is 0"

# ---------------------------------------------------------------------------
# Scenario 3 — Automated EOD Sweep
# ---------------------------------------------------------------------------
step "Scenario 3 — Automated EOD Sweep"
SWEEP_PAYMENT_ID=$(create_payment "$PAYOUT_MERCHANT_ID" "4242424242424242" "demo-m3-sweep-${RUN_ID}")
pass "created another payment ${SWEEP_PAYMENT_ID} for merchant ${PAYOUT_MERCHANT_ID}"

poll_payment_until_status "$SWEEP_PAYMENT_ID" "CAPTURED"
pass "payment ${SWEEP_PAYMENT_ID} reached CAPTURED"

SWEEP_RESPONSE=$(curl -sf -X POST "${LEDGER_URL}/payouts/sweep")
SWEEP_PAYOUT_ID=$(echo "$SWEEP_RESPONSE" | jq -r --arg mid "$PAYOUT_MERCHANT_ID" '.[] | select(.merchantId == $mid) | .id')
[[ -n "$SWEEP_PAYOUT_ID" && "$SWEEP_PAYOUT_ID" != "null" ]] \
  || fail "sweep did not create a payout for merchant ${PAYOUT_MERCHANT_ID}: ${SWEEP_RESPONSE}"
pass "sweep created payout ${SWEEP_PAYOUT_ID} for merchant ${PAYOUT_MERCHANT_ID}"

sleep 4

poll_payout_until_status "$SWEEP_PAYOUT_ID" "SETTLED"
pass "swept payout ${SWEEP_PAYOUT_ID} settled automatically"

# ---------------------------------------------------------------------------
# Scenario 4 — Ledger Integrity Reconciliation (BR-12)
# ---------------------------------------------------------------------------
step "Scenario 4 — Ledger Integrity Reconciliation (BR-12)"
INTEGRITY_RESPONSE=$(curl -sf "${LEDGER_URL}/operator/ledger/integrity")
BALANCED=$(echo "$INTEGRITY_RESPONSE" | jq -r '.balanced')
TOTAL_DEBITS=$(echo "$INTEGRITY_RESPONSE" | jq -r '.totalDebitsMinor')
TOTAL_CREDITS=$(echo "$INTEGRITY_RESPONSE" | jq -r '.totalCreditsMinor')

[[ "$BALANCED" == "true" ]] || fail "ledger integrity reported balanced=false: ${INTEGRITY_RESPONSE}"
[[ "$TOTAL_DEBITS" -eq "$TOTAL_CREDITS" ]] \
  || fail "totalDebitsMinor (${TOTAL_DEBITS}) != totalCreditsMinor (${TOTAL_CREDITS})"
pass "ledger balanced — totalDebitsMinor == totalCreditsMinor == ${TOTAL_DEBITS}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo -e "\n${GREEN}${BOLD}[PASSED] Milestone 3 Payout & Refund Lifecycles Verified${RESET}"
echo "  refunded payment:  ${REFUND_PAYMENT_ID} (REFUNDED, 4 ledger entries)"
echo "  manual payout:     ${PAYOUT_ID} for merchant ${PAYOUT_MERCHANT_ID} (SETTLED)"
echo "  swept payout:      ${SWEEP_PAYOUT_ID} (SETTLED)"
echo "  ledger integrity:  balanced (debits == credits == ${TOTAL_DEBITS})"
