#!/usr/bin/env bash
#
# Milestone 4 — "Security, RBAC & Observability" end-to-end verification.
#
# Validates the 8 scenarios from ARCHITECTURE_AND_PLAN.md §4 against a LIVE environment: the
# full docker-compose infra stack running (Postgres, Redis, Kafka, Temporal) and all 8 Spring
# Boot services started. Every operator/merchant call in this script goes through the API
# Gateway (:8080) — never directly at a downstream service — because the point of this
# milestone is the gateway's JwtAuthenticationFilter, not the services behind it.
#
# Usage:
#   docker compose up -d
#   # start all 8 services (see each module's application.yml for its port)
#   ./scripts/verify-milestone-4.sh
#
# Prerequisites on PATH: curl, jq.
#
# Re-run safety: every merchant/payment used here is generated fresh each run (a brand new
# UUID and a brand new idempotency key), so re-running never collides with a previous run.
set -euo pipefail

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
GATEWAY_URL="http://localhost:8080"
AMOUNT_MINOR=21400
RUN_ID="$(date +%s)-$$"

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
pass "required tools present, gateway reachable"

# ---------------------------------------------------------------------------
# Shared helper — issue an access/refresh token pair for the given role.
# ---------------------------------------------------------------------------
issue_token() {
  local role="$1" merchant_id="${2:-}"
  local body response status_code
  if [[ -n "$merchant_id" ]]; then
    body='{"role":"'"${role}"'","merchantId":"'"${merchant_id}"'"}'
  else
    body='{"role":"'"${role}"'"}'
  fi
  response=$(curl -s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/auth/token" \
    -H "Content-Type: application/json" -d "$body")
  status_code=$(echo "$response" | tail -n1)
  [[ "$status_code" == "200" ]] || fail "expected HTTP 200 from /auth/token (role=${role}), got ${status_code}"
  echo "$response" | sed '$d'
}

# ---------------------------------------------------------------------------
# Test 1 — JWT Issuance & Authorized Payment Creation
# ---------------------------------------------------------------------------
step "Test 1 — JWT Issuance & Authorized Payment Creation"
MERCHANT_ID=$(generate_uuid)
MERCHANT_TOKEN_RESPONSE=$(issue_token "MERCHANT" "$MERCHANT_ID")
MERCHANT_ACCESS_TOKEN=$(echo "$MERCHANT_TOKEN_RESPONSE" | jq -r '.accessToken')
MERCHANT_REFRESH_TOKEN=$(echo "$MERCHANT_TOKEN_RESPONSE" | jq -r '.refreshToken')
[[ -n "$MERCHANT_ACCESS_TOKEN" && "$MERCHANT_ACCESS_TOKEN" != "null" ]] || fail "no accessToken in /auth/token response: ${MERCHANT_TOKEN_RESPONSE}"
[[ -n "$MERCHANT_REFRESH_TOKEN" && "$MERCHANT_REFRESH_TOKEN" != "null" ]] || fail "no refreshToken in /auth/token response: ${MERCHANT_TOKEN_RESPONSE}"
pass "issued merchant accessToken + refreshToken for merchant ${MERCHANT_ID}"

PAYMENT_RESPONSE=$(curl -s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/api/v1/payments" \
  -H "Authorization: Bearer ${MERCHANT_ACCESS_TOKEN}" \
  -H "Idempotency-Key: demo-m4-${RUN_ID}" -H "Content-Type: application/json" \
  -d '{
        "merchantId": "'"${MERCHANT_ID}"'",
        "customerName": "Elena Marsh",
        "amountMinor": '"${AMOUNT_MINOR}"',
        "currency": "USD",
        "cardNumber": "4242424242424242"
      }')
PAYMENT_STATUS_CODE=$(echo "$PAYMENT_RESPONSE" | tail -n1)
[[ "$PAYMENT_STATUS_CODE" == "201" ]] || fail "expected HTTP 201 creating an authorized payment, got ${PAYMENT_STATUS_CODE}"
pass "authorized payment created with a valid merchant access token"

# ---------------------------------------------------------------------------
# Test 2 — Refresh Token Rotation
# ---------------------------------------------------------------------------
step "Test 2 — Refresh Token Rotation"
REFRESH_RESPONSE=$(curl -s -w '\n%{http_code}' -X POST "${GATEWAY_URL}/auth/refresh" \
  -H "Content-Type: application/json" -d '{"refreshToken":"'"${MERCHANT_REFRESH_TOKEN}"'"}')
REFRESH_STATUS_CODE=$(echo "$REFRESH_RESPONSE" | tail -n1)
REFRESH_BODY=$(echo "$REFRESH_RESPONSE" | sed '$d')
[[ "$REFRESH_STATUS_CODE" == "200" ]] || fail "expected HTTP 200 rotating the refresh token, got ${REFRESH_STATUS_CODE}: ${REFRESH_BODY}"
NEW_ACCESS_TOKEN=$(echo "$REFRESH_BODY" | jq -r '.accessToken')
NEW_REFRESH_TOKEN=$(echo "$REFRESH_BODY" | jq -r '.refreshToken')
[[ "$NEW_REFRESH_TOKEN" != "$MERCHANT_REFRESH_TOKEN" ]] || fail "rotation returned the same refresh token"
pass "refresh rotated into a new access/refresh token pair"

REUSE_STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${GATEWAY_URL}/auth/refresh" \
  -H "Content-Type: application/json" -d '{"refreshToken":"'"${MERCHANT_REFRESH_TOKEN}"'"}')
[[ "$REUSE_STATUS_CODE" == "401" ]] || fail "expected HTTP 401 reusing an already-rotated refresh token, got ${REUSE_STATUS_CODE}"
pass "reusing the old (rotated-away) refresh token correctly returns 401"

# ---------------------------------------------------------------------------
# Test 3 — Refresh Token Revocation (Logout)
# ---------------------------------------------------------------------------
step "Test 3 — Refresh Token Revocation (Logout)"
REVOKE_RESPONSE=$(curl -s -X POST "${GATEWAY_URL}/auth/revoke" \
  -H "Content-Type: application/json" -d '{"refreshToken":"'"${NEW_REFRESH_TOKEN}"'"}')
REVOKE_STATUS=$(echo "$REVOKE_RESPONSE" | jq -r '.status')
[[ "$REVOKE_STATUS" == "REVOKED" ]] || fail "expected {\"status\":\"REVOKED\"} from /auth/revoke, got: ${REVOKE_RESPONSE}"
pass "refresh token revoked"

POST_REVOKE_STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${GATEWAY_URL}/auth/refresh" \
  -H "Content-Type: application/json" -d '{"refreshToken":"'"${NEW_REFRESH_TOKEN}"'"}')
[[ "$POST_REVOKE_STATUS_CODE" == "401" ]] || fail "expected HTTP 401 refreshing with a revoked token, got ${POST_REVOKE_STATUS_CODE}"
pass "refreshing with a revoked token correctly returns 401"

# ---------------------------------------------------------------------------
# Test 4 — RBAC Enforcement (401 & 403)
# ---------------------------------------------------------------------------
step "Test 4 — RBAC Enforcement (401 & 403)"
NO_TOKEN_STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY_URL}/operator/ledger/integrity")
[[ "$NO_TOKEN_STATUS_CODE" == "401" ]] || fail "expected HTTP 401 with no token, got ${NO_TOKEN_STATUS_CODE}"
pass "operator route with no token correctly returns 401"

