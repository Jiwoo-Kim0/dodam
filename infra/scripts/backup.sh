#!/usr/bin/env bash
# MySQL 일일 백업. cron에서 호출한다.
#
# 사용:
#   ./backup.sh                 # 기본 설정으로 백업
#   BACKUP_DIR=/other ./backup.sh
#
# 주의: 이 덤프에는 평문 컬럼(이메일, 금액, 거래내역 등)이 그대로 들어간다.
#       암호화 컬럼(account_no_encrypted 등)은 복호화하려면 별도의 데이터 암호화 키가
#       필요하므로, 키 백업은 이 스크립트가 아니라 별도 절차로 관리한다.
#       (키와 덤프를 같은 곳에 두면 한 번의 유출로 둘 다 털린다 — infra/todo/security.md R5)
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
BACKUP_DIR="${BACKUP_DIR:-/home/ubuntu/backups/mysql}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
DISK_WARN_PCT="${DISK_WARN_PCT:-85}"

log() { echo "[backup $(date '+%Y-%m-%d %H:%M:%S')] $*"; }
fail() { log "ERROR: $*"; exit 1; }

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 없음"
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
: "${MYSQL_ROOT_PASSWORD:?.env에 MYSQL_ROOT_PASSWORD 없음}"
: "${MYSQL_DATABASE:?.env에 MYSQL_DATABASE 없음}"

docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null | grep -q true \
    || fail "$MYSQL_CONTAINER 컨테이너가 실행 중이 아님"

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$BACKUP_DIR/${MYSQL_DATABASE}-${STAMP}.sql.gz"
TMP="$OUT.partial"

log "덤프 시작 → $OUT"
# --single-transaction: InnoDB를 락 없이 일관된 시점으로 덤프
# MYSQL_PWD로 전달해 컨테이너 안 프로세스 목록에 비밀번호가 남지 않게 함
if ! docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" \
        mysqldump -u root \
            --single-transaction \
            --routines --triggers --events \
            --default-character-set=utf8mb4 \
            "$MYSQL_DATABASE" 2>/tmp/backup-err.$$ | gzip -c > "$TMP"; then
    log "mysqldump 실패:"; cat /tmp/backup-err.$$ >&2; rm -f "$TMP" /tmp/backup-err.$$
    exit 1
fi
rm -f /tmp/backup-err.$$

# 빈 덤프가 "성공"으로 남지 않도록 검증한다.
[ -s "$TMP" ] || fail "덤프 파일이 비어 있음"
gzip -t "$TMP" 2>/dev/null || fail "덤프 파일이 손상됨(gzip 검증 실패)"
zcat "$TMP" | grep -q "MySQL dump" || fail "덤프 내용이 예상과 다름"

mv "$TMP" "$OUT"
chmod 600 "$OUT"
log "완료: $(du -h "$OUT" | cut -f1)"

log "보관 기간(${RETENTION_DAYS}일) 지난 백업 정리"
find "$BACKUP_DIR" -maxdepth 1 -name "*.sql.gz" -mtime "+$RETENTION_DAYS" -print -delete | sed 's/^/  삭제: /'

USED_PCT="$(df --output=pcent "$BACKUP_DIR" | tail -1 | tr -dc '0-9')"
if [ "$USED_PCT" -ge "$DISK_WARN_PCT" ]; then
    log "WARNING: 디스크 사용률 ${USED_PCT}% (임계 ${DISK_WARN_PCT}%)"
fi

log "보유 백업 $(find "$BACKUP_DIR" -maxdepth 1 -name '*.sql.gz' | wc -l)개, 디스크 사용률 ${USED_PCT}%"
