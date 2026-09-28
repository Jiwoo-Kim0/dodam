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

# mysqldump가 끝까지 썼는지 확인한다.
# 마지막 행이 `-- Dump completed on ...`이다. 중간에 끊긴 덤프는 이 표식이 없다.
# (gzip -t로는 못 잡는다 — mysqldump가 중간에 죽어도 gzip 스트림 자체는 정상 종료될 수 있다)
#
# ★ `zcat "$TMP" | grep -q "MySQL dump"` 를 쓰면 안 된다 (2026-09-15 수정).
#   grep -q는 첫 매칭에서 즉시 종료하는데, "MySQL dump"는 헤더 1행에 있으므로
#   덤프가 크면 zcat이 아직 쓰는 중에 파이프가 닫혀 SIGPIPE(141)로 죽는다.
#   상단의 `set -o pipefail`이 그 141을 파이프라인 실패로 잡아 백업이 통째로 실패한다.
#   DB가 작을 때는 zcat이 grep보다 먼저 끝나 드러나지 않다가, 시드 적재로 덤프가
#   8.7KB → 8.1MB가 된 2026-09-15부터 매일 실패했다. 덤프 자체는 멀쩡했고
#   검증 단계만 죽인 것이라 `.partial`만 남고 조용히 넘어갔다.
#   경위: infra/todo/jiw/day-08.md 15절
#
#   tail은 EOF까지 읽으므로 조기 종료가 없고, 따라서 SIGPIPE도 없다.
#   헤더가 아니라 꼬리를 보는 쪽이 검증으로서도 낫다 — 시작이 아니라 완주를 확인한다.
TAIL="$(zcat "$TMP" 2>/dev/null | tail -3)"
case "$TAIL" in
    *"Dump completed"*) ;;
    *) fail "덤프가 끝까지 기록되지 않음(완료 표식 없음)" ;;
esac

mv "$TMP" "$OUT"
chmod 600 "$OUT"
log "완료: $(du -h "$OUT" | cut -f1)"

log "보관 기간(${RETENTION_DAYS}일) 지난 백업 정리"
# .partial 도 포함한다. 검증 실패로 중단되면 남는 파일인데, 예전에는 이름이
# "*.sql.gz"에 걸리지 않아 영원히 쌓였다.
find "$BACKUP_DIR" -maxdepth 1 \( -name "*.sql.gz" -o -name "*.sql.gz.partial" \) \
    -mtime "+$RETENTION_DAYS" -print -delete | sed 's/^/  삭제: /'

USED_PCT="$(df --output=pcent "$BACKUP_DIR" | tail -1 | tr -dc '0-9')"
if [ "$USED_PCT" -ge "$DISK_WARN_PCT" ]; then
    log "WARNING: 디스크 사용률 ${USED_PCT}% (임계 ${DISK_WARN_PCT}%)"
fi

log "보유 백업 $(find "$BACKUP_DIR" -maxdepth 1 -name '*.sql.gz' | wc -l)개, 디스크 사용률 ${USED_PCT}%"
