#!/usr/bin/env bash
# 시연 서버 seed 적재기 (backend/database/seeds/README.md "적재 순서" 1~18번).
#
# 사용:
#   ./seed-load.sh bootstrap <덤프.sql.gz>   # 운영 DB를 비우고 "마이그레이션이 끝난 빈 스키마"로 만든다
#   ./seed-load.sh load                      # fixture 생성 + 1~18번 순서대로 적재
#   ./seed-load.sh check [로그디렉터리]      # 적재 로그의 위반 수치 점검 + VERIFY 3종 실행
#   ./seed-load.sh all <덤프.sql.gz>         # bootstrap → load → check
#
# 왜 이 스크립트가 있나 (2026-09-24 706 재적재에서 겪은 것)
# ---------------------------------------------------------
#   1) **빈 DB에 Flyway만으로는 스키마를 만들 수 없다.** V6의 `transaction.card_id`가 V11의 `card`를
#      FK로 걸어 V1→V44 번호순 적용이 V6에서 멈춘다(Error 1824). 그래서 bootstrap은 마이그레이션이 아니라
#      **백업에서 정의만** 떠 온다.
#   2) `mysqldump --no-data`는 `AUTO_INCREMENT=` 값까지 들고 온다. 지우지 않으면 행 내용이 같아도
#      id가 통째로 밀려 `DODAM_TRANSACTION_VERIFY.sql` 1번 지문이 어긋난다.
#   3) 마이그레이션 34행과 `transaction_category` 20행을 따로 넣어야 한다(후자는 V6가 직접 넣는 기준 데이터라
#      `--no-data`로 안 따라온다).
#   4) DB를 갈아엎는 동안 backend가 떠 있으면 기동 실패 → 재기동 루프 → 같은 DB에 동시 쓰기가 된다.
#   5) **`mysql` 종료 코드 0은 seed 통과를 뜻하지 않는다.** seed의 자체 검증은 출력 숫자로 말한다.
#      실제로 14번이 `contract_violation_count = 1027`을 찍고 있었는데 종료 코드는 0이었다.
#      `check`가 로그의 위반 계열 컬럼을 전부 훑는다.
#
# 자세한 배경: backend/database/seeds/README.md "빈 DB에 1~18번을 처음부터 적재하기",
#              infra/todo/jiw/day-13.md 2026-09-24 절
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
SEED_DIR="${SEED_DIR:-$APP_DIR/backend/database/seeds}"
BE_SCRIPT_DIR="${BE_SCRIPT_DIR:-$APP_DIR/backend/scripts}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql}"
BACKEND_CONTAINER="${BACKEND_CONTAINER:-backend}"
TEMP_DB="${TEMP_DB:-dodam_restore_test}"
# 491·522·523·524 fixture 생성기. 애플리케이션 코덱과 같은 형식의 암호문·해시를 만든다.
# 키는 $ENV_FILE 에서 읽기만 하고 출력 SQL에는 암호문·해시·마스킹만 들어간다(원문 번호 없음).
# 2026-09-25 이전에는 서버 ~/seed-load/ 에만 있었다. 옛 서버를 쓰는 경우 FIXTURE_GEN 으로 지정한다.
FIXTURE_GEN="${FIXTURE_GEN:-$BE_SCRIPT_DIR/make_seed_fixtures.py}"
WORK_DIR="${WORK_DIR:-$HOME/seed-load/$(date +%Y%m%d-%H%M%S)}"

log()  { echo "[seed-load $(date '+%H:%M:%S')] $*"; }
fail() { echo "[seed-load $(date '+%H:%M:%S')] ERROR: $*" >&2; exit 1; }

load_env() {
    [ -f "$ENV_FILE" ] || fail "$ENV_FILE 없음"
    # shellcheck disable=SC1090
    set -a; . "$ENV_FILE"; set +a
    # .env 의 키는 MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE 다(DB_USERNAME/DB_PASSWORD 가 아니다).
    : "${MYSQL_USER:?.env에 MYSQL_USER 없음}"
    : "${MYSQL_PASSWORD:?.env에 MYSQL_PASSWORD 없음}"
    : "${MYSQL_DATABASE:?.env에 MYSQL_DATABASE 없음}"
    docker inspect -f '{{.State.Running}}' "$MYSQL_CONTAINER" 2>/dev/null | grep -q true \
        || fail "$MYSQL_CONTAINER 컨테이너가 실행 중이 아님"
}

# 비밀번호는 -p 가 아니라 MYSQL_PWD 로 넘긴다(ps 에 남지 않는다).
# 한글 리터럴이 깨지지 않도록 --default-character-set=utf8mb4 를 항상 붙인다.
mysql_app()  { docker exec -i -e MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CONTAINER" \
                 mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" "$@"; }
