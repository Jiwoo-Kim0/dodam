#!/usr/bin/env bash
# MySQL 백업 복구. 파괴적인 작업이라 명시적 확인을 요구한다.
#
# 사용:
#   ./restore.sh <덤프파일>                     # 검증용 임시 DB(dodam_restore_test)로 복구
#   ./restore.sh <덤프파일> --into-production   # 운영 DB를 덮어씀 (확인 문구 입력 필요)
#
# 기본 동작이 "임시 DB로 복구"인 이유: 복구 리허설을 안전하게 반복하기 위함이다.
# 실제 장애 복구에서만 --into-production을 쓴다.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"

log() { echo "[restore $(date '+%H:%M:%S')] $*"; }
fail() { log "ERROR: $*"; exit 1; }

DUMP="${1:-}"
MODE="${2:-}"
[ -n "$DUMP" ] || fail "사용법: $0 <덤프파일> [--into-production]"
[ -f "$DUMP" ] || fail "덤프 파일을 찾을 수 없음: $DUMP"

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 없음"
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
: "${MYSQL_ROOT_PASSWORD:?.env에 MYSQL_ROOT_PASSWORD 없음}"
: "${MYSQL_DATABASE:?.env에 MYSQL_DATABASE 없음}"

docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null | grep -q true \
    || fail "$MYSQL_CONTAINER 컨테이너가 실행 중이 아님"

gzip -t "$DUMP" 2>/dev/null || fail "덤프 파일이 손상됨"

mysql_run() {
    docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" mysql -u root "$@"
}

if [ "$MODE" = "--into-production" ]; then
    TARGET="$MYSQL_DATABASE"
    echo
    echo "  ⚠️  운영 DB '$TARGET'를 이 덤프로 덮어씁니다. 현재 데이터는 사라집니다."
    echo "     덤프: $DUMP"
    echo
    printf "  계속하려면 정확히 '%s' 를 입력하세요: " "$TARGET"
    read -r CONFIRM
    [ "$CONFIRM" = "$TARGET" ] || fail "확인 문구 불일치 — 중단"
    log "복구 전 안전 백업 생성"
    BACKUP_DIR="${BACKUP_DIR:-/home/ubuntu/backups/mysql}" "$(dirname "$0")/backup.sh" \
        || fail "안전 백업 실패 — 복구를 중단합니다"
else
    TARGET="${RESTORE_DB:-${MYSQL_DATABASE}_restore_test}"
    log "검증 모드: 임시 DB '$TARGET'로 복구합니다 (운영 DB는 건드리지 않음)"
fi

log "대상 DB 초기화: $TARGET"
mysql_run -e "DROP DATABASE IF EXISTS \`$TARGET\`; CREATE DATABASE \`$TARGET\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

log "덤프 적재 중..."
zcat "$DUMP" | mysql_run "$TARGET"

TABLES="$(mysql_run -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$TARGET';")"
log "복구 완료 — 테이블 $TABLES개"

if [ "$MODE" != "--into-production" ]; then
    echo
    log "임시 DB는 다음 명령으로 정리하세요:"
    echo "  docker exec -e MYSQL_PWD=... $MYSQL_CONTAINER mysql -u root -e \"DROP DATABASE \\\`$TARGET\\\`;\""
fi
