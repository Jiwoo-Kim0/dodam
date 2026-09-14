# 시연용 seed 데이터

`backend/database/seeds`의 SQL은 시연·K-Means 학습용 데이터다. Flyway가 자동 실행하지 않으며, 마이그레이션이 끝난 MySQL 8.x에
`mysql` 클라이언트로 직접 적재한다. 모든 파일은 재실행해도 같은 결과가 되게(멱등) 작성되어 있고, 선행 조건 검사(preflight)가
실패하면 영구 테이블에 아무것도 쓰지 않는다.

## 적재 순서

| 순서 | 파일 | Jira | 선행 조건 |
| --- | --- | --- | --- |
| 1 | `DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05_COMPAT_FIXED.sql`, `DODAM_FINANCIAL_PRODUCTS_BATCH_05_BIG4_CARDS_COMPAT_FIXED.sql` | 525 | V10 |
| 2 | `DODAM_USER_ACCOUNT_FINANCIAL_PROFILE_SEED.sql` | 491 | V1~V3, 애플리케이션이 만든 계좌번호 암호문·해시 fixture 테이블(일반 테이블) |
| 3 | `DODAM_CARD_INSTALLMENT_SEED.sql` | 522 | 491, V11~V12, 카드번호 암호문 fixture 테이블(일반 테이블) |
| 4 | `DODAM_LOAN_MINUS_ACCOUNT_SEED.sql` | 523 | 491, V13~V14 |
| 5 | `DODAM_DEPOSIT_SAVING_SEED.sql` | 524 | 491, V15 |
| 6 | `DODAM_TRANSACTION_SEED.sql` | 492 | 491, 522, V6 (카테고리 기준 행 20개) |
| 7 | `DODAM_GOAL_SEED.sql` | 493 | 491, 524, V5·V7·V8·V9 |
| 8 | `DODAM_SUBSCRIPTION_SEED.sql` | 492 | 491, 522, 6번, V30 (`subscription`·`subscription_payment_history`, 2026-09-11 추가) |

2~5는 계좌·카드번호 원문을 파일에 두지 않기 위해 애플리케이션 코덱으로 만든 fixture 테이블(`tmp_dodam_*_crypto`, `*_account_keys`)이
같은 스키마에 있어야 한다. 각 파일 머리말에 정확한 계약이 있다. 6~8은 fixture 없이 자연키(사용자 이메일, 계좌 유형·이름)로 참조한다.

**작업 테이블은 TEMPORARY가 아니다.** MySQL은 한 문장에서 같은 TEMPORARY 테이블을 두 번 참조할 수 없다(`ERROR 1137 Can't reopen table`).
2·3·6·7·8번은 자릿수 교차곱과 preflight 서브쿼리가 그 제약에 걸리므로 `tmp_dodam_*` 작업 테이블과 2·3번의 fixture 테이블을 일반 테이블로 만들고
파일 끝의 cleanup 절에서 모두 지운다. 4·5번은 파생 테이블과 단일 참조 문장으로 작성돼 TEMPORARY fixture로도 동작한다(머리말 참고).
파일이 중간에 멈추면 작업 테이블이 남는데, 다음 실행의 `DROP TABLE IF EXISTS`가 정리한다.

## 공통 규약

- 사용자: `seed.kmeans.u0001@dodam.invalid` ~ `u4000`. 성향 코드 `MOD(순번-1, 4)`: 0 BALANCED, 1 SPENDING_FOCUSED, 2 SAVING_FOCUSED, 3 VOLATILE.
  이 코드는 생성 규칙에만 쓰이고 어떤 테이블에도 저장되지 않는다(K-Means 라벨 누출 방지).
- 기준 시각: 적재 행의 `created_at`은 `2026-09-07 09:00:00`(목표·구독은 창 시작일 `2026-03-01 09:00:00`). 거래·이력 창은 `2026-03-01 ~ 2026-08-31`.
- `source_type = 'SEED'`인 계좌·거래는 금융망 동기화(ACCOUNT scope)가 덮어쓰지 않는다.
- 각 파일 끝의 검증 SELECT로 기대 건수·계약 위반 0건을 확인한다.

## 492 거래 seed (`DODAM_TRANSACTION_SEED.sql`)

