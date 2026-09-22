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
# 배포 대상 서비스.
#
# compose에 정의된 서비스 중 jenkins만 제외한다.
#   - jenkins: 이 스크립트가 Jenkins 컨테이너 안에서 실행되므로, up 대상에 넣으면
#     배포 도중 자기 자신을 재시작해 빌드가 죽는다.
#     jenkins 이미지/설정 변경은 호스트에서 수동으로 실행할 것:
#       docker compose up -d --build --no-deps jenkins
#
# 모니터링 서비스도 반드시 포함한다. 목록에서 빠지면 설정 파일이나 이미지 태그를
# 고쳐 머지해도 배포가 반영하지 않아, 수동으로 올린 변경이 다음 배포의
# git reset --hard에 밀려 조용히 원복된다. (Day 3에서 같은 방식으로 사고가 났었다)
DEPLOY_SERVICES="${DEPLOY_SERVICES:-mysql redis backend nginx certbot prometheus node-exporter mysqld-exporter redis-exporter loki promtail grafana}"
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
# 현재 "서비스 중인" backend 이미지를 함께 보존한다.
# (코드를 되돌려도 이미지를 다시 빌드하면 시간이 걸리고, 빌드 자체가 깨져 있을 수도 있다)
#
# ★ infra-backend:latest 를 기준으로 삼으면 안 된다 (2026-09-14 수정).
#   Jenkins의 Build & Test 단계가 이 스크립트보다 **먼저**
#     docker compose -f $WORKSPACE/infra/docker-compose.yml build backend
#   를 돌리는데, 워크스페이스와 $APP_DIR 양쪽 모두 compose 프로젝트명이 'infra'
#   (= compose 파일이 있는 디렉터리명)라 동일한 infra-backend:latest 태그를 공유한다.
#   따라서 이 줄에 도달한 시점의 latest는 이미 "방금 빌드한, 아직 검증되지 않은 이미지"다.
#   그 상태로 rollback 태그를 잡으면, 롤백해도 같은 깨진 이미지가 다시 뜬다.
#   실측(2026-09-14): latest와 rollback이 동일 ID였고, 실제 실행 중 컨테이너는 별개 이미지였다.
#
#   → 태그가 아니라 "지금 실제로 떠 있는 것"을 기준으로 잡는다.
#
# ★ 단, "떠 있다"는 것만으로는 부족하다 (2026-09-17 추가).
#   실행 중 컨테이너가 **재시작 루프**일 수도 있다. 그 상태에서 다음 배포가 시작되면
#   깨진 이미지가 rollback 태그를 덮어써, 롤백해도 같은 깨진 이미지가 다시 뜬다.
#   2026-09-17 장애(day-08.md 31절) 때 실제로 그 조건이 갖춰져 있었고,
#   롤백이 먼저 끝나 정상 이미지로 돌아가 있던 덕분에 타이밍상 피했을 뿐이다.
#   → **healthy일 때만** rollback 태그를 갱신하고, 아니면 마지막으로 검증된
#     $DEPLOYED_TAG 를 롤백 지점으로 쓴다. 깨진 이미지가 롤백 지점이 되는 일은 없다.
DEPLOYED_TAG="infra-backend:deployed"   # 헬스체크를 통과한 마지막 이미지 (배포 성공 시 갱신)
PREV_IMAGE=""
RUNNING_IMAGE="$(docker inspect -f '{{.Image}}' backend 2>/dev/null || true)"
BACKEND_STATUS="$(docker inspect -f '{{.State.Status}}' backend 2>/dev/null || true)"
# healthcheck가 정의돼 있지 않은 경우를 none으로 구분한다 (그때는 status만으로 판단).
BACKEND_HEALTH="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' backend 2>/dev/null || true)"

backend_is_healthy() {
    [ "$BACKEND_STATUS" = "running" ] || return 1
    case "$BACKEND_HEALTH" in
        healthy|none) return 0 ;;
        *)            return 1 ;;   # starting / unhealthy → 롤백 지점으로 삼지 않는다
    esac
}

if backend_is_healthy && [ -n "$RUNNING_IMAGE" ] && docker image inspect "$RUNNING_IMAGE" >/dev/null 2>&1; then
    docker tag "$RUNNING_IMAGE" infra-backend:rollback && PREV_IMAGE="infra-backend:rollback"
    log "롤백 지점: 현재 실행 중인 정상 이미지 (status=$BACKEND_STATUS health=$BACKEND_HEALTH)"
