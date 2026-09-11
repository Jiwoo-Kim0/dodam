# 시연용 seed 데이터

`backend/database/seeds`의 SQL은 시연·K-Means 학습용 데이터다. Flyway가 자동 실행하지 않으며, 마이그레이션이 끝난 MySQL 8.x에
`mysql` 클라이언트로 직접 적재한다. 모든 파일은 재실행해도 같은 결과가 되게(멱등) 작성되어 있고, 선행 조건 검사(preflight)가
실패하면 영구 테이블에 아무것도 쓰지 않는다.

## 적재 순서

| 순서 | 파일 | Jira | 선행 조건 |
| --- | --- | --- | --- |
| 1 | `DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05_COMPAT_FIXED.sql`, `DODAM_FINANCIAL_PRODUCTS_BATCH_05_BIG4_CARDS_COMPAT_FIXED.sql` | 525 | V10 |
| 2 | `DODAM_USER_ACCOUNT_FINANCIAL_PROFILE_SEED.sql` | 491 | V1~V3, 애플리케이션이 만든 계좌번호 암호문·해시 임시 테이블 |
| 3 | `DODAM_CARD_INSTALLMENT_SEED.sql` | 522 | 491, V11~V12, 카드번호 암호문 임시 테이블 |
| 4 | `DODAM_LOAN_MINUS_ACCOUNT_SEED.sql` | 523 | 491, V13~V14 |
| 5 | `DODAM_DEPOSIT_SAVING_SEED.sql` | 524 | 491, V15 |
| 6 | `DODAM_TRANSACTION_SEED.sql` | 492 | 491, 522, V6 (카테고리 기준 행 20개) |
| 7 | `DODAM_GOAL_SEED.sql` | 493 | 491, 524, V5·V7·V8·V9 |
| 8 | `DODAM_SUBSCRIPTION_SEED.sql` | 492 | 491, 522, 6번, **Subscription 마이그레이션(미작성, V18 이후)** |

2~5는 계좌·카드번호 원문을 파일에 두지 않기 위해 애플리케이션 코덱으로 만든 임시 테이블(`tmp_dodam_*_crypto`, `*_account_keys`)이
같은 세션에 있어야 한다. 각 파일 머리말에 정확한 계약이 있다. 6~8은 임시 테이블 없이 자연키(사용자 이메일, 계좌 유형·이름)로 참조한다.

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
월말 스냅샷 이력. `goal.current_amount`는 연결 계좌 잔액 합으로, Goal API(509·510)가 재계산하는 값과 같다.
seed 목표는 `created_at = '2026-03-01 09:00:00'`으로 식별해 재실행 시 지우고 다시 만든다.

## 검증 상태

492·493·구독 seed는 2026-09-11 기준 **MySQL에서 실행해 보지 않았다**(작업 환경에 MySQL 없음). 문법·기대 건수는 파일 끝 검증 SELECT로
확인하도록 작성했으며, 첫 실행 결과를 `backend/docs/remaining-work.md`의 492·493 절에 기록한다.
