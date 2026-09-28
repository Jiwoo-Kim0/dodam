#!/usr/bin/env python3
"""시연 계정 U0003의 SEED '주거래 입출금'을 SSAFY 금융망 실계좌에 대응시킨다 (S15P21D105-705).

무대에서 주거래 → 도담 계좌 이체가 실제 금융망 이체(2.4.10)로 성공하려면 두 계좌가 모두 금융망에 있어야 한다.
도담 계좌는 U0003이 앱에서 금융망에 연결할 때 서버가 실계좌로 연다(SafeboxOpenListener). 주거래는 seed 계좌번호라
금융망에 없으므로 이 스크립트가 backend/docs/goal-transfer-plan.md §5 B안 2~5단계를 수행한다.

  1. 2.2.2 회원 조회로 userKey 확보 (앱의 금융망 연결이 먼저 끝나 있어야 한다)
  2. 2.4.2 상품 조회 → 2.4.3 계좌 생성
  3. 2.4.9 입금으로 **현재 도담 DB의 주거래 잔액과 같은 금액**을 넣고 2.4.7로 잔액 확인
  4. stdout에 SQL 출력: 주거래 행의 계좌번호 암호문·해시·마스킹·은행만 교체한다.
     account_id·balance·거래 이력·source_type(SEED)은 그대로다. SEED라 계좌 동기화가 이 행을 덮지 않는다.

SQL은 "seed 계좌번호 해시 + 지정한 잔액"일 때만 1행을 바꾼다. 이미 교체됐거나 잔액이 달라졌으면 0행이다.
금융망 계좌 생성·입금은 되돌릴 수 없으므로 한 번만 실행한다. 먼저 `--dry-run`으로 회원·상품 조회만 확인한다.

사용(서버):
  set -a; . infra/.env; set +a       # SSAFY_FINANCE_API_KEY·DATA_ENCRYPTION_KEY·NUMBER_HASH_KEY
  python3 backend/scripts/prepare_demo_primary_account.py --balance 15460000 --dry-run
  python3 backend/scripts/prepare_demo_primary_account.py --balance 15460000 > /tmp/demo_primary_account.sql
  cd infra && docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/demo_primary_account.sql

키·userKey·계좌번호 원문은 출력하지 않는다(stderr 진행 로그는 마스킹 값만). 의존: `pip install cryptography`.
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import random
import re
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Optional

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DEFAULT_EMAIL = "seed.kmeans.u0003@dodam.invalid"
# 491 seed 규칙 `1100{순번6}{유형3}`의 U0003 주거래(유형 001). 이 해시일 때만 교체해 재실행·오적용을 막는다.
DEFAULT_SEED_ACCOUNT_NO = "1100000003001"
DEPOSIT_BASE = "/ssafy/api/v1/edu/demandDeposit/"
KST = timezone(timedelta(hours=9))


def log(message: str) -> None:
    print(message, file=sys.stderr)


def load_key(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        sys.exit(f"{name} 환경변수가 비어 있습니다 (infra/.env 참고).")
    return value


def mask(account_no: str) -> str:
    digits = re.sub(r"\D", "", account_no)
    if len(digits) >= 12:
        return digits[:6] + "-**-**" + digits[-4:]
    if len(digits) >= 7:
        return digits[:3] + "-***-" + digits[-3:]
    raise ValueError("마스킹할 수 없는 계좌번호 형식")


def sql_text(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


class Finance:
    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key

    def _post(self, path: str, body: dict) -> dict:
        request = urllib.request.Request(
            self.base_url + path,
            data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            payload = error.read().decode("utf-8", "replace")
            try:
                parsed = json.loads(payload)
                code = parsed.get("responseCode") or parsed.get("Header", {}).get("responseCode")
            except ValueError:
                code = None
            sys.exit(f"금융망 오류 {error.code} ({code}) — {path}")

    def _header(self, api_name: str, user_key: Optional[str]) -> dict:
        now = datetime.now(KST)
        header = {
            "apiName": api_name,
            "transmissionDate": now.strftime("%Y%m%d"),
            "transmissionTime": now.strftime("%H%M%S"),
            "institutionCode": "00100",
            "fintechAppNo": "001",
            "apiServiceCode": api_name,
            "institutionTransactionUniqueNo": now.strftime("%Y%m%d%H%M%S") + f"{random.SystemRandom().randrange(1_000_000):06d}",
            "apiKey": self.api_key,
        }
        if user_key is not None:
            header["userKey"] = user_key
        return header

    def call(self, api_name: str, user_key: Optional[str], extra: Optional[dict] = None, allow_missing: bool = False):
        body = {"Header": self._header(api_name, user_key)}
        body.update(extra or {})
        root = self._post(DEPOSIT_BASE + api_name, body)
        code = root.get("Header", {}).get("responseCode")
        if code != "H0000":
            sys.exit(f"금융망 응답 오류 {code} — {api_name}")
        if "REC" not in root or root["REC"] is None:
            if allow_missing:
                return None
            sys.exit(f"금융망 응답에 REC가 없습니다 — {api_name}")
        return root["REC"]

    def user_key(self, email: str) -> str:
        root = self._post("/ssafy/api/v1/member/search", {"apiKey": self.api_key, "userId": email})
        key = root.get("userKey")
        if not key:
            sys.exit("금융망 회원을 찾지 못했습니다. 앱에서 U0003으로 금융망 연결(POST /api/v1/finance-data/connection)을 먼저 하세요.")
        return key


def main() -> None:
    # SQL에 한글 조건('주거래 입출금')이 있다. Windows 기본 인코딩(cp949)으로 쓰면 조건이 깨져 0행이 되므로 UTF-8로 고정한다.
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--balance", type=int, required=True, help="현재 도담 DB의 U0003 주거래 잔액(원)")
    parser.add_argument("--email", default=DEFAULT_EMAIL)
    parser.add_argument("--seed-account-no", default=DEFAULT_SEED_ACCOUNT_NO)
    parser.add_argument("--dry-run", action="store_true", help="회원·상품 조회만 하고 계좌를 만들지 않는다")
    args = parser.parse_args()
    if args.balance <= 0:
        sys.exit("--balance 는 양수여야 합니다.")

    finance = Finance(os.environ.get("SSAFY_FINANCE_BASE_URL", "https://finopenapi.ssafy.io"),
                      load_key("SSAFY_FINANCE_API_KEY"))
    data_key = base64.b64decode(load_key("DATA_ENCRYPTION_KEY"))
    if len(data_key) != 32:
        sys.exit("DATA_ENCRYPTION_KEY 는 Base64 32바이트여야 합니다.")
    hash_key = load_key("NUMBER_HASH_KEY")

    user_key = finance.user_key(args.email)
    existing = finance.call("inquireDemandDepositAccountList", user_key, allow_missing=True) or []
    log(f"금융망 회원 확인. 기존 수시입출금 계좌 {len(existing)}개(도담 계좌 포함 예상 1개)")
    products = [p for p in finance.call("inquireDemandDepositList", None) or [] if p.get("accountTypeUniqueNo")]
    if not products:
        sys.exit("사용 가능한 수시입출금 상품이 없습니다.")
    # 도담 계좌(SafeboxAccountService)는 고유번호가 가장 작은 상품을 쓴다. 화면에서 두 계좌 은행이 겹치지 않게 다른 상품을 우선한다.
    products.sort(key=lambda p: p["accountTypeUniqueNo"])
    product = products[1] if len(products) > 1 else products[0]
    log(f"선택 상품: {product.get('bankName')} / {product.get('accountName') or product.get('accountTypeName')}")
    if len(existing) > 1:
        log("경고: 기존 계좌가 2개 이상입니다. 이 스크립트가 이미 실행됐을 수 있습니다. 확인 후 진행하세요.")
    if args.dry_run:
        log("dry-run: 계좌를 만들지 않고 종료합니다.")
        return

    created = finance.call("createDemandDepositAccount", user_key, {"accountTypeUniqueNo": product["accountTypeUniqueNo"]})
    account_no = str(created.get("accountNo", "")).strip()
    if not re.fullmatch(r"[0-9]{10,20}", account_no):
        sys.exit("계좌 생성 응답에 계좌번호가 없습니다. 금융망 계좌 목록을 확인하세요.")
    masked = mask(account_no)
    log(f"계좌 생성 {masked}")

    finance.call("updateDemandDepositAccountDeposit", user_key,
                 {"accountNo": account_no, "transactionBalance": str(args.balance), "transactionSummary": "도담 시연 준비"})
    balance = int(finance.call("inquireDemandDepositAccountBalance", user_key, {"accountNo": account_no})["accountBalance"])
    if balance != args.balance:
        sys.exit(f"입금 후 잔액 {balance} 이 기대값 {args.balance} 과 다릅니다. SQL을 출력하지 않습니다.")
    log(f"입금 확인 {balance:,}원")

    iv = os.urandom(12)
    encrypted = (iv + AESGCM(data_key).encrypt(iv, account_no.encode("utf-8"), None)).hex()
    new_hash = hmac.new(hash_key.encode("utf-8"), account_no.encode("utf-8"), hashlib.sha256).hexdigest()
    seed_hash = hmac.new(hash_key.encode("utf-8"), args.seed_account_no.encode("utf-8"), hashlib.sha256).hexdigest()
    bank_code = str(created.get("bankCode") or product.get("bankCode") or "").strip()
    bank_name = str(product.get("bankName") or bank_code).strip()

    out = sys.stdout
    out.write("SET NAMES utf8mb4;\nSTART TRANSACTION;\n")
    out.write(
        "UPDATE `account` a JOIN `user` u ON u.user_id = a.user_id\n"
        f"SET a.account_no_encrypted = UNHEX('{encrypted}'), a.account_no_hash = '{new_hash}',\n"
        f"    a.account_no_masked = {sql_text(masked)}, a.bank_code = {sql_text(bank_code)}, a.bank_name = {sql_text(bank_name)},\n"
        "    a.updated_at = NOW()\n"
        f"WHERE u.email = {sql_text(args.email)} AND u.deleted_at IS NULL\n"
        "  AND a.account_type = 'DEMAND_DEPOSIT' AND a.status = 'ACTIVE' AND a.source_type = 'SEED'\n"
        f"  AND a.account_name = '주거래 입출금' AND a.balance = {args.balance} AND a.account_no_hash = '{seed_hash}';\n"
        "-- Expected: 1. 0 means the row was already replaced or its balance moved; nothing changed.\n"
        "SELECT ROW_COUNT() AS replaced_rows;\n"
        "COMMIT;\n"
    )
    log("SQL 출력 완료. 적용 후 replaced_rows = 1 인지 확인하세요.")


if __name__ == "__main__":
    main()
