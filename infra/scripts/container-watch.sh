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
#   - cron 작업이 너무 오래 성공하지 못한 경우 (2026-09-16 추가)
#     `cron-notify.sh`는 작업이 "실패"할 때 알린다. 그러나 cron 자체가 멈추거나
#     crontab에서 줄이 사라지면 작업이 아예 실행되지 않고, 그때는 실패 알림도 없다.
#     그래서 성공 시각(heartbeat)을 여기서 따로 본다.
set -uo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
STATE_FILE="${STATE_FILE:-/home/ubuntu/.container-watch.state}"
# 이 컨테이너들은 항상 떠 있어야 한다.
EXPECTED="${EXPECTED:-nginx jenkins certbot mysql redis backend prometheus node-exporter loki promtail grafana}"
RESTART_THRESHOLD="${RESTART_THRESHOLD:-3}"
# cron heartbeat — `cron-notify.sh`가 작업 성공 시 남긴다.
# 형식: 작업이름:허용지연(분). backup은 매일 04:00이므로 26시간(1560분)을 허용치로 둔다.
HEARTBEAT_DIR="${HEARTBEAT_DIR:-/home/ubuntu/.cron-heartbeat}"
# backup: 매일 04:00 → 26시간(1560분) / docker-gc: 매주 일 05:00 → 8일(11520분)
CRON_JOBS="${CRON_JOBS:-backup:1560 docker-gc:11520}"

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

# cron 작업이 최근에 성공했는지 확인한다.
now="$(date +%s)"
for spec in $CRON_JOBS; do
    job="${spec%%:*}"
    max_min="${spec##*:}"
    hb="$HEARTBEAT_DIR/$job"
    if [ ! -f "$hb" ]; then
        problems="${problems}- cron \`${job}\`: 성공 기록이 없음 (래퍼 미적용이거나 한 번도 성공하지 않음)\n"
        continue
    fi
    last="$(cat "$hb" 2>/dev/null || echo 0)"
    case "$last" in ''|*[!0-9]*) last=0 ;; esac
    age_min=$(( (now - last) / 60 ))
    if [ "$age_min" -gt "$max_min" ]; then
        problems="${problems}- cron \`${job}\`: 마지막 성공이 ${age_min}분 전 (허용 ${max_min}분 초과 — 실행 자체가 안 되고 있을 수 있음)\n"
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

TEXT=":warning: **인프라 이상 감지** — 도담\n${problems}\n확인: \`ssh ssafy-ec2\` 후 \`docker ps -a\` / \`tail ~/backups/backup.log\`"
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
