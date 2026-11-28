#!/usr/bin/env bash
set -euo pipefail

: "${SUPABASE_URL:?Set SUPABASE_URL}"
: "${SUPABASE_PUBLISHABLE_KEY:?Set SUPABASE_PUBLISHABLE_KEY}"
: "${TEST_SCHOOL_ID:?Set the isolated staging school ID (not the production tenant ID)}"
: "${TEST_SENTINEL_STUDENT_ID:?Set a pre-seeded, non-sensitive staging student ID}"
: "${TEST_SENTINEL_GATE_LOG_ID:?Set a pre-seeded staging gate-log ID}"
: "${TEST_SENTINEL_MEAL_ID:?Set a pre-seeded staging meal ID}"
: "${TEST_SENTINEL_ATTENDANCE_EVENT_ID:?Set a pre-seeded staging attendance record ID}"
: "${TEST_SENTINEL_EXAM_EVENT_ID:?Set a pre-seeded staging exam exception ID}"

api="${SUPABASE_URL%/}"
tmp="$(mktemp -d)"
storage_probe_path="${TEST_SCHOOL_ID}/ci-storage-probe-${GITHUB_RUN_ID:-local}/photo.jpg"
admin_token=""

cleanup() {
  if [[ -n "$admin_token" ]]; then
    curl --silent --show-error --output /dev/null \
      -X DELETE "$api/storage/v1/object/student-photos" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
      -H "Authorization: Bearer $admin_token" \
      -H 'Content-Type: application/json' \
      --data "$(jq -n --arg path "$storage_probe_path" '{prefixes:[$path]}')" || true
  fi
  rm -rf "$tmp"
}
trap cleanup EXIT

diagnostic_body() {
  local file="$1"
  if jq -e . "$file" >/dev/null 2>&1; then
    jq -c 'walk(if type == "object" then with_entries(if (.key | test("token|password|email|phone|authorization"; "i")) then .value = "[REDACTED]" else . end) else . end)' "$file"
  else
    echo "[non-JSON response body omitted]"
  fi
}

meal_staff_step() {
  if [[ "${1:-}" == "MEAL_STAFF" ]]; then
    echo "DIAG: MEAL_STAFF check: $2"
  fi
}

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
  meal_staff_step "$role" "membership lookup (GET /rest/v1/staff_memberships)"
  code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
    "$api/rest/v1/staff_memberships?select=role,is_active,has_finance_access&school_id=eq.$TEST_SCHOOL_ID&user_id=eq.$uid" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
  [[ "$code" == "200" ]] || { echo "::error::$role membership lookup returned HTTP $code; response=$(diagnostic_body "$response")"; exit 1; }
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
    "$api/rest/v1/staff_memberships?select=user_id&school_id=neq.$TEST_SCHOOL_ID&user_id=eq.$uid" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
  [[ "$code" == "200" ]] && [[ "$(jq 'length' "$tmp/$role-other-school.json")" == "0" ]] || {
    echo "::error::$role token crossed the school-tenant boundary."
    exit 1
  }
}

check_row_visibility() {
  local role="$1" token="$2" table="$3" column="$4" sentinel="$5" expected="$6"
  local response="$tmp/$role-$table.json" code count
  code="$(curl --silent --show-error --get --output "$response" --write-out '%{http_code}' \
    "$api/rest/v1/$table" \
    --data-urlencode "select=$column" \
    --data-urlencode "school_id=eq.$TEST_SCHOOL_ID" \
    --data-urlencode "$column=eq.$sentinel" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
  [[ "$code" == "200" ]] || { echo "::error::$role $table SELECT returned HTTP $code; response=$(diagnostic_body "$response")"; exit 1; }
  count="$(jq 'length' "$response")"
  [[ "$count" == "$expected" ]] || {
    echo "::error::$role expected $expected visible sentinel row(s) in $table, got $count; response=$(diagnostic_body "$response")"
    exit 1
  }
}

