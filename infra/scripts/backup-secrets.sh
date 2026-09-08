#!/usr/bin/env bash
# 시크릿(.env) 백업 — 암호화된 단일 파일로 묶어 서버 밖으로 내보내기 위한 스크립트.
#
# 왜 별도 스크립트인가:
#   backup.sh(DB 덤프)는 cron으로 자동 실행되지만, 이 스크립트는 자동화하지 않는다.
#   시크릿 백업본이 DB 덤프와 같은 디렉터리/같은 주기로 쌓이면 한 번의 유출로 둘 다 털린다.
#   (infra/todo/security.md R5)
#
#   특히 DATA_ENCRYPTION_KEY / DATA_HASH_KEY는 계좌번호·userKey 등 암호화 컬럼의 키라서
#   이 키가 없으면 DB 덤프를 복원해도 해당 컬럼을 읽을 수 없다. 반대로 키와 덤프가 한곳에
#   같이 있으면 암호화의 의미가 사라진다. 그래서 "따로, 수동으로, 서버 밖에" 보관한다.
#
# 사용:
#   ./backup-secrets.sh              # 암호화 백업 생성 (패스프레이즈 입력)
#   그 뒤 안내되는 scp 명령으로 로컬에 내려받고, 서버의 사본은 삭제할 것.
set -euo pipefail

APP_DIR="${APP_DIR:-/home/ubuntu/app}"
ENV_FILE="${ENV_FILE:-$APP_DIR/infra/.env}"
# DB 덤프 디렉터리(~/backups/mysql)와 의도적으로 분리한다.
OUT_DIR="${OUT_DIR:-/home/ubuntu/secrets-export}"

log() { echo "[secrets $(date '+%H:%M:%S')] $*"; }
fail() { log "ERROR: $*"; exit 1; }

[ -f "$ENV_FILE" ] || fail "$ENV_FILE 없음"
command -v openssl >/dev/null || fail "openssl 없음"

mkdir -p "$OUT_DIR"; chmod 700 "$OUT_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="$OUT_DIR/secrets-${STAMP}.env.enc"

log "포함되는 키 목록 (값은 출력하지 않음):"
grep -oE '^[A-Z_]+=' "$ENV_FILE" | tr -d '=' | sed 's/^/    /'

echo
echo "  이 백업을 열 때 필요한 패스프레이즈를 정하세요."
echo "  ★ 패스프레이즈를 잊으면 이 백업도 복구할 수 없습니다. 비밀번호 관리자에 저장하세요."
echo

# -pbkdf2: 구형 KDF 대신 반복 해싱 사용 (무차별 대입 비용 증가)
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
    -in "$ENV_FILE" -out "$OUT" \
    || fail "암호화 실패"

chmod 600 "$OUT"
log "생성 완료: $OUT ($(du -h "$OUT" | cut -f1))"

cat <<EOF

  다음 단계 (수동):

  1) 로컬 PC로 내려받기
       scp ssafy-ec2:$OUT ./

  2) 서버 사본 삭제 (서버가 털리면 백업도 같이 털리므로 남겨두지 않는다)
       ssh ssafy-ec2 'rm -f $OUT'

  3) 내려받은 파일을 EC2가 아닌 곳에 보관
       예: 개인 클라우드 드라이브, 비밀번호 관리자 첨부

  복호화 방법:
       openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in secrets-${STAMP}.env.enc -out .env

EOF