MERCHANT_FORBIDDEN_STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY_URL}/operator/ledger/integrity" \
  -H "Authorization: Bearer ${MERCHANT_ACCESS_TOKEN}")
[[ "$MERCHANT_FORBIDDEN_STATUS_CODE" == "403" ]] || fail "expected HTTP 403 with a merchant token, got ${MERCHANT_FORBIDDEN_STATUS_CODE}"
pass "operator route with a merchant token correctly returns 403"

# ---------------------------------------------------------------------------
# Test 5 — Operator Access (200)
# ---------------------------------------------------------------------------
step "Test 5 — Operator Access (200)"
OPERATOR_TOKEN_RESPONSE=$(issue_token "OPERATOR")
OPERATOR_ACCESS_TOKEN=$(echo "$OPERATOR_TOKEN_RESPONSE" | jq -r '.accessToken')
[[ -n "$OPERATOR_ACCESS_TOKEN" && "$OPERATOR_ACCESS_TOKEN" != "null" ]] || fail "no accessToken in operator /auth/token response: ${OPERATOR_TOKEN_RESPONSE}"
pass "issued operator accessToken"

INTEGRITY_RESPONSE=$(curl -s -w '\n%{http_code}' "${GATEWAY_URL}/operator/ledger/integrity" \
  -H "Authorization: Bearer ${OPERATOR_ACCESS_TOKEN}")