check_role_data_boundaries() {
  local role="$1" token="$2"
  local private_expected=0 gate_expected=0 meal_expected=0 academic_expected=0 exam_expected=0
  case "$role" in
    ADMIN|HEAD_TEACHER)
      private_expected=1; gate_expected=1; meal_expected=1; academic_expected=1; exam_expected=1 ;;
    GATE_OFFICER)
      gate_expected=1 ;;
    MEAL_STAFF)
      meal_expected=1 ;;
  esac
  meal_staff_step "$role" "row visibility: student_private"
  check_row_visibility "$role" "$token" student_private student_id "$TEST_SENTINEL_STUDENT_ID" "$private_expected"
  meal_staff_step "$role" "row visibility: gate_logs"
  check_row_visibility "$role" "$token" gate_logs id "$TEST_SENTINEL_GATE_LOG_ID" "$gate_expected"
  meal_staff_step "$role" "row visibility: meals"
  check_row_visibility "$role" "$token" meals id "$TEST_SENTINEL_MEAL_ID" "$meal_expected"
  meal_staff_step "$role" "row visibility: attendance_record_events"
  check_row_visibility "$role" "$token" attendance_record_events event_id "$TEST_SENTINEL_ATTENDANCE_EVENT_ID" "$academic_expected"
  meal_staff_step "$role" "row visibility: exam_exception_events"
  check_row_visibility "$role" "$token" exam_exception_events event_id "$TEST_SENTINEL_EXAM_EVENT_ID" "$exam_expected"
}

check_rpc_role() {
  local role="$1" token="$2" rpc="$3" allowed="$4" payload="$5"
  local response="$tmp/$role-$rpc.json" code
  meal_staff_step "$role" "RPC authorization: $rpc"
  code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
    -X POST "$api/rest/v1/rpc/$rpc" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    --data "$payload")"
  if [[ "$allowed" == "true" ]]; then
    [[ "$code" == "200" ]] && [[ "$(jq -r 'tostring' "$response")" == "false" ]] || {
      echo "::error::$role should be allowed to call $rpc idempotently against its existing sentinel row (HTTP $code)."
      echo "Response: $(diagnostic_body "$response")"
      exit 1
    }
  else
    [[ "$code" == "401" || "$code" == "403" ]] || {
      echo "::error::$role must be denied $rpc by RLS (HTTP $code)."
      cat "$response"
      exit 1
    }
  fi
}

check_rpc_boundaries() {
  local role="$1" token="$2" gate_allowed=false meal_allowed=false
  case "$role" in
    ADMIN) gate_allowed=true; meal_allowed=true ;;
    GATE_OFFICER) gate_allowed=true ;;
    MEAL_STAFF) meal_allowed=true ;;
  esac
  local gate_payload meal_payload
  gate_payload="$(jq -n --arg school "$TEST_SCHOOL_ID" --arg id "$TEST_SENTINEL_GATE_LOG_ID" \
    '{p_row:{school_id:$school,id:$id,student_number:"AUTHZ-PROBE",student_name:"Synthetic",decision:"APPROVED",gate_location:"CI sentinel",timestamp_ms:1}}')"
  meal_payload="$(jq -n --arg school "$TEST_SCHOOL_ID" --arg id "$TEST_SENTINEL_MEAL_ID" --arg student "$TEST_SENTINEL_STUDENT_ID" \
    '{p_row:{school_id:$school,id:$id,student_id:$student,student_number:"AUTHZ-PROBE",student_name:"Synthetic",grade_class:"CI",meal_type:"LUNCH",meal_date:"2000-01-01",status:"SERVED",notes:"",serving_station:"CI sentinel",timestamp_ms:1}}')"
  check_rpc_role "$role" "$token" scholink_insert_gate_log_once "$gate_allowed" "$gate_payload"
  check_rpc_role "$role" "$token" scholink_insert_meal_once "$meal_allowed" "$meal_payload"
}

