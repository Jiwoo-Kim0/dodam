#!/usr/bin/env python3
"""seed 491/522의 애플리케이션 fixture 테이블을 실제 서버 키로 만든다(서버에서 실행, 키는 파일에 남기지 않음).

- 암호문: FieldCryptor와 같은 [12바이트 IV][AES-256-GCM 암호문+태그], 키 = DATA_ENCRYPTION_KEY(Base64 32바이트)
- 해시  : SensitiveNumberHasher와 같은 HMAC-SHA-256 소문자 hex, 키 = NUMBER_HASH_KEY(UTF-8 문자열)
- 마스킹: AccountNumberMasker(12자리 이상 → 앞6 + '-**-**' + 뒤4). 카드는 앞4 + '-****-****-' + 뒤4
- 계좌·카드 번호 원문은 합성값이며 출력 SQL에는 들어가지 않는다(암호문·해시·마스킹만).

사용: python3 make_seed_fixtures.py /home/ubuntu/app/infra/.env /home/ubuntu/seed-load
"""
import base64
import hmac
import hashlib
import os
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def read_env(path: str) -> dict:
    values = {}
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            value = value.strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values[key.strip()] = value
    return values


def main(env_path: str, out_dir: str) -> int:
    env = read_env(env_path)
    data_key = base64.b64decode(env["DATA_ENCRYPTION_KEY"])
    if len(data_key) != 32:
        raise SystemExit("DATA_ENCRYPTION_KEY must decode to 32 bytes")
    number_hash_key = env["NUMBER_HASH_KEY"].encode("utf-8")
    aes = AESGCM(data_key)

    def encrypt(plain: str) -> str:
        iv = os.urandom(12)
        return (iv + aes.encrypt(iv, plain.encode("utf-8"), None)).hex()

    def number_hash(plain: str) -> str:
        return hmac.new(number_hash_key, plain.encode("utf-8"), hashlib.sha256).hexdigest()

    def mask_account(digits: str) -> str:
        return digits[:6] + "-**-**" + digits[-4:] if len(digits) >= 12 else digits[:3] + "-***-" + digits[-3:]

    def mask_card(digits: str) -> str:
        return digits[:4] + "-****-****-" + digits[-4:]

    def account_no(n: int, suffix: str) -> str:
        return f"1100{n:06d}{suffix}"  # 13 digits

    def card_no(n: int, suffix: str) -> str:
        return f"5{n:07d}{suffix}"  # 16 digits

    # ---- 491: account crypto ----
    rows = []
    for n in range(1, 4001):
        plain = account_no(n, "001")
        rows.append((f"U{n:04d}_PRIMARY", encrypt(plain), number_hash(plain), mask_account(plain)))
    inactive = account_no(2, "002")
    rows.append(("U0002_INACTIVE", encrypt(inactive), number_hash(inactive), mask_account(inactive)))
    with open(os.path.join(out_dir, "fixture_491.sql"), "w", encoding="utf-8") as out:
        out.write("DROP TABLE IF EXISTS tmp_dodam_491_account_crypto;\n")
        out.write("CREATE TABLE tmp_dodam_491_account_crypto (seed_account_code VARCHAR(50) NOT NULL, account_no_encrypted VARBINARY(512) NOT NULL, "
                  "account_no_hash CHAR(64) NOT NULL, account_no_masked VARCHAR(100) NOT NULL);\n")
        for i in range(0, len(rows), 500):
            chunk = rows[i:i + 500]
            out.write("INSERT INTO tmp_dodam_491_account_crypto VALUES\n" + ",\n".join(
                f"('{c}', X'{e}', '{h}', '{m}')" for c, e, h, m in chunk) + ";\n")

    # ---- 522: account keys + card crypto ----
    with open(os.path.join(out_dir, "fixture_522.sql"), "w", encoding="utf-8") as out:
        out.write("DROP TABLE IF EXISTS tmp_dodam_522_account_keys;\n")
        out.write("CREATE TABLE tmp_dodam_522_account_keys (user_email VARCHAR(255) NOT NULL PRIMARY KEY, account_no_hash CHAR(64) NOT NULL);\n")
        keys = [(f"seed.kmeans.u{n:04d}@dodam.invalid", number_hash(account_no(n, "001"))) for n in range(1, 4001)]
        for i in range(0, len(keys), 500):
            out.write("INSERT INTO tmp_dodam_522_account_keys VALUES\n" + ",\n".join(f"('{e}', '{h}')" for e, h in keys[i:i + 500]) + ";\n")
        out.write("DROP TABLE IF EXISTS tmp_dodam_522_card_crypto;\n")
        out.write("CREATE TABLE tmp_dodam_522_card_crypto (seed_card_code VARCHAR(50) NOT NULL PRIMARY KEY, card_no_encrypted VARBINARY(512) NOT NULL, "
                  "card_no_hash CHAR(64) NOT NULL, card_no_masked VARCHAR(100) NOT NULL);\n")
        cards = []
        for n in range(1, 4001):
            plain = card_no(n, "00000001")
            cards.append((f"U{n:04d}_CARD_PRIMARY", encrypt(plain), number_hash(plain), mask_card(plain)))
            if n % 2 == 0:
                plain2 = card_no(n, "00000002")
                cards.append((f"U{n:04d}_CARD_SECONDARY", encrypt(plain2), number_hash(plain2), mask_card(plain2)))
        for i in range(0, len(cards), 500):
            out.write("INSERT INTO tmp_dodam_522_card_crypto VALUES\n" + ",\n".join(
                f"('{c}', X'{e}', '{h}', '{m}')" for c, e, h, m in cards[i:i + 500]) + ";\n")
    # ---- 523: account keys + loan crypto + minus crypto ----
    # 계좌번호 접미사는 491(001/002)과 겹치지 않게 유형별로 나눈다(해시 네임스페이스 공유).
    def crypto_rows(codes_with_plain):
        return [(code, encrypt(p), number_hash(p), mask_account(p)) for code, p in codes_with_plain]

    def write_crypto_table(out, table: str, code_col: str, rows_):
        out.write(f"DROP TABLE IF EXISTS {table};\n")
        out.write(f"CREATE TABLE {table} ({code_col} VARCHAR(50) NOT NULL PRIMARY KEY, account_no_encrypted VARBINARY(512) NOT NULL, "
                  "account_no_hash CHAR(64) NOT NULL, account_no_masked VARCHAR(100) NOT NULL);\n")
        for i in range(0, len(rows_), 500):
            out.write(f"INSERT INTO {table} VALUES\n" + ",\n".join(
                f"('{c}', X'{e}', '{h}', '{m}')" for c, e, h, m in rows_[i:i + 500]) + ";\n")

    def write_keys_table(out, table: str):
        out.write(f"DROP TABLE IF EXISTS {table};\n")
        out.write(f"CREATE TABLE {table} (user_email VARCHAR(255) NOT NULL PRIMARY KEY, account_no_hash CHAR(64) NOT NULL);\n")
        for i in range(0, len(keys), 500):
            out.write(f"INSERT INTO {table} VALUES\n" + ",\n".join(f"('{e}', '{h}')" for e, h in keys[i:i + 500]) + ";\n")

    loans = crypto_rows([(f"U{n:04d}_LOAN_PRIMARY", account_no(n, "101")) for n in range(2, 4001, 2)]
                        + [(f"U{n:04d}_LOAN_SECONDARY", account_no(n, "102")) for n in range(4, 4001, 4)])
    minus = crypto_rows([(f"U{n:04d}_MINUS", account_no(n, "201")) for n in range(4, 4001, 4)])
    with open(os.path.join(out_dir, "fixture_523.sql"), "w", encoding="utf-8") as out:
        write_keys_table(out, "tmp_dodam_523_account_keys")
        write_crypto_table(out, "tmp_dodam_523_loan_crypto", "seed_loan_code", loans)
        write_crypto_table(out, "tmp_dodam_523_minus_crypto", "seed_account_code", minus)

    # ---- 524: account keys + deposit crypto + savings crypto ----
    deposits = crypto_rows([(f"U{n:04d}_DEPOSIT", account_no(n, "301")) for n in range(1, 4001, 2)])
    savings = crypto_rows([(f"U{n:04d}_SAVINGS", account_no(n, "401")) for n in range(2, 4001, 2)])
    with open(os.path.join(out_dir, "fixture_524.sql"), "w", encoding="utf-8") as out:
        write_keys_table(out, "tmp_dodam_524_account_keys")
        write_crypto_table(out, "tmp_dodam_524_deposit_crypto", "seed_account_code", deposits)
        write_crypto_table(out, "tmp_dodam_524_savings_crypto", "seed_account_code", savings)

    print(f"fixture_491.sql rows={len(rows)}, fixture_522.sql keys={len(keys)} cards={len(cards)}, "
          f"fixture_523.sql loans={len(loans)} minus={len(minus)}, fixture_524.sql deposits={len(deposits)} savings={len(savings)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1], sys.argv[2]))
