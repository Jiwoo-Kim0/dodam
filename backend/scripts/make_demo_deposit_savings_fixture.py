#!/usr/bin/env python3
"""DODAM_DEMO_DEPOSIT_SAVING_678_SEED.sql 이 요구하는 적금 계좌번호 암호문 fixture 3행을 만든다.

seed 파일은 계좌번호 원문·키를 담지 않으므로, 서버(또는 로컬)의 애플리케이션 키로 이 스크립트를 실행해
나온 SQL을 seed 파일보다 먼저 같은 DB에 적용한다. `make_demo_loan_fixture.py`(633)와 같은 코덱 규칙이며,
시연 계정 U0003의 적금 3건만 다루기 위해 레포에 둔다.

애플리케이션 코덱(FieldCryptor / SensitiveNumberHasher / AccountNumberMasker)과 같은 형식:
  - account_no_encrypted : [12바이트 IV][AES-256-GCM 암호문+128비트 태그], 키는 DATA_ENCRYPTION_KEY(Base64 32바이트)
  - account_no_hash      : HMAC-SHA-256 소문자 hex, 키는 NUMBER_HASH_KEY(문자열 그대로 UTF-8 바이트)
  - account_no_masked    : 12자리 이상 → 앞 6자리 + "-**-**" + 뒤 4자리

암호문은 실행마다 새 IV라 바이트가 달라지지만 해시는 같은 키면 항상 같다. seed 는 (user_id, account_no_hash) 로
재실행을 식별하므로 fixture 를 다시 만들어 적재해도 같은 계좌 행을 갱신한다.

사용(서버는 MySQL이 컨테이너 안에만 있으므로 `docker compose exec -T`로 넘긴다. 전체 절차는 seeds/README.md §시연 계정 U0003 예적금 보강):
  set -a; . infra/.env; set +a        # DATA_ENCRYPTION_KEY·NUMBER_HASH_KEY·MYSQL_* 가 채워진다
  python3 backend/scripts/make_demo_deposit_savings_fixture.py > /tmp/demo_deposit_savings_fixture.sql
  cd infra && docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/demo_deposit_savings_fixture.sql
  (같은 명령으로 backend/database/seeds/DODAM_DEMO_DEPOSIT_SAVING_678_SEED.sql 적재)

키는 환경변수(infra/.env 와 같은 이름)로만 읽고 출력하지 않는다. 계좌번호 원문도 stdout SQL에 남기지 않는다
(암호문·해시·마스킹값만). 의존: `pip install cryptography`.
"""
import base64
import hashlib
import hmac
import os
import re
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# 시연 계정 U0003 의 적금 계좌 원문. seed 계좌번호 규칙 `1100{순번6}{유형3}` 을 따르되 524 의 적금 유형 401 과
# 633 의 대출 유형 101 을 피해 402~404 를 쓴다. 524 는 짝수 사용자에게만 적금(401)을 만들어 U0003 과 겹치지 않는다.
FIXTURES = {
    "U0003_SAVINGS_YOUTH_LEAP": "1100000003402",      # 청년도약계좌
    "U0003_SAVINGS_HOUSING_SUBSCRIPTION": "1100000003403",  # 주택청약종합저축
    "U0003_SAVINGS_HOME_FUND": "1100000003404",       # 내집마련 목돈적금
}
TABLE = "tmp_dodam_demo_deposit_saving_crypto"


def load_key(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"{name} 환경변수가 비어 있습니다 (infra/.env 참고).")
    return value


def encrypt(data_key: bytes, plaintext: str) -> bytes:
    iv = os.urandom(12)
    return iv + AESGCM(data_key).encrypt(iv, plaintext.encode("utf-8"), None)


def number_hash(hash_key: str, plaintext: str) -> str:
    return hmac.new(hash_key.encode("utf-8"), plaintext.encode("utf-8"), hashlib.sha256).hexdigest()


def mask(account_no: str) -> str:
    digits = re.sub(r"\D", "", account_no)
    if len(digits) < 7:
        raise ValueError("마스킹할 수 없는 계좌번호 형식")
    if len(digits) >= 12:
        return digits[:6] + "-**-**" + digits[-4:]
    return digits[:3] + "-***-" + digits[-3:]


def main() -> None:
    data_key = base64.b64decode(load_key("DATA_ENCRYPTION_KEY"))
    if len(data_key) != 32:
        sys.exit("DATA_ENCRYPTION_KEY 는 Base64 32바이트여야 합니다.")
    hash_key = load_key("NUMBER_HASH_KEY")

    out = sys.stdout
    out.write("SET NAMES utf8mb4;\n")
    out.write(f"DROP TABLE IF EXISTS {TABLE};\n")
    out.write(
        f"CREATE TABLE {TABLE} (\n"
        "  seed_account_code VARCHAR(50) NOT NULL PRIMARY KEY,\n"
        "  account_no_encrypted VARBINARY(512) NOT NULL,\n"
        "  account_no_hash CHAR(64) NOT NULL,\n"
        "  account_no_masked VARCHAR(100) NOT NULL\n"
        ");\n"
    )
    for code, account_no in FIXTURES.items():
        encrypted = encrypt(data_key, account_no).hex()
        out.write(
            f"INSERT INTO {TABLE} (seed_account_code, account_no_encrypted, account_no_hash, account_no_masked)\n"
            f"VALUES ('{code}', UNHEX('{encrypted}'), '{number_hash(hash_key, account_no)}', '{mask(account_no)}');\n"
        )
    out.write(
        f"SELECT seed_account_code, account_no_masked, LENGTH(account_no_encrypted) AS encrypted_bytes "
        f"FROM {TABLE} ORDER BY seed_account_code;\n"
    )


if __name__ == "__main__":
    main()