check_storage_boundary() {
  local role="$1" token="$2" code response="$tmp/$role-storage.json"
  local denied_path="${TEST_SCHOOL_ID}/ci-storage-denied-${role}-${GITHUB_RUN_ID:-local}/photo.jpg"
  meal_staff_step "$role" "private photo storage boundary"

  if [[ "$role" == "ADMIN" ]]; then
    admin_token="$token"
    # A tiny synthetic PNG; unique CI path, removed by the EXIT trap.
    printf '%s' 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jWZkAAAAASUVORK5CYII=' | base64 -d > "$tmp/photo.png"
    code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
      -X POST "$api/storage/v1/object/student-photos/$storage_probe_path" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token" \
      -H 'Content-Type: image/png' --data-binary @"$tmp/photo.png")"
    [[ "$code" == "200" ]] || { echo "::error::Admin student-photo upload failed (HTTP $code)."; echo "Response: $(diagnostic_body "$response")"; exit 1; }

    code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
      -X POST "$api/storage/v1/object/student-photos/$storage_probe_path" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token" \
      -H 'Content-Type: image/png' -H 'x-upsert: true' --data-binary @"$tmp/photo.png")"
    [[ "$code" == "200" ]] || { echo "::error::Admin student-photo replacement failed (HTTP $code)."; cat "$response"; exit 1; }
  fi

  code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
    "$api/storage/v1/object/authenticated/student-photos/$storage_probe_path" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
  [[ "$code" == "200" ]] || { echo "::error::$role could not download the private student-photo sentinel (HTTP $code)."; cat "$response"; exit 1; }

  if [[ "$role" != "ADMIN" ]]; then
    code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
      -X POST "$api/storage/v1/object/student-photos/$storage_probe_path" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token" \
      -H 'Content-Type: image/png' -H 'x-upsert: true' --data-binary @"$tmp/photo.png")"
    [[ "$code" == "400" || "$code" == "403" ]] || {
      echo "::error::$role unexpectedly replaced an Admin-owned student photo (HTTP $code)."; cat "$response"; exit 1;
    }

    code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
      -X DELETE "$api/storage/v1/object/student-photos" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' \
      --data "$(jq -n --arg path "$storage_probe_path" '{prefixes:[$path]}')")"
    # Storage may return HTTP 200 with an empty result when RLS filters the
    # DELETE target. Verify the object remains downloadable instead of trusting
    # the status code alone.
    code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
      "$api/storage/v1/object/authenticated/student-photos/$storage_probe_path" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token")"
    [[ "$code" == "200" ]] || {
      echo "::error::$role deleted or lost access to an Admin-owned student photo after a denied DELETE (HTTP $code)."; cat "$response"; exit 1;
    }

    code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
      -X POST "$api/storage/v1/object/student-photos/$denied_path" \
      -H "apikey: $SUPABASE_PUBLISHABLE_KEY" -H "Authorization: Bearer $token" \
      -H 'Content-Type: image/png' --data-binary @"$tmp/photo.png")"
    [[ "$code" == "400" || "$code" == "403" ]] || {
      echo "::error::$role unexpectedly uploaded a student photo (HTTP $code)."; cat "$response"; exit 1;
    }
  fi

  echo "PASS: $role private student-photo read and Admin-only write boundary"
}

check_finance_boundary() {
  local role="$1" token="$2" code
  meal_staff_step "$role" "finance write boundary (POST /rest/v1/student_finances)"
  # A deliberately nonexistent student_id ensures that even the authorized Admin
  # cannot create a persistent test row. Admin should reach the FK check (409);
  # roles without finance capability must be rejected by RLS (401/403).
  code="$(curl --silent --show-error --output "$tmp/$role-finance-write.json" --write-out '%{http_code}' \
    -X POST "$api/rest/v1/student_finances" \
    -H "apikey: $SUPABASE_PUBLISHABLE_KEY" \
    -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' \
    -H 'Prefer: return=minimal' \
    --data "$(jq -n --arg school "$TEST_SCHOOL_ID" '{school_id:$school,student_id:"__ltc_authz_probe_nonexistent__",outstanding_amount:1,fee_status:"OUTSTANDING",updated_at_ms:1}')")"

  if [[ "$role" == "ADMIN" ]]; then
    [[ "$code" == "409" ]] || {
      echo "::error::Finance-enabled Admin expected FK rejection (HTTP 409), got HTTP $code."
      echo "Response: $(diagnostic_body "$tmp/$role-finance-write.json")"
      exit 1
    }
  else
    [[ "$code" == "401" || "$code" == "403" ]] || {
      echo "::error::$role should be denied finance writes by RLS (HTTP 401/403), got HTTP $code."
      echo "Response: $(diagnostic_body "$tmp/$role-finance-write.json")"
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
  check_role_data_boundaries "$expected" "$token"
  check_rpc_boundaries "$expected" "$token"
  check_finance_boundary "$expected" "$token"
  check_storage_boundary "$expected" "$token"
  echo "PASS: $expected real Auth token, active staging membership, cross-school denial and finance boundary"
}

run_role ADMIN ADMIN "${TEST_ADMIN_EMAIL:?}" "${TEST_ADMIN_PASSWORD:?}"
run_role HEAD_TEACHER HEAD_TEACHER "${TEST_HEAD_TEACHER_EMAIL:?}" "${TEST_HEAD_TEACHER_PASSWORD:?}"
run_role GATE_OFFICER GATE_OFFICER "${TEST_GATE_OFFICER_EMAIL:?}" "${TEST_GATE_OFFICER_PASSWORD:?}"
run_role MEAL_STAFF MEAL_STAFF "${TEST_MEAL_STAFF_EMAIL:?}" "${TEST_MEAL_STAFF_PASSWORD:?}"