mysql_root() { docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" \
                 mysql --default-character-set=utf8mb4 -h127.0.0.1 -u root "$@"; }

require_backend_stopped() {
    local running
    running="$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER" 2>/dev/null || echo false)"
    [ "$running" = "true" ] || return 0
    [ "${ALLOW_BACKEND_UP:-0}" != "1" ] || { log "backend가 떠 있지만 ALLOW_BACKEND_UP=1 이라 계속한다"; return 0; }
    fail "backend 컨테이너가 떠 있다. 먼저 내린다:
    cd $APP_DIR/infra && docker compose stop $BACKEND_CONTAINER
  기동에 실패한 backend는 재기동 루프에 빠져 같은 DB에 마이그레이션을 동시에 쓴다(2026-09-24에 실제로 겪음).
  적재가 끝나면 docker compose start $BACKEND_CONTAINER 로 올린다."
}

# 파일 하나를 적재한다. 로그는 $WORK_DIR/<파일명>.log 에 남는다.
run_sql() {
    local src="$1" name start
    [ -f "$src" ] || src="$SEED_DIR/$1"
    [ -f "$src" ] || fail "파일 없음: $1"
    name="$(basename "$src")"
    start=$SECONDS
    if mysql_app < "$src" > "$WORK_DIR/$name.log" 2>&1; then
        log "  OK   $name ($((SECONDS - start))초)"
    else
        tail -20 "$WORK_DIR/$name.log" >&2
        fail "적재 실패: $name — 로그 $WORK_DIR/$name.log"
    fi
}

