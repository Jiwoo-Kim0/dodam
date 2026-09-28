#!/usr/bin/env bash
# cron 작업 래퍼 — 작업이 실패하면 Mattermost로 알린다.
#
# 왜 필요한가 (2026-09-15에 실제로 겪은 문제):
#   일일 백업이 실패했는데 그 사실이 `backup.log`에만 남고 아무도 몰랐다.
#   `container-watch.sh`는 컨테이너만 보기 때문에 cron 작업의 실패는 감지하지 못한다.
#   cron은 기본적으로 메일로 결과를 보내지만 이 서버에는 MTA가 없어 그대로 버려진다.
#
# 사용:
#   cron-notify.sh <작업이름> <실행할 명령...>
#   예) cron-notify.sh backup /home/ubuntu/app/infra/scripts/backup.sh
#
# 동작:
#   - 명령의 출력을 그대로 표준출력에 흘린다 (기존 로그 리다이렉션이 그대로 동작한다)
#   - 종료 코드가 0이 아니면 Mattermost로 작업 이름·종료 코드·출력 끝부분을 보낸다
#   - 성공하면 heartbeat 파일에 시각을 남긴다 → container-watch.sh가 "아예 실행되지
#     않음"(cron 자체가 죽은 경우)을 따로 잡는다. 실패 알림만으로는 그 경우를 못 잡는다
#   - 래퍼는 명령의 종료 코드를 그대로 반환한다
#
# ⚠️ 출력 끝부분이 Mattermost로 나간다. cron으로 도는 스크립트는 비밀번호·키를
#    출력하지 않아야 한다 (backup.sh는 MYSQL_PWD로 전달해 노출되지 않는다).
set -uo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
HEARTBEAT_DIR="${HEARTBEAT_DIR:-/home/ubuntu/.cron-heartbeat}"
TAIL_LINES="${TAIL_LINES:-20}"

[ $# -ge 2 ] || { echo "[cron-notify] 사용: $0 <작업이름> <명령...>"; exit 2; }
JOB="$1"; shift

OUT_FILE="$(mktemp)"
trap 'rm -f "$OUT_FILE"' EXIT

START="$(date '+%Y-%m-%d %H:%M:%S')"
"$@" > "$OUT_FILE" 2>&1
CODE=$?

# 출력은 그대로 흘려보낸다 — 기존 로그 파일에 남는 내용이 달라지지 않게.
cat "$OUT_FILE"

if [ "$CODE" -eq 0 ]; then
    mkdir -p "$HEARTBEAT_DIR" && chmod 700 "$HEARTBEAT_DIR"
    date +%s > "$HEARTBEAT_DIR/$JOB"
    exit 0
fi

echo "[cron-notify] '$JOB' 실패 (종료 코드 $CODE) — 알림 전송 시도"

[ -f "$ENV_FILE" ] || { echo "[cron-notify] $ENV_FILE 없음 — 알림 생략"; exit "$CODE"; }
WEBHOOK="$(grep -E '^MATTERMOST_WEBHOOK_URL=' "$ENV_FILE" | cut -d= -f2-)"
if [ -z "$WEBHOOK" ]; then
    echo "[cron-notify] webhook 미설정 — 알림 생략"
    exit "$CODE"
fi

# 출력은 파일에서 읽는다. 인자로 넘기면 길이 제한에 걸리고 프로세스 목록에도 남는다.
python3 - "$WEBHOOK" "$JOB" "$CODE" "$START" "$OUT_FILE" "$TAIL_LINES" <<'PY'
import json, sys, urllib.request

url, job, code, start, path, tail_n = sys.argv[1:7]
try:
    lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
except OSError:
    lines = []
tail = "\n".join(lines[-int(tail_n):]) or "(출력 없음)"

text = (
    f":rotating_light: **cron 작업 실패** — 도담 인프라\n"
    f"- 작업: `{job}`\n"
    f"- 종료 코드: `{code}`\n"
    f"- 시작 시각: {start}\n\n"
    f"출력 끝부분:\n```\n{tail}\n```"
)
req = urllib.request.Request(
    url,
    data=json.dumps({"text": text}).encode(),
    headers={"Content-Type": "application/json"},
)
try:
    urllib.request.urlopen(req, timeout=10).read()
    print("[cron-notify] 알림 전송 완료")
except Exception as e:
    print("[cron-notify] 알림 전송 실패:", e)
PY

exit "$CODE"
