#!/usr/bin/env bash
# 배포 전 .env 사전 검사.
#
# 왜 필요한가 (2026-09-08에 실제로 겪은 문제):
#   1) `SSAFY_FINANCE_API_KEY=`가 파일에 두 번 있었다. docker compose는 나중 정의가 이기므로,
#      앞줄에 값을 채워도 뒷줄의 빈 값이 덮어써서 "분명히 넣었는데 안 먹는" 상황이 된다.
#   2) `DATA_ENCRYPTION_KEY=   # openssl rand -base64 32` 처럼 안내 문구가 그대로 붙어 있었다.
#      `#` 뒤는 주석으로 잘려 값이 비게 되는데, 겉보기엔 채워진 것처럼 보인다.
#   두 경우 모두 백엔드가 기동 시점에 죽는 것으로만 드러나서 원인 추적에 시간이 걸린다.
#   여기서 먼저 걸러 "무엇이 왜 잘못됐는지"를 배포 로그에 남긴다.
#
# 필수 변수 목록은 하드코딩하지 않고 docker-compose.yml이 참조하는 ${VAR}에서 뽑는다.
# 그래야 백엔드가 새 변수를 요구하기 시작할 때 이 검사도 자동으로 따라간다.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$APP_DIR/infra/docker-compose.yml}"

log()  { echo "[check-env] $*"; }
ERRORS=0
err()  { echo "[check-env] ERROR: $*"; ERRORS=$((ERRORS+1)); }
warn() { echo "[check-env] WARN: $*"; }

[ -f "$ENV_FILE" ]     || { err "$ENV_FILE 없음"; exit 1; }
[ -f "$COMPOSE_FILE" ] || { err "$COMPOSE_FILE 없음"; exit 1; }

# 1) 중복 키
DUPES="$(grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$ENV_FILE" | sort | uniq -d | tr -d '=' || true)"
if [ -n "$DUPES" ]; then
    while read -r k; do
        [ -n "$k" ] && err "중복 정의: $k — compose는 마지막 값을 쓰므로 앞의 값이 무시된다"
    done <<< "$DUPES"
fi

# 2) 값 뒤에 붙은 인라인 주석 (값이 잘려 비어버리는 흔한 실수)
while IFS= read -r line; do
    [ -n "$line" ] && err "인라인 주석 때문에 값이 잘림: ${line%%=*} — 주석은 별도 줄로 옮길 것"
done < <(grep -nE '^[A-Za-z_][A-Za-z0-9_]*=[^#]*#' "$ENV_FILE" || true)

# 3) compose가 요구하는 변수가 실제로 채워져 있는지
#    ${VAR} 형태만 검사하고 ${VAR:-기본값}은 기본값이 있으므로 제외한다.
REQUIRED="$(grep -oE '\$\{[A-Za-z_][A-Za-z0-9_]*\}' "$COMPOSE_FILE" \
            | tr -d '${}' | sort -u || true)"

# .env를 서브셸에서 읽어 현재 환경을 오염시키지 않는다.
for var in $REQUIRED; do
    val="$(
        set -a
        # shellcheck disable=SC1090
        . "$ENV_FILE" >/dev/null 2>&1 || true
        set +a
        printf '%s' "${!var-}"
    )"
    if [ -z "$val" ]; then
        err "$var 가 비어 있음 — docker-compose.yml이 이 값을 요구한다"
    fi
done

# 3-1) 앱이 요구하는 변수를 compose가 실제로 전달하는지
#
# 왜 필요한가 (2026-09-08 장애):
#   Auth MR 병합 과정에서 docker-compose.yml의 JWT_SECRET 등 4줄이 유실됐다.
#   위 3)번 검사는 필수 변수 목록을 compose에서 뽑기 때문에, compose가 참조 자체를
#   잃어버리면 "요구하는 변수가 없다"고 판단해 통과시켜 버린다.
#   결국 백엔드만 기동에 실패하고(`app.auth.jwt.secret must be at least 32 bytes`)
#   원인 파악에 시간이 걸렸다.
#   그래서 "앱이 뭘 필요로 하는가"를 별도 출처(application-prod.yml)에서 읽어 대조한다.
APP_CONFIG="${APP_CONFIG:-$APP_DIR/backend/src/main/resources/application-prod.yml}"
if [ -f "$APP_CONFIG" ]; then
    APP_VARS="$(grep -oE '\$\{[A-Z_][A-Z0-9_]*\}' "$APP_CONFIG" | tr -d '${}' | sort -u || true)"
    for var in $APP_VARS; do
        # compose가 "값을 어떻게 만드는지"는 상관없다 (리터럴이든 ${...} 조합이든).
        # 중요한 것은 그 이름의 환경변수 키를 backend에 전달하고 있는지다.
        #   예) DB_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?...  ← 키는 DB_URL
        #       REDIS_HOST: redis                                      ← 리터럴이지만 키는 존재
        if ! grep -qE "^[[:space:]]+${var}:" "$COMPOSE_FILE" \
           && ! grep -qE "^[[:space:]]+- ${var}=" "$COMPOSE_FILE"; then
            err "$var 를 application-prod.yml이 요구하는데 docker-compose.yml이 전달하지 않는다 (병합 중 유실 의심)"
        fi
    done
else
    warn "application-prod.yml을 찾을 수 없어 앱↔compose 대조를 건너뜀 ($APP_CONFIG)"
fi

# 4) 암호화 키 형식 (AES-256 = 디코드 시 32바이트)
for var in DATA_ENCRYPTION_KEY DATA_HASH_KEY; do
    val="$(
        set -a
        # shellcheck disable=SC1090
        . "$ENV_FILE" >/dev/null 2>&1 || true
        set +a
        printf '%s' "${!var-}"
    )"
    [ -z "$val" ] && continue
    n="$(printf '%s' "$val" | base64 -d 2>/dev/null | wc -c || echo 0)"
    [ "$n" = "32" ] || warn "$var 가 base64 32바이트가 아님(${n}바이트) — AES-256 키로 의도한 값인지 확인"
done

# 5) 권한
PERM="$(stat -c '%a' "$ENV_FILE")"
[ "$PERM" = "600" ] || warn ".env 권한이 $PERM — 600 권장"

if [ "$ERRORS" -gt 0 ]; then
    log "검사 실패 ($ERRORS건). 위 항목을 고친 뒤 다시 배포하세요."
    exit 1
fi
log "통과 (필수 변수 $(echo "$REQUIRED" | wc -w)개 확인)"