INTEGRITY_STATUS_CODE=$(echo "$INTEGRITY_RESPONSE" | tail -n1)
INTEGRITY_BODY=$(echo "$INTEGRITY_RESPONSE" | sed '$d')
[[ "$INTEGRITY_STATUS_CODE" == "200" ]] || fail "expected HTTP 200 from operator ledger-integrity, got ${INTEGRITY_STATUS_CODE}: ${INTEGRITY_BODY}"
INTEGRITY_BALANCED=$(echo "$INTEGRITY_BODY" | jq -r '.balanced')
[[ "$INTEGRITY_BALANCED" == "true" ]] || fail "expected ledger integrity balanced=true, got: ${INTEGRITY_BODY}"
pass "operator token granted access — ledger reports balanced=true"

# ---------------------------------------------------------------------------
# Test 6 — Dynamic Provider Routing (BR-13)
# ---------------------------------------------------------------------------
step "Test 6 — Dynamic Provider Routing (BR-13)"
PROVIDERS_RESPONSE=$(curl -sf "${GATEWAY_URL}/operator/providers" -H "Authorization: Bearer ${OPERATOR_ACCESS_TOKEN}")
PROVIDERS_COUNT=$(echo "$PROVIDERS_RESPONSE" | jq 'length')
[[ "$PROVIDERS_COUNT" -gt 0 ]] || fail "expected a non-empty provider list, got: ${PROVIDERS_RESPONSE}"
pass "provider list is non-empty (${PROVIDERS_COUNT} providers)"

ROUTING_RESPONSE=$(curl -s -w '\n%{http_code}' -X PATCH "${GATEWAY_URL}/operator/providers/mock-primary/routing" \
  -H "Authorization: Bearer ${OPERATOR_ACCESS_TOKEN}" -H "Content-Type: application/json" \
  -d '{"priority":5,"active":true}')
ROUTING_STATUS_CODE=$(echo "$ROUTING_RESPONSE" | tail -n1)
ROUTING_BODY=$(echo "$ROUTING_RESPONSE" | sed '$d')
[[ "$ROUTING_STATUS_CODE" == "200" ]] || fail "expected HTTP 200 updating provider routing, got ${ROUTING_STATUS_CODE}: ${ROUTING_BODY}"
UPDATED_PRIORITY=$(echo "$ROUTING_BODY" | jq -r '.priority')
[[ "$UPDATED_PRIORITY" == "5" ]] || fail "expected mock-primary priority updated to 5, got: ${ROUTING_BODY}"
pass "mock-primary routing priority updated to 5"

# ---------------------------------------------------------------------------
# Test 7 — Operator Saga Events (BR-14)
# ---------------------------------------------------------------------------
step "Test 7 — Operator Saga Events (BR-14)"
SAGA_EVENTS_STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY_URL}/operator/saga-events" \
  -H "Authorization: Bearer ${OPERATOR_ACCESS_TOKEN}")
[[ "$SAGA_EVENTS_STATUS_CODE" == "200" ]] || fail "expected HTTP 200 from operator saga-events, got ${SAGA_EVENTS_STATUS_CODE}"
pass "operator saga-events reachable through the gateway"

# ---------------------------------------------------------------------------
# Test 8 — Webhook DLQ Management
# ---------------------------------------------------------------------------
step "Test 8 — Webhook DLQ Management"
DLQ_STATUS_CODE=$(curl -s -o /dev/null -w '%{http_code}' "${GATEWAY_URL}/operator/webhooks/dlq" \
  -H "Authorization: Bearer ${OPERATOR_ACCESS_TOKEN}")
[[ "$DLQ_STATUS_CODE" == "200" ]] || fail "expected HTTP 200 from operator webhooks/dlq, got ${DLQ_STATUS_CODE}"
pass "operator webhooks/dlq reachable through the gateway"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo -e "\n${GREEN}${BOLD}[PASSED] Milestone 4 Security, RBAC & Observability Verified${RESET}"
