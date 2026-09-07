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
HEALTH_URL="${HEALTH_URL:-http://localhost/api/health}"
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
git -C "$APP_DIR" reset --hard "origin/$DEPLOY_BRANCH"
log "현재 커밋: $(git -C "$APP_DIR" rev-parse --short HEAD) $(git -C "$APP_DIR" log -1 --pretty=%s)"

log "이미지 빌드 및 컨테이너 기동 ($DEPLOY_SERVICES)"
# --no-deps: depends_on을 타고 jenkins 같은 비대상 서비스가 재생성되는 것을 막는다.
# (DEPLOY_SERVICES에서 제외해도 의존성으로 끌려 들어오면 배포가 자기 자신을 죽인다)
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build --no-deps $DEPLOY_SERVICES

log "nginx 설정 리로드 (conf.d 변경 반영)"
docker exec nginx nginx -s reload 2>/dev/null || log "nginx reload 생략(컨테이너 미기동?)"

log "헬스체크: $HEALTH_URL"
for i in $(seq 1 "$HEALTH_RETRIES"); do
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$HEALTH_URL" || true)"
    if [ "$code" = "200" ]; then
        log "헬스체크 통과 (${i}회차)"
        docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps
        exit 0
    fi
    log "  대기중... (${i}/${HEALTH_RETRIES}, http_code=$code)"
    sleep "$HEALTH_INTERVAL"
done

log "ERROR: 헬스체크 실패. backend 로그 마지막 100줄:"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" logs --tail=100 backend
exit 1