elif docker image inspect "$DEPLOYED_TAG" >/dev/null 2>&1; then
    # 두 경우가 여기로 온다.
    #   ① 최초 배포라 컨테이너가 없거나, 실행 중 이미지가 스토어에서 사라진 경우
    #      태그가 붙지 않은 이미지는 GC 대상이 되어 실제로 사라진 적이 있다
    #      (2026-09-14 실측: 실행 중 backend의 이미지가 docker image inspect로 조회되지 않았다).
    #      그래서 배포 성공 시마다 $DEPLOYED_TAG 를 붙여 최소 한 세대는 남도록 해둔다.
    #   ② 현재 컨테이너가 비정상(재시작 루프·unhealthy)인 경우 ← 2026-09-17 추가
    #      이때 실행 중 이미지를 롤백 지점으로 삼으면 깨진 것으로 되돌아간다.
    docker tag "$DEPLOYED_TAG" infra-backend:rollback && PREV_IMAGE="infra-backend:rollback"
    log "롤백 지점: 마지막 검증 이미지($DEPLOYED_TAG) — 현재 backend가 롤백 기준으로 부적합" \
        "(status=${BACKEND_STATUS:-없음} health=${BACKEND_HEALTH:-없음})"
elif docker image inspect infra-backend:rollback >/dev/null 2>&1; then
    # $DEPLOYED_TAG 조차 없을 때의 마지막 보루. 기존 rollback 태그를 그대로 둔다.
    # 덮어쓰지 않는 것이 핵심이다 — 지금 실행 중인 것이 깨진 이미지일 수 있다.
    PREV_IMAGE="infra-backend:rollback"
    log "롤백 지점: 기존 infra-backend:rollback 태그를 그대로 사용한다 (갱신하지 않음)"
else
    log "WARN: 보존할 backend 이미지가 없다 — 실패해도 코드만 롤백된다(이미지 롤백 불가)"
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

# compose에 서비스를 추가하고 DEPLOY_SERVICES에 넣는 것을 잊으면,
# 그 서비스는 배포에 반영되지 않는데 아무 신호도 없다.
# (실제로 모니터링 스택 5개가 이 상태로 누락되어 있었다)
# 배포할 때마다 대조해서 알려준다.
ALL_SERVICES="$(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" config --services 2>/dev/null || true)"
EXCLUDED="jenkins"   # 의도적 제외 (자기 자신을 재시작하게 되므로)
for svc in $ALL_SERVICES; do
    echo "$DEPLOY_SERVICES $EXCLUDED" | grep -qw "$svc" \
        || log "WARN: '$svc' 가 compose에 있으나 배포 대상이 아닙니다 — DEPLOY_SERVICES 확인 필요"
done

# ── 사전 검증: 새 이미지가 실제로 기동하는지 운영 교체 전에 확인한다 ──────────
#
# 왜 필요한가 (2026-09-17 장애, day-08.md 31절):
#   아래 `up -d --build`는 **기존 컨테이너를 먼저 교체**한다. 새 이미지가 기동에
#   실패하면 그 순간부터 롤백이 끝날 때까지 서비스가 중단된다. 실제로 V38 스키마
#   불일치로 60분간 외부 502가 났다. 롤백은 정상 동작했지만, 롤백이 자리를 잡을
#   때마다 다음 배포가 다시 교체를 실행해 장애가 반복됐다.
#   → "교체하고 검증"이 아니라 "검증하고 교체" 순서로 바꾼다.
#     기동 실패는 이 단계에서 걸러지고, 운영 컨테이너는 손도 대지 않는다.
#
# 알아둘 점:
#   - 이 일회용 컨테이너에서도 Flyway가 돌아 마이그레이션이 적용된다. 어차피 본
#     배포에서 적용될 것이라 달라지는 것은 없고, 오히려 마이그레이션 자체의 실패를
#     서비스를 건드리기 전에 잡는다. (Flyway는 전진 전용이라 되돌아가지 않는다)
#   - 포트를 열지 않으므로(--service-ports 미사용) 운영 컨테이너와 충돌하지 않는다.
#   - 성공 판정은 종료 코드가 아니라 **로그**로 한다. 서버는 정상일수록 끝나지 않는다.
#   - PREFLIGHT=0 으로 끌 수 있다. 끄면 2026-09-17 장애와 같은 노출이 생긴다.
PREFLIGHT="${PREFLIGHT:-1}"
PREFLIGHT_TIMEOUT="${PREFLIGHT_TIMEOUT:-180}"
PREFLIGHT_MARKER="${PREFLIGHT_MARKER:-Started DodamApplication}"

preflight_abort() {
    # 운영 컨테이너는 건드리지 않았으므로 되돌릴 것은 코드뿐이다.
    # (102행에서 이미 새 커밋으로 reset된 상태라, 그대로 두면 코드와 실행 중 이미지가 어긋난다)
    log "코드를 직전 커밋으로 되돌린다: $(git -C "$APP_DIR" rev-parse --short "$PREV_COMMIT")"
    git -C "$APP_DIR" reset --hard "$PREV_COMMIT" || log "WARN: 코드 되돌리기 실패 — 수동 확인 필요"
    log "───────────────────────────────────────────────"
    log "★ 사전 검증에서 걸러졌습니다. 서비스는 중단되지 않았습니다."
    log "  develop이 깨진 상태이므로 원인을 수정한 뒤 다시 배포하세요."
    exit 1
}