사용자당 월 14건(급여 1, 고정 3, 구독 1~2, 저축 이체 1, 변동 8, 시연 사용자 U0001~U0004는 변동 16) × 6개월 ≈ 348,000건.
성향별 변동 소비·저축 이체 비율은 파일 머리말 표를 따른다. 거래 ID는 `SEED-U0001-202603-VAR-03` 형식이고, 재실행 시
`SEED-U%` 거래를 지우고 다시 만든다. 적재 후 주거래 계좌의 `last_transaction_date`를 채운다(491이 위임한 첫 갱신).

## 493 목표 seed (`DODAM_GOAL_SEED.sql`)

모으기 목표 4,000개(연결 계좌: 홀수 사용자 예금, 짝수 사용자 적금, BALANCED는 주거래 추가), 줄이기 목표 2,000개(SPENDING_FOCUSED·VOLATILE),
월말 스냅샷 이력. 524가 CLOSED로 만든 예·적금만 가진 사용자 1,000명은 주거래 계좌를 대신 연결한다(Goal API가 CLOSED 연결을 400으로 막음).
연결 4,750건 = 예금 1,500 + 적금 1,500 + 주거래 1,750. `goal.current_amount`는 연결 계좌 잔액 합으로, Goal API(509·510)가 재계산하는 값과 같다.
seed 목표는 `created_at = '2026-03-01 09:00:00'`으로 식별해 재실행 시 지우고 다시 만든다.

## 검증 상태

2026-09-11 Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용하고 1~7번을 순서대로 실행했다(fixture는 SHA2 기반 더미로 형식만 충족).
상세 수치는 `backend/docs/remaining-work.md` 492·493 절에 있다.

- 1~7번 전부 오류 없이 종료, 총 43초(522 약 14초, 524 약 11초, 492 약 14초). 파일 끝 검증 SELECT의 위반·불일치·누락 항목 전부 0.
- 전체 재실행 후 모든 테이블 건수와 거래 금액 합·목표 금액 합이 같다(멱등).
- 8번은 V30이 생기기 전에는 Subscription 테이블 부재로 첫 DELETE에서 멈추고 아무것도 쓰지 않았다. V30 추가 후 결과는 `remaining-work.md` 514 절.
- 새 DB에 마이그레이션을 손으로 적용할 때는 V6이 V11의 `card`를 참조하므로 V1~V5 → V10~V11 → V6~V9 → V12~V17 순서가 필요하다(Flyway `out-of-order` 전제).
- 1번은 마이그레이션에 없는 `product_sync_history` INSERT를 제거했다(ERD에만 있는 테이블).

2026-09-14 **시연 서버(j15D105.p.ssafy.io, MySQL 8.0 컨테이너)에 1~8번을 순서대로 적재했다.** 사용자 4,000·계좌 9,001·카드 6,000·할부 3,000·대출 3,000·
예적금 4,000·거래 348,192·목표 6,000·구독 6,000·상품 198. 각 파일 끝 검증 SELECT의 위반 항목 전부 0.

- fixture(491·522·523·524)는 서버 `infra/.env`의 `DATA_ENCRYPTION_KEY`·`NUMBER_HASH_KEY`로 애플리케이션 코덱과 같은 형식으로 만들었다
  (`FieldCryptor`: 12바이트 IV + AES-256-GCM 128비트 태그, `SensitiveNumberHasher`: HMAC-SHA-256 소문자 hex, `AccountNumberMasker` 규칙, 카드는 앞4-****-****-뒤4).
  계좌번호 원문은 `1100{순번6}{유형3}` 형식의 합성값이며(유형: 주거래 001, 휴면 002, 대출 101/102, 마이너스 201, 예금 301, 적금 401) 파일에는 남기지 않았다.
  생성 스크립트는 서버 `~/seed-load/make_seed_fixtures.py`(Python 3, `cryptography`)이고 키는 읽기만 하며 출력하지 않는다.
- 523·524는 fixture를 `DROP TEMPORARY TABLE`로 정리하므로 일반 테이블로 만든 fixture 6개가 남는다. 적재 후 `DROP TABLE tmp_dodam_523_*, tmp_dodam_524_*`로 지운다.
- 컨테이너 안에서 적재할 때는 `mysql -h127.0.0.1`(TCP)로 붙는다. 초기화 중 임시 서버는 소켓 ping이 먼저 성공해 문장이 그쪽으로 들어갈 수 있다.
- seed 사용자는 `password_hash`가 NULL이라 API 로그인이 불가하다. 시연 계정으로 로그인하려면 별도 비밀번호 설정 절차가 필요하다.
