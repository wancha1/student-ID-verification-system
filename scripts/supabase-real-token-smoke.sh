#!/usr/bin/env bash
set -euo pipefail

: "${SUPABASE_URL:?Set SUPABASE_URL}"
: "${SUPABASE_PUBLISHABLE_KEY:?Set SUPABASE_PUBLISHABLE_KEY}"

api="${SUPABASE_URL%/}"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

login() {
  local role="$1" email="$2" password="$3"
  local response="$tmp/$role-login.json"
  local code
  code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
    -X POST "$api/auth/v1/token?grant_type=password" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
    -H 'Content-Type: application/json' \
    --data "$(jq -n --arg email "$email" --arg password "$password" '{email:$email,password:$password}')")"
  if [[ "$code" != "200" ]]; then
    echo "::error::$role Supabase password sign-in failed (HTTP $code). Check the protected test-account secrets."
    exit 1
  fi
  local token uid
  token="$(jq -r '.access_token // empty' "$response")"
  uid="$(jq -r '.user.id // empty' "$response")"
  [[ -n "$token" && -n "$uid" ]] || { echo "::error::$role sign-in response did not contain a token and user ID."; exit 1; }
  printf '%s\n%s\n' "$token" "$uid"
}

check_membership() {
  local role="$1" token="$2" uid="$3" expected="$4" expected_finance="$5"
  local response="$tmp/$role-membership.json" code count actual active finance
  code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
    "$api/rest/v1/staff_memberships?select=role,is_active,has_finance_access&school_id=eq.ltc_school_01&user_id=eq.$uid" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
  [[ "$code" == "200" ]] || { echo "::error::$role membership lookup returned HTTP $code."; exit 1; }
  count="$(jq 'length' "$response")"
  actual="$(jq -r '.[0].role // empty' "$response")"
  active="$(jq -r '.[0].is_active // false' "$response")"
  finance="$(jq -r '.[0].has_finance_access // false' "$response")"
  [[ "$count" == "1" && "$actual" == "$expected" && "$active" == "true" && "$finance" == "$expected_finance" ]] || {
    echo "::error::$role token did not resolve to exactly one active $expected membership with finance_access=$expected_finance."
    exit 1
  }

  # A valid token must not reveal a membership in a different school tenant.
  code="$(curl --silent --show-error --output "$tmp/$role-other-school.json" --write-out '%{http_code}' \
    "$api/rest/v1/staff_memberships?select=user_id&school_id=neq.ltc_school_01&user_id=eq.$uid" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
  [[ "$code" == "200" ]] && [[ "$(jq 'length' "$tmp/$role-other-school.json")" == "0" ]] || {
    echo "::error::$role token crossed the school-tenant boundary."
    exit 1
  }
}

check_finance_boundary() {
  local role="$1" token="$2" code
  # A deliberately nonexistent student_id ensures that even the authorized Admin
  # cannot create a persistent test row. Admin should reach the FK check (409);
  # roles without finance capability must be rejected by RLS (401/403).
  code="$(curl --silent --show-error --output "$tmp/$role-finance-write.json" --write-out '%{http_code}' \
    -X POST "$api/rest/v1/student_finances" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -H 'Prefer: return=minimal' \
    --data '{"school_id":"ltc_school_01","student_id":"__ltc_authz_probe_nonexistent__","outstanding_amount":1,"fee_status":"OUTSTANDING","updated_at_ms":1}')"

  if [[ "$role" == "ADMIN" ]]; then
    [[ "$code" == "409" ]] || {
      echo "::error::Finance-enabled Admin expected FK rejection (HTTP 409), got HTTP $code."
      cat "$tmp/$role-finance-write.json"
      exit 1
    }
  else
    [[ "$code" == "401" || "$code" == "403" ]] || {
      echo "::error::$role should be denied finance writes by RLS (HTTP 401/403), got HTTP $code."
      cat "$tmp/$role-finance-write.json"
      exit 1
    }
  fi
}

run_role() {
  local role="$1" expected="$2" email="$3" password="$4"
  local data token uid
  data="$(login "$role" "$email" "$password")"
  token="$(printf '%s\n' "$data" | sed -n '1p')"
  uid="$(printf '%s\n' "$data" | sed -n '2p')"
  local expected_finance=false
  [[ "$expected" == "ADMIN" ]] && expected_finance=true
  check_membership "$role" "$token" "$uid" "$expected" "$expected_finance"
  check_finance_boundary "$expected" "$token"
  echo "PASS: $expected real Auth token, active LTC membership, cross-school denial and finance boundary"
}

run_role ADMIN ADMIN "${TEST_ADMIN_EMAIL:?}" "${TEST_ADMIN_PASSWORD:?}"
run_role HEAD_TEACHER HEAD_TEACHER "${TEST_HEAD_TEACHER_EMAIL:?}" "${TEST_HEAD_TEACHER_PASSWORD:?}"
run_role GATE_OFFICER GATE_OFFICER "${TEST_GATE_OFFICER_EMAIL:?}" "${TEST_GATE_OFFICER_PASSWORD:?}"
run_role MEAL_STAFF MEAL_STAFF "${TEST_MEAL_STAFF_EMAIL:?}" "${TEST_MEAL_STAFF_PASSWORD:?}"