# ---------------------------------------------------------------------------
# bootstrap — 마이그레이션이 끝난 "빈 스키마"를 만든다
# ---------------------------------------------------------------------------
cmd_bootstrap() {
    local dump="${1:-}"
    [ -n "$dump" ] || fail "사용법: $0 bootstrap <덤프.sql.gz>"
    [ -f "$dump" ] || fail "덤프 파일을 찾을 수 없음: $dump"
    gzip -t "$dump" 2>/dev/null || fail "덤프 파일이 손상됨: $dump"
    load_env
    require_backend_stopped

    if [ "${FORCE:-0}" != "1" ]; then
        echo
        echo "  ⚠️  운영 DB '$MYSQL_DATABASE'의 모든 데이터를 지우고 빈 스키마로 만듭니다."
        echo "     스키마 출처: $dump"
        echo
        printf "  계속하려면 정확히 '%s' 를 입력하세요: " "$MYSQL_DATABASE"
        read -r confirm
        [ "$confirm" = "$MYSQL_DATABASE" ] || fail "확인 문구 불일치 — 중단"
    fi

    log "지우기 전 안전 백업"
    "$(dirname "$0")/backup.sh" >/dev/null || fail "안전 백업 실패 — 중단"

    log "덤프를 임시 DB '$TEMP_DB'로 복구 (운영 DB 미접촉)"
    RESTORE_DB="$TEMP_DB" "$(dirname "$0")/restore.sh" "$dump" >/dev/null

    log "운영 DB 재생성"
    mysql_root -e "DROP DATABASE IF EXISTS \`$MYSQL_DATABASE\`;
                   CREATE DATABASE \`$MYSQL_DATABASE\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

    # AUTO_INCREMENT= 를 지우는 것이 핵심이다. 남기면 옛 DB의 채번 위치를 물려받아
    # 행 내용이 같은데도 account_id·card_id 가 밀리고 VERIFY 1번 지문이 어긋난다.
    log "테이블 정의 복사 (AUTO_INCREMENT 리셋)"
    docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" sh -c \
      "mysqldump -h127.0.0.1 -uroot --no-data --routines --events '$TEMP_DB' \
         | sed 's/ AUTO_INCREMENT=[0-9]*//g' \
         | mysql -h127.0.0.1 -uroot '$MYSQL_DATABASE'"

    # flyway_schema_history 가 없으면 다음 기동에서 Flyway가 전부 다시 적용하려 들고 V6에서 깨진다.
    # transaction_category 20행은 V6 마이그레이션이 직접 넣는 기준 데이터라 --no-data 로는 안 따라온다.
    log "마이그레이션 이력 + 카테고리 기준 행 복사"
    docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$MYSQL_CONTAINER" sh -c \
      "mysqldump -h127.0.0.1 -uroot --no-create-info '$TEMP_DB' flyway_schema_history transaction_category \
         | mysql -h127.0.0.1 -uroot '$MYSQL_DATABASE'"

    log "임시 DB 정리"
    mysql_root -e "DROP DATABASE IF EXISTS \`$TEMP_DB\`;"

    local tables flyway_ok categories rows
    tables="$(mysql_app -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'")"
    flyway_ok="$(mysql_app -N -e "SELECT CONCAT(COUNT(*), '/', MIN(success)) FROM flyway_schema_history")"
    categories="$(mysql_app -N -e "SELECT COUNT(*) FROM transaction_category")"
    rows="$(mysql_app -N -e "SELECT COUNT(*) FROM \`user\`")"
    log "빈 스키마 완성 — 테이블 $tables개 / flyway $flyway_ok(행수/전부성공) / 카테고리 $categories행 / user $rows행"
    [ "$categories" = "20" ] || fail "카테고리 기준 행이 20개가 아니다($categories) — 6번 preflight가 막힌다"
    [ "$rows" = "0" ] || fail "빈 DB여야 하는데 user가 $rows행이다"
    case "$flyway_ok" in
        0/*|*/0) fail "flyway_schema_history가 비었거나 실패 행이 있다($flyway_ok) — 다음 기동에서 V6에서 깨진다" ;;
    esac
}

# ---------------------------------------------------------------------------
# load — fixture 생성 + 1~18번 적재
# ---------------------------------------------------------------------------
cmd_load() {
    load_env
    require_backend_stopped
    mkdir -p "$WORK_DIR"
    log "작업 디렉터리 $WORK_DIR"

    [ -f "$FIXTURE_GEN" ] || fail "fixture 생성기가 없다: $FIXTURE_GEN
  491·522·523·524는 애플리케이션 코덱으로 만든 암호문·해시 fixture가 있어야 적재된다.
  FIXTURE_GEN=<경로> 로 지정하거나 생성기를 복구한다."

    log "fixture 생성 (491·522·523·524)"
    python3 "$FIXTURE_GEN" "$ENV_FILE" "$WORK_DIR" >/dev/null

    log "[1] 금융 상품"
    run_sql DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05_COMPAT_FIXED.sql
    run_sql DODAM_FINANCIAL_PRODUCTS_BATCH_05_BIG4_CARDS_COMPAT_FIXED.sql
    log "[2] 491 사용자·계좌";      run_sql "$WORK_DIR/fixture_491.sql"; run_sql DODAM_USER_ACCOUNT_FINANCIAL_PROFILE_SEED.sql
    log "[3] 522 카드·할부";        run_sql "$WORK_DIR/fixture_522.sql"; run_sql DODAM_CARD_INSTALLMENT_SEED.sql
    log "[4] 523 대출·마이너스";    run_sql "$WORK_DIR/fixture_523.sql"; run_sql DODAM_LOAN_MINUS_ACCOUNT_SEED.sql
    log "[5] 524 예적금";           run_sql "$WORK_DIR/fixture_524.sql"; run_sql DODAM_DEPOSIT_SAVING_SEED.sql
    log "[6] 492 거래 (24개월, 2분 이상 걸린다)"; run_sql DODAM_TRANSACTION_SEED.sql
    log "[7] 493 목표";             run_sql DODAM_GOAL_SEED.sql
    log "[8] 구독";                 run_sql DODAM_SUBSCRIPTION_SEED.sql
    log "[9] 알림";                 run_sql DODAM_NOTIFICATION_SEED.sql
    log "[10] 시연 비밀번호";       run_sql DODAM_DEMO_PASSWORD_SEED.sql
    log "[11] 625 9월 거래";        run_sql DODAM_DEMO_SEPTEMBER_SEED.sql
    # 12번(인구통계 백필)은 새 DB에 불필요하다 — 491 최신판이 birth_date·gender를 채운다.
    log "[13] 633 U0003 대출"
    python3 "$BE_SCRIPT_DIR/make_demo_loan_fixture.py" > "$WORK_DIR/fixture_633.sql"
    run_sql "$WORK_DIR/fixture_633.sql"; run_sql DODAM_DEMO_LOAN_SEED.sql
    log "[14] 636 U0003 소비 스토리"; run_sql DODAM_DEMO_U0003_STORY_SEED.sql
    log "[15] 655 시연 구독";         run_sql DODAM_DEMO_SUBSCRIPTION_SEED.sql
    log "[16] 678 U0003 예적금"
    python3 "$BE_SCRIPT_DIR/make_demo_deposit_savings_fixture.py" > "$WORK_DIR/fixture_678.sql"
    run_sql "$WORK_DIR/fixture_678.sql"; run_sql DODAM_DEMO_DEPOSIT_SAVING_678_SEED.sql
    log "[17] 679 U0003 가계부";      run_sql DODAM_DEMO_U0003_LEDGER_679_SEED.sql
    # 15번이 시연 4명 구독을 다시 만들었으므로 알림을 한 번 더 돌린다(README 15번 규약).
    log "[9 재실행] 알림";            run_sql DODAM_NOTIFICATION_SEED.sql
    # 680은 모든 시연 보정이 끝난 뒤 마지막에 실행한다(U0003 점수를 하드코딩하지 않는다).
    log "[18] 680 신용점수";          run_sql DODAM_USER_CREDIT_SCORE_SEED.sql

    # 523·524는 fixture를 TEMPORARY 로 지우려 해서 일반 테이블이 남는다(README 참고).
    local leftover
    leftover="$(mysql_app -N -e "SELECT GROUP_CONCAT(CONCAT('\`', table_name, '\`'))
                                   FROM information_schema.tables
                                  WHERE table_schema=DATABASE() AND table_name LIKE 'tmp\_dodam\_%'")"
    if [ -n "$leftover" ] && [ "$leftover" != "NULL" ]; then
        log "남은 작업 테이블 정리: $leftover"
        mysql_app -e "DROP TABLE IF EXISTS $leftover;"
    fi

    # fixture 파일에는 계좌·카드번호 암호문과 해시가 들어 있다. 남겨 두지 않는다.
    rm -f "$WORK_DIR"/fixture_*.sql
    log "적재 완료 — 로그 $WORK_DIR"
}

# ---------------------------------------------------------------------------
# check — 로그의 위반 수치 점검 + VERIFY 실행
# ---------------------------------------------------------------------------
# seed 파일들은 끝에서 자체 검증 SELECT를 돌리는데, 결과가 0이 아니어도 mysql 종료 코드는 0이다.
# 그래서 로그를 직접 읽어 위반 계열 컬럼만 골라 본다(헤더 줄 = 전부 소문자 식별자, 그 다음 줄이 값).
scan_logs() {
    local dir="$1"
    awk -F'\t' '
      function is_header(   i) {
        for (i = 1; i <= NF; i++) if ($i !~ /^[a-z][a-z0-9_]*$/) return 0
        return NF > 0
      }
      is_header() {
        delete cols; active = 0
        for (i = 1; i <= NF; i++)
          if ($i ~ /(violation|mismatch|orphan|conflict|leak|duplicate|missing|without)/) { cols[i] = $i; active = 1 }
        next
      }
      active {
        for (i in cols)
          if ($i ~ /^[0-9]+$/ && $i + 0 != 0) printf "%s\t%s = %s\n", FILENAME, cols[i], $i
      }
    ' "$dir"/*.sql.log 2>/dev/null | sed -e "s#^$dir/##" -e 's/^/  ⚠️  /'
}

cmd_check() {
    load_env
    local dir="${1:-}"
    if [ -z "$dir" ]; then
        # 인자가 없으면 가장 최근 적재 로그 디렉터리를 쓴다.
        dir="$(ls -dt "$(dirname "$WORK_DIR")"/*/ 2>/dev/null | while read -r d; do
                 compgen -G "$d*.sql.log" >/dev/null && { echo "${d%/}"; break; }
               done)"
    fi
    [ -n "$dir" ] && [ -d "$dir" ] || fail "적재 로그 디렉터리를 찾지 못했다 (사용법: $0 check <로그디렉터리>)"

    log "적재 로그 점검 — $dir"
    local hits
    hits="$(scan_logs "$dir")"
    if [ -n "$hits" ]; then
        echo "$hits"
        log "⚠️  0이 아닌 위반 수치가 있다. 파일별 로그를 확인한다."
        log "   (2026-09-24 사례: 14번의 contract_violation_count 1027은 seed 파일 안 검증 경계가"
        log "    창 확장을 따라가지 못한 것이었다. 데이터가 아니라 검사식이 틀린 경우도 있다.)"
    else
        log "위반 수치 0 — 모든 seed 자체 검증 통과"
    fi

    local v
    for v in DODAM_TRANSACTION_VERIFY DODAM_DEMO_DEPOSIT_SAVING_678_VERIFY DODAM_DEMO_U0003_LEDGER_679_VERIFY; do
        [ -f "$SEED_DIR/$v.sql" ] || continue
        echo
        log "=== $v ==="
        mysql_app -t < "$SEED_DIR/$v.sql"
    done
    echo
    log "VERIFY 1번 기대값(2026-09-24 706 기준): 347652 / 195906782800 / 499687287"
    log "  행수·합계는 맞는데 지문만 다르면 AUTO_INCREMENT 승계를 의심한다(이 스크립트 머리말 2번)."
}

case "${1:-}" in
    bootstrap) shift; cmd_bootstrap "$@" ;;
    load)      shift; cmd_load "$@" ;;
    check)     shift; cmd_check "$@" ;;
    all)
        shift
        [ -n "${1:-}" ] || fail "사용법: $0 all <덤프.sql.gz>"
        cmd_bootstrap "$1"
        cmd_load
        cmd_check "$WORK_DIR"
        log "backend를 올린다: cd $APP_DIR/infra && docker compose start $BACKEND_CONTAINER"
        ;;
    *)
        sed -n '2,10p' "$0"
        exit 1
        ;;
esac
