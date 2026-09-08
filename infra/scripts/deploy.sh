#!/usr/bin/env bash
# EC2 위의 배포용 체크아웃(APP_DIR)을 origin 기준으로 맞추고 스택을 재기동한다.
# Jenkins 파이프라인에서 호출하며, 호스트에서 수동 실행도 가능하다.
#
# 주의: APP_DIR은 "배포 대상 사본"이므로 git reset --hard로 origin 상태에 강제로 맞춘다.
#       이 디렉터리에서 직접 코드를 수정하지 말 것 (.env 등 gitignore된 파일은 보존됨).
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
DEPLOY_BRANCH="${DEPLOY_BRANCH:-develop}"
COMPOSE_FILE="$APP_DIR/infra/docker-compose.yml"
ENV_FILE="$APP_DIR/infra/.env"
# jenkins는 일부러 제외한다 — 이 스크립트는 Jenkins 컨테이너 안에서 실행되므로
# jenkins까지 up 대상에 넣으면 배포 도중 자기 자신을 재시작해 빌드가 죽는다.
# jenkins 이미지 변경은 호스트에서 수동으로 `docker compose up -d --build jenkins` 실행.
DEPLOY_SERVICES="${DEPLOY_SERVICES:-mysql redis backend nginx certbot}"
# 배포 검증은 두 단계로 한다.
#
#  1) readiness — 앱이 의존성(DB/Redis)에 실제로 붙었는가
#     /actuator/health 는 DataSource·Redis 상태를 집계하므로 "떴지만 DB에 못 붙은" 배포를 잡아낸다.
#     실측(2026-09-08): MySQL을 pause한 상태에서
#       /actuator/health → 실패(타임아웃)
#       /api/health      → 200  ← 앱 컨트롤러라 의존성을 보지 않아 통과해버린다
#     즉 /api/health 만으로 검증하면 DB에 못 붙는 배포가 성공으로 처리된다.
#
#     actuator는 nginx에서 외부 차단(404)돼 있으므로 컨테이너 안에서 직접 호출한다.
#     docker exec를 쓰면 Jenkins 컨테이너에서든 호스트에서든 동일하게 동작한다.
#
#  2) end-to-end — 외부에서 nginx를 거쳐 실제로 응답이 나오는가
#     nginx 라우팅까지 포함해 확인한다.
HEALTH_URL="${HEALTH_URL:-http://localhost/api/health}"
READINESS_CMD="${READINESS_CMD:-docker exec backend curl -fsS --max-time 10 http://localhost:8080/actuator/health}"
HEALTH_RETRIES="${HEALTH_RETRIES:-40}"
HEALTH_INTERVAL="${HEALTH_INTERVAL:-5}"

log() { echo "[deploy] $*"; }

if [ ! -f "$ENV_FILE" ]; then
    log "ERROR: $ENV_FILE 가 없습니다. .env.example을 복사해 값을 채우세요."
    exit 1
fi

log "APP_DIR=$APP_DIR / branch=$DEPLOY_BRANCH"

log "소스 동기화"
git config --global --add safe.directory "$APP_DIR" 2>/dev/null || true

# Jenkins 컨테이너 안에는 호스트의 ~/.git-credentials가 없으므로,
# 파이프라인이 GIT_USER/GIT_TOKEN을 넘겨주면 그것으로 인증한다.
# (토큰은 디스크에 쓰지 않고 helper가 표준출력으로만 넘긴다)
# 호스트에서 수동 실행할 때는 저장된 credential.helper가 쓰인다.
if [ -n "${GIT_USER:-}" ] && [ -n "${GIT_TOKEN:-}" ]; then
    export GIT_USER GIT_TOKEN
    GIT_AUTH=(-c credential.helper= \
              -c "credential.helper=!f() { echo \"username=\${GIT_USER}\"; echo \"password=\${GIT_TOKEN}\"; }; f")
else
    GIT_AUTH=()
fi

git -C "$APP_DIR" "${GIT_AUTH[@]}" fetch --prune origin
git -C "$APP_DIR" checkout "$DEPLOY_BRANCH"

# 롤백 지점: 동기화 "전" 커밋. 배포가 실패하면 여기로 되돌린다.
PREV_COMMIT="$(git -C "$APP_DIR" rev-parse HEAD)"
# 현재 떠 있는 backend 이미지도 함께 보존한다.
# (코드를 되돌려도 이미지를 다시 빌드하면 시간이 걸리고, 빌드 자체가 깨져 있을 수도 있다)
PREV_IMAGE=""
if docker image inspect infra-backend:latest >/dev/null 2>&1; then
    docker tag infra-backend:latest infra-backend:rollback && PREV_IMAGE="infra-backend:rollback"
fi

git -C "$APP_DIR" reset --hard "origin/$DEPLOY_BRANCH"
log "현재 커밋: $(git -C "$APP_DIR" rev-parse --short HEAD) $(git -C "$APP_DIR" log -1 --pretty=%s)"
log "롤백 지점: $(git -C "$APP_DIR" rev-parse --short "$PREV_COMMIT")${PREV_IMAGE:+ + 이미지 보존됨}"

