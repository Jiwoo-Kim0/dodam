#!/usr/bin/env bash
# 컨테이너 이상 감지 → Mattermost 알림. cron에서 5분마다 실행한다.
#
# 왜 cAdvisor 대신 이 방식인가:
#   cAdvisor(v0.49/v0.52 모두)가 Docker 29의 overlayfs 스토리지 드라이버를 지원하지 않아
#   "failed to identify the read-write layer ID"로 컨테이너 객체 생성에 실패한다.
#   결과적으로 name 라벨이 붙은 컨테이너별 메트릭이 하나도 나오지 않는다.
#   Prometheus의 up 알림은 "수집 대상"만 보므로 mysql/redis/backend처럼
#   스크레이프 대상이 아닌 컨테이너의 이상은 잡지 못한다.
#
#   그래서 docker 자체에 직접 물어본다. 의존성이 적고 확실하다.
#
# 감지 대상
#   - 실행 중이어야 하는데 멈춘 컨테이너
#   - health check가 unhealthy인 컨테이너
#   - 재시작이 반복되는 컨테이너 (크래시 루프)
set -uo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
STATE_FILE="${STATE_FILE:-/home/ubuntu/.container-watch.state}"
# 이 컨테이너들은 항상 떠 있어야 한다.
EXPECTED="${EXPECTED:-nginx jenkins certbot mysql redis backend prometheus node-exporter loki promtail grafana}"
RESTART_THRESHOLD="${RESTART_THRESHOLD:-3}"

problems=""

for c in $EXPECTED; do
    if ! docker inspect "$c" >/dev/null 2>&1; then
        problems="${problems}- \`${c}\`: 컨테이너가 존재하지 않음\n"
        continue
    fi
    status="$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}-{{end}}' "$c" 2>/dev/null)"
    restarts="$(docker inspect -f '{{.RestartCount}}' "$c" 2>/dev/null)"

    if [ "$status" != "running" ]; then
        problems="${problems}- \`${c}\`: 상태 ${status}\n"
    elif [ "$health" = "unhealthy" ]; then
        problems="${problems}- \`${c}\`: health check 실패(unhealthy)\n"
    fi

    # 재시작 횟수가 직전 확인 이후 임계치 이상 늘었으면 크래시 루프로 본다.
    prev="$(grep -E "^${c}=" "$STATE_FILE" 2>/dev/null | cut -d= -f2)"
    if [ -n "$prev" ] && [ -n "$restarts" ]; then
        delta=$(( restarts - prev ))
        if [ "$delta" -ge "$RESTART_THRESHOLD" ]; then
            problems="${problems}- \`${c}\`: 최근 5분간 ${delta}회 재시작 (크래시 루프 의심)\n"
        fi
    fi
done

# 현재 재시작 횟수를 기록해 다음 실행과 비교한다.
: > "$STATE_FILE"
for c in $EXPECTED; do
    r="$(docker inspect -f '{{.RestartCount}}' "$c" 2>/dev/null)" || continue
    echo "${c}=${r}" >> "$STATE_FILE"
done
chmod 600 "$STATE_FILE" 2>/dev/null || true

[ -z "$problems" ] && exit 0

# 알림 전송
[ -f "$ENV_FILE" ] || { echo "[container-watch] $ENV_FILE 없음"; exit 1; }
WEBHOOK="$(grep -E '^MATTERMOST_WEBHOOK_URL=' "$ENV_FILE" | cut -d= -f2-)"
if [ -z "$WEBHOOK" ]; then
    echo "[container-watch] webhook 미설정 — 알림 생략"
    printf "%b" "$problems"
    exit 1
fi

TEXT=":warning: **컨테이너 이상 감지** — 도담 인프라\n${problems}\n확인: \`ssh ssafy-ec2\` 후 \`docker ps -a\`"
python3 - "$WEBHOOK" "$TEXT" <<'PY'
import json, sys, urllib.request
url, text = sys.argv[1], sys.argv[2].replace("\\n", "\n")
req = urllib.request.Request(
    url,
    data=json.dumps({"text": text}).encode(),
    headers={"Content-Type": "application/json"},
)
try:
    urllib.request.urlopen(req, timeout=10).read()
    print("[container-watch] 알림 전송 완료")
except Exception as e:
    print("[container-watch] 알림 전송 실패:", e)
    sys.exit(1)
PY
