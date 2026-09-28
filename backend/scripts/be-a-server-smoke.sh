#!/usr/bin/env bash
# BE-A 담당 도메인 서버 스모크 (S15P21D105-521)
#
# 시연 서버(또는 임의 BASE_URL)에 시연 계정으로 로그인한 뒤 BE-A 8개 API를 한 번씩 호출해 HTTP 상태 코드를 표로 남긴다.
# 서버 DB를 직접 읽거나 쓰지 않고 공개 API만 사용한다. 쓰기 호출은 로그인(세션 생성)과 --analyze를 줬을 때의 패턴 분석 실행만이다.
#
# 사용법:
#   BASE_URL=https://j15D105.p.ssafy.io/api DEMO_EMAIL=seed.kmeans.u0001@dodam.invalid DEMO_PASSWORD=... \
#     bash backend/scripts/be-a-server-smoke.sh [--analyze] [--year-month 2026-08]
#
# 선행 조건: 시연 계정에 비밀번호가 설정되어 있어야 한다(backend/database/seeds/DODAM_DEMO_PASSWORD_SEED.sql).
# 종료 코드: 기대 상태 코드와 다른 호출이 하나라도 있으면 1.
set -u

BASE_URL="${BASE_URL:-https://j15D105.p.ssafy.io/api}"
DEMO_EMAIL="${DEMO_EMAIL:-seed.kmeans.u0001@dodam.invalid}"
DEMO_PASSWORD="${DEMO_PASSWORD:-}"
YEAR_MONTH="2026-08"   # seed 거래 창(2026-03 ~ 2026-08)의 마지막 달
RUN_ANALYZE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --analyze) RUN_ANALYZE=1 ;;
    --year-month) shift; YEAR_MONTH="$1" ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

if [ -z "$DEMO_PASSWORD" ]; then
  echo "DEMO_PASSWORD 환경변수가 필요합니다." >&2
  exit 2
fi

FAIL=0
TOKEN=""
BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

# call <label> <expected-codes(공백 구분)> <method> <path> [json-body]
call() {
  local label="$1" expected="$2" method="$3" path="$4" body="${5:-}"
  local code
  if [ -n "$body" ]; then
    code=$(curl -s -m 30 -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path" \
      -H 'Content-Type: application/json' ${TOKEN:+-H "Authorization: Bearer $TOKEN"} -d "$body")
  else
    code=$(curl -s -m 30 -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path" \
      ${TOKEN:+-H "Authorization: Bearer $TOKEN"})
  fi
  local ok="FAIL"
  for e in $expected; do [ "$code" = "$e" ] && ok="ok"; done
  [ "$ok" = "ok" ] || FAIL=1
  local snippet
  snippet=$(tr -d '\n' < "$BODY_FILE" | cut -c1-140)
  printf '%-4s %-3s %-6s %-52s %s\n' "$ok" "$code" "$method" "$path" "$snippet"
}

echo "BASE_URL=$BASE_URL  계정=$DEMO_EMAIL  시각=$(date '+%Y-%m-%d %H:%M:%S %z')"
echo

# 1. 로그인 (Auth)
call "login" "200" POST "/v1/auth/login" \
  "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\"}"
TOKEN=$(sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p' "$BODY_FILE")
if [ -z "$TOKEN" ]; then
  echo "로그인 실패: accessToken을 받지 못했습니다. 비밀번호 seed가 서버에 적용되었는지 확인하세요." >&2
  exit 1
fi

# 2. 계좌 (Account)
call "accounts" "200" GET "/v1/accounts"
call "account-summary" "200" GET "/v1/accounts/summary"
# 3. 거래 (Transaction)
call "transactions" "200" GET "/v1/transactions?page=0&size=5"
# 4. 소비 (Spending)
call "spending" "200" GET "/v1/spending/monthly-summary?yearMonth=$YEAR_MONTH"
# 5. 목표 (Goal)
call "goals" "200" GET "/v1/goals?page=0&size=5"
call "goal-summary" "200" GET "/v1/goals/summary"
# 6. 정기결제 (Subscription)
call "subscriptions" "200" GET "/v1/subscriptions?page=0&size=5"
call "subscription-summary" "200" GET "/v1/subscriptions/summary"
# 7. 패턴 (Pattern) — 분석 이력이 없으면 404 PATTERN_ANALYSIS_NOT_FOUND가 정상이다. --analyze면 먼저 실행한다.
if [ "$RUN_ANALYZE" = "1" ]; then
  call "pattern-analyze" "200 201" POST "/v1/patterns/analyze" "{}"
  call "pattern-me" "200" GET "/v1/patterns/me"
else
  call "pattern-me" "200 404" GET "/v1/patterns/me"
fi
# 8. 알림 (Notification)
call "notifications" "200" GET "/v1/notifications?page=0&size=5"
call "unread-count" "200" GET "/v1/notifications/unread-count"
# 세션 확인·로그아웃
call "session" "200" GET "/v1/auth/session"
call "logout" "200 204" POST "/v1/auth/logout" "{}"

echo
if [ "$FAIL" = "0" ]; then echo "결과: 전부 기대 상태 코드"; else echo "결과: 기대와 다른 호출 있음"; fi
exit "$FAIL"