# 배포 실패 시 직전 상태로 되돌린다.
# 설계 의도: 서비스는 살리되 빌드는 실패로 끝낸다.
#   - 서비스가 죽은 채 방치되는 것이 가장 나쁘다 (2026-09-07, 09-08 두 차례 실제 발생)
#   - 그렇다고 조용히 성공 처리하면 develop이 깨진 것을 아무도 모른다
rollback() {
    log "───────────────────────────────────────────────"
    log "롤백 시작: $(git -C "$APP_DIR" rev-parse --short "$PREV_COMMIT") 상태로 되돌립니다"
    git -C "$APP_DIR" reset --hard "$PREV_COMMIT" || { log "롤백 실패(git)"; return 1; }
    if [ -n "$PREV_IMAGE" ]; then
        docker tag "$PREV_IMAGE" infra-backend:latest || true
    fi
    # --build를 쓰지 않는다: 보존해둔 이미지를 그대로 사용해야 빠르고 확실하다
    docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --no-deps --force-recreate $DEPLOY_SERVICES \
        || { log "롤백 실패(compose)"; return 1; }
    docker exec nginx nginx -s reload 2>/dev/null || true

    # 롤백 검증용 URL은 따로 둔다. 기본값은 동일하지만,
    # "배포는 실패시키고 롤백은 성공해야 한다"는 시나리오를 실제로 돌려보려면 분리가 필요하다.
    # (검증되지 않은 롤백 경로는 없느니만 못하므로 테스트 가능하게 만들어 둔다)
    ROLLBACK_HEALTH_URL="${ROLLBACK_HEALTH_URL:-$HEALTH_URL}"
    log "롤백 후 헬스체크: $ROLLBACK_HEALTH_URL"
    for i in $(seq 1 20); do
        c="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$ROLLBACK_HEALTH_URL" || true)"
        if [ "$c" = "200" ]; then log "롤백 성공 — 서비스 정상 (${i}회차)"; return 0; fi
        sleep "$HEALTH_INTERVAL"
    done
    log "롤백했으나 헬스체크가 여전히 실패 — 수동 확인 필요"
    return 1
}

# .env가 잘못돼 있으면 컨테이너가 기동에 실패하는 형태로만 드러나 원인 추적이 오래 걸린다.
# 배포를 진행하기 전에 먼저 걸러낸다.
CHECK_ENV="$(dirname "$0")/check-env.sh"
if [ -x "$CHECK_ENV" ]; then
    log "환경변수 사전 검사"
    APP_DIR="$APP_DIR" ENV_FILE="$ENV_FILE" COMPOSE_FILE="$COMPOSE_FILE" "$CHECK_ENV" \
        || fail_msg="환경변수 검사 실패"
    [ -n "${fail_msg:-}" ] && { log "ERROR: $fail_msg"; exit 1; }
fi

log "이미지 빌드 및 컨테이너 기동 ($DEPLOY_SERVICES)"
# --no-deps: depends_on을 타고 jenkins 같은 비대상 서비스가 재생성되는 것을 막는다.
# (DEPLOY_SERVICES에서 제외해도 의존성으로 끌려 들어오면 배포가 자기 자신을 죽인다)
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build --no-deps $DEPLOY_SERVICES

log "nginx 설정 리로드 (conf.d 변경 반영)"
docker exec nginx nginx -s reload 2>/dev/null || log "nginx reload 생략(컨테이너 미기동?)"

log "① readiness 검사 (의존성 포함): actuator/health"
READY=0
for i in $(seq 1 "$HEALTH_RETRIES"); do
    if out="$($READINESS_CMD 2>/dev/null)"; then
        log "  readiness 통과 (${i}회차) — $out"
        READY=1
        break
    fi
    log "  대기중... (${i}/${HEALTH_RETRIES})"
    sleep "$HEALTH_INTERVAL"
done

if [ "$READY" = "1" ]; then
    log "② end-to-end 검사 (nginx 경유): $HEALTH_URL"
    for i in $(seq 1 "$HEALTH_RETRIES"); do
        code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || true)"
        if [ "$code" = "200" ]; then
            log "  end-to-end 통과 (${i}회차)"
            log "배포 성공"
            docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps
            exit 0
        fi
        log "  대기중... (${i}/${HEALTH_RETRIES}, http_code=$code)"
        sleep "$HEALTH_INTERVAL"
    done
    log "ERROR: readiness는 통과했으나 nginx 경유 접근이 실패 — 라우팅 문제 의심"
else
    log "ERROR: readiness 실패 — 앱이 기동하지 못했거나 DB/Redis에 접속하지 못함"
fi

log "backend 로그 마지막 100줄:"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" logs --tail=100 backend

if rollback; then
    log "───────────────────────────────────────────────"
    log "배포는 실패했지만 서비스는 직전 상태로 복구되었습니다."
    log "develop이 깨진 상태이므로 원인을 수정한 뒤 다시 배포하세요."
else
    log "───────────────────────────────────────────────"
    log "★ 롤백까지 실패했습니다. 서비스가 중단 상태일 수 있으니 즉시 수동 확인이 필요합니다."
fi
# 롤백 성공 여부와 무관하게 빌드는 실패로 끝낸다 — develop이 깨졌다는 사실이 드러나야 한다.
exit 1
