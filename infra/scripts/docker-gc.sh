#!/usr/bin/env bash
# Docker 디스크 정리. cron에서 호출한다.
#
# 왜 필요한가:
#   배포 한 번마다 744MB짜리 backend 이미지가 하나씩 쌓이는데, 파이프라인·cron 어디에도
#   정리하는 곳이 없었다. 2026-09-14 실측 기준 빌드 캐시만 30.2GB(248개, active 0)였다.
#   디스크는 309GB 중 42GB(14%)라 당장 위험하진 않지만, 방치하면 배포가 디스크로 죽는다.
#
# 무엇을 지우고 무엇을 남기는가:
#   - 이미지: dangling(태그 없는 것)만 지운다. `-a`는 쓰지 않는다.
#     → infra-backend:latest / :rollback / :deployed 는 태그가 있으므로 살아남는다.
#     → 실행 중인 컨테이너가 쓰는 이미지는 docker가 알아서 건너뛴다.
#     ★ deploy.sh가 배포 성공 시 :deployed 태그를 붙이는 이유가 여기에도 있다.
#       태그가 없으면 "방금 서비스에 올린 이미지"가 이 정리의 대상이 되어버린다.
#   - 빌드 캐시: 전부 지우지 않는다. 다 날리면 다음 배포의 gradle 빌드가 처음부터 돌아
#     배포 시간이 크게 늘어난다. 대신 두 단계로 줄인다.
#       1) CACHE_MAX_AGE보다 오래된 캐시를 버린다 (최근 캐시는 남으므로 빌드는 계속 빠르다)
#       2) 상한선(MAX_CACHE)을 백스톱으로 적용한다
#
#     ★ 2)만으로는 부족하다 — 2026-09-16 실측:
#       `docker builder prune --max-used-space 10GB`를 돌려도 46.27GB가 남았고,
#       같은 명령을 한 번 더 돌리면 `Total: 0B`로 아무것도 더 지우지 않는다.
#       BuildKit이 상한과 비교하는 값은 `docker builder du`의 Private(당시 10.73GB)이라
#       Reclaimable 46.27GB는 대상이 되지 않는다. 즉 상한만으로는 캐시가 계속 자란다.
#       (09-14 30.2GB → 09-16 65.5GB. 이틀 만에 2배)
#
#     ★ 기간을 7일 → 3일로 줄인 이유 (2026-09-16 → 09-17 실측):
#       `until=168h`로 돌렸더니 회수량이 427MB뿐이었다. 하루에도 여러 번 배포되므로
#       캐시 대부분이 7일 이내라 걸리는 게 거의 없었다. 반면 캐시는 하루 8.5GB씩 늘어
#       주 1회 정리(약 5GB 회수)로는 순증을 못 따라간다.
#       72h + 매일 실행이면 "최근 3일치"로 수렴해 25~30GB 선에서 안정화된다.
#       3일 이내 캐시는 남으므로 평소 빌드 속도에는 영향이 없다.
#       배포가 3일 넘게 없으면 캐시가 0에 수렴하는데, 이미지는 태그가 있어 그대로 남고
#       서비스도 영향받지 않는다. 다만 그 뒤 첫 빌드는 처음부터 돌아 오래 걸린다
#       (시연 직전 배포는 피할 것 — 하루 전에 한 번 돌려 캐시를 데워둔다)
#   - 볼륨/네트워크: 건드리지 않는다. mysql_data·jenkins_home이 여기 있다.
#     `docker system prune --volumes`를 이 스크립트에 절대 넣지 말 것.
set -uo pipefail

MAX_CACHE="${MAX_CACHE:-10GB}"          # 빌드 캐시 상한 (백스톱)
CACHE_MAX_AGE="${CACHE_MAX_AGE:-72h}"   # 이보다 오래된 빌드 캐시는 버린다 (기본 3일)
DISK_WARN_PCT="${DISK_WARN_PCT:-85}"

log() { echo "[docker-gc $(date '+%Y-%m-%d %H:%M:%S')] $*"; }

before="$(df --output=avail -BG / | tail -1 | tr -dc '0-9')"
log "시작 — 여유 ${before}GB"
docker system df 2>/dev/null | sed 's/^/  /'

# 배포 중에는 건드리지 않는다. 빌드 중인 캐시를 걷어내면 그 배포가 느려지거나 깨진다.
if docker ps --format '{{.Names}}' | grep -qx backend \
   && pgrep -f 'infra/scripts/deploy.sh' >/dev/null 2>&1; then
    log "배포가 진행 중이라 정리를 건너뛴다"
    exit 0
fi

log "dangling 이미지 정리"
docker image prune -f 2>&1 | sed 's/^/  /'

log "빌드 캐시 — ${CACHE_MAX_AGE}보다 오래된 것 정리"
docker builder prune -f --filter "until=$CACHE_MAX_AGE" 2>&1 | tail -2 | sed 's/^/  /'

log "빌드 캐시 — 상한 ${MAX_CACHE} 백스톱 적용"
docker builder prune -f --max-used-space "$MAX_CACHE" 2>&1 | tail -2 | sed 's/^/  /'

log "빌드 캐시 현황"
docker builder du 2>/dev/null | tail -3 | sed 's/^/  /'

log "중지된 컨테이너 정리"
docker container prune -f 2>&1 | sed 's/^/  /'

after="$(df --output=avail -BG / | tail -1 | tr -dc '0-9')"
used_pct="$(df --output=pcent / | tail -1 | tr -dc '0-9')"
log "완료 — 여유 ${before}GB → ${after}GB (디스크 사용률 ${used_pct}%)"

if [ "$used_pct" -ge "$DISK_WARN_PCT" ]; then
    log "WARNING: 정리 후에도 디스크 사용률 ${used_pct}% (임계 ${DISK_WARN_PCT}%)"
fi

# 남은 보존 이미지를 로그에 남긴다 — 롤백 지점이 살아 있는지 사후에 확인할 수 있게.
log "보존된 backend 이미지"
docker images --filter 'reference=infra-backend*' \
    --format '  {{.Repository}}:{{.Tag}} {{.ID}} {{.Size}}' 2>/dev/null