if [ "$PREFLIGHT" = "1" ]; then
    log "사전 검증 ①: backend 이미지 빌드"
    if ! docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" build backend; then
        log "이미지 빌드 실패"
        preflight_abort
    fi

    log "사전 검증 ②: 일회용 컨테이너로 기동 확인 (최대 ${PREFLIGHT_TIMEOUT}초)"
    # `compose run -d`는 진행 로그를 함께 출력하므로 마지막 줄만 취하고,
    # 그 값이 실제 컨테이너 ID(64자리 hex)인지 확인한다.
    # (2026-09-17 실측: 진행 로그를 그대로 잡으면 여러 줄이 들어온다)
    PF_CID="$(docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" \
                run -d --no-deps backend 2>/dev/null | tail -1 | tr -d '[:space:]')" || PF_CID=""
    case "$PF_CID" in
        *[!0-9a-f]* | "") 
            log "일회용 컨테이너를 띄우지 못했다 (반환값: '${PF_CID:-없음}')"
            # 이름 규칙으로 혹시 떠 있을 잔여 컨테이너를 정리한다
            docker ps -aq --filter "name=infra-backend-run" | xargs -r docker rm -f >/dev/null 2>&1 || true
            preflight_abort
            ;;
    esac

    PF_OK=0
    PF_WAITED=0
    while [ "$PF_WAITED" -lt "$PREFLIGHT_TIMEOUT" ]; do
        if docker logs "$PF_CID" 2>&1 | grep -q "$PREFLIGHT_MARKER"; then
            PF_OK=1
            break
        fi
        # 컨테이너가 이미 죽었으면 더 기다릴 이유가 없다 (기동 실패는 대개 즉시 드러난다)
        if [ "$(docker inspect -f '{{.State.Running}}' "$PF_CID" 2>/dev/null)" != "true" ]; then
            break
        fi
        sleep 3
        PF_WAITED=$((PF_WAITED + 3))
    done

    if [ "$PF_OK" != "1" ]; then
        log "사전 검증 실패 — 새 이미지가 기동하지 못했습니다. 로그 마지막 60줄:"
        docker logs --tail 60 "$PF_CID" 2>&1 | sed 's/^/  /'
        docker rm -f "$PF_CID" >/dev/null 2>&1 || true
        preflight_abort
    fi

    log "사전 검증 통과 (${PF_WAITED}초) — 운영 컨테이너 교체를 진행한다"
    docker rm -f "$PF_CID" >/dev/null 2>&1 || true
    # 이전 배포가 중간에 죽어 남겼을 수 있는 고아 컨테이너까지 함께 정리한다.
    # 떠 있는 채로 방치되면 운영 DB에 붙은 백엔드가 둘이 된다.
    docker ps -aq --filter "name=infra-backend-run" | xargs -r docker rm -f >/dev/null 2>&1 || true
else
    log "WARN: PREFLIGHT=0 — 사전 검증을 건너뜁니다 (기동 실패 시 서비스가 중단됩니다)"
fi

log "이미지 빌드 및 컨테이너 기동 ($DEPLOY_SERVICES)"
# --no-deps: depends_on을 타고 jenkins 같은 비대상 서비스가 재생성되는 것을 막는다.
# (DEPLOY_SERVICES에서 제외해도 의존성으로 끌려 들어오면 배포가 자기 자신을 죽인다)
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build --no-deps $DEPLOY_SERVICES

log "nginx 설정 리로드 (conf.d 변경 반영)"
docker exec nginx nginx -s reload 2>/dev/null || log "nginx reload 생략(컨테이너 미기동?)"

# prometheus.yml은 bind mount라 컨테이너가 재생성되지 않으면 그대로 남는다.
# 위 `up -d`는 --force-recreate가 없어 서비스 정의·이미지가 그대로면 컨테이너를 건드리지 않는다.
# nginx와 같은 이유로 여기서 설정만 다시 읽힌다(--web.enable-lifecycle이 켜져 있다).
log "prometheus 설정 리로드 (스크레이프 잡 변경 반영)"
docker exec prometheus wget -q --post-data="" -O- http://localhost:9090/-/reload >/dev/null 2>&1 \
    || log "prometheus reload 생략(컨테이너 미기동?)"

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
            # 이 이미지는 두 단계 헬스체크를 모두 통과했다. 고정 태그를 붙여 둔다.
            # 이유 두 가지:
            #   1) 다음 배포의 롤백 지점 후보가 된다 (위 캡처 블록의 fallback)
            #   2) latest는 다음 빌드에서 옮겨가므로, 태그가 없으면 이 이미지는
            #      아무도 참조하지 않는 상태가 되어 GC로 사라질 수 있다
            NEW_IMAGE="$(docker inspect -f '{{.Image}}' backend 2>/dev/null || true)"
            docker image inspect "$NEW_IMAGE" >/dev/null 2>&1 || NEW_IMAGE="infra-backend:latest"
            docker tag "$NEW_IMAGE" "$DEPLOYED_TAG" 2>/dev/null \
                || log "WARN: $DEPLOYED_TAG 태그 실패 — 다음 배포의 롤백 fallback이 없다"
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
