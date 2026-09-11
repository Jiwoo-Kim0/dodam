# Flyway migrations

| 버전 | 내용 | Jira |
| --- | --- | --- |
| V1 | `user`, `auth_session` 생성 (Auth API 최소 범위) | S15P21D105-494 |
| V2 | `user_setting`, `financial_profile`, `finance_connection` 생성 | S15P21D105-494, S15P21D105-497 |
| V3 | `account` 생성 (BE-A 소관 테이블 선반영) | S15P21D105-501 |
| V4 | `finance_sync` 생성 (금융 데이터 동기화 이력) | S15P21D105-499 |
| V5 | `goal` 생성 (BE-A 소관 테이블 선반영) | S15P21D105-543 |
| V6 | `transaction_category`, `transaction` 생성 + 카테고리 기준 행 적재 | S15P21D105-502, S15P21D105-504 |
| V10 | `financial_company`, `financial_product`, `financial_product_option` 생성 | S15P21D105-525, S15P21D105-526 |
| V11 | `card`, `card_billing_statement` 생성 | S15P21D105-527, S15P21D105-529 |
| V12 | `installment` 생성 | S15P21D105-530 |
| V13 | `loan`, `loan_repayment` 생성 | S15P21D105-531 |
| V14 | `minus_account` 생성 | S15P21D105-534 |
| V15 | `deposit_saving`, `savings_payment` 생성 | S15P21D105-535 |
| V16 | `cashflow_forecast`, `cashflow_daily_balance`, `cashflow_event`, `cashflow_risk` 생성 | S15P21D105-538 |
| V17 | `diagnosis`, `diagnosis_issue` 생성 | S15P21D105-543 |

## 버전 번호 할당

브랜치가 갈린 상태에서 같은 버전 번호를 잡으면 병합 시 반드시 충돌합니다. 담당 영역별로 번호 구간을 나눠 씁니다.

| 구간 | 담당 영역 |
| --- | --- |
| V1~V9 | BE-A (Auth/User, Account, Transaction/Spending, Goal 등) |
| V10~ | BE-B (금융상품, Card, Installment, Loan, 예적금, Cashflow 등) |

`V3`의 `account`는 BE-A 소관 테이블이지만, 이미 병합된 카드 도메인이 이 테이블을 매핑해 develop이
기동하지 못하는 상태였으므로 스키마만 먼저 추가했습니다. 계좌 API(S15P21D105-501)는 BE-A에서
진행하며 같은 테이블을 다시 만드는 마이그레이션을 추가하지 않습니다.

`V5`의 `goal`도 같은 처리입니다. `diagnosis.goal_id`가 ERD상 `goal(user_id, goal_id)`를 복합 외래키로
참조하는데 `goal` 마이그레이션이 없어 V17을 만들 수 없었습니다. `goal` 한 테이블만 ERD 정의 그대로
추가했고 `saving_goal`·`spending_goal`·`goal_account` 하위행은 넣지 않았습니다. Goal API
(S15P21D105-508~511)는 BE-A에서 진행하며 같은 테이블을 다시 만들지 않습니다.
**V5 번호는 BE-A와 확인이 필요합니다**(구간을 넘겨 쓴 것은 아니지만 BE-A 구간의 번호를 소비했습니다).
→ 2026-09-11 BE-A 확인: V5(goal)를 그대로 두고, Transaction 테이블은 V6으로 올렸다(S15P21D105-502). BE-A의 다음 번호는 V7부터다.

Flyway는 버전이 연속일 필요가 없으므로 빈 번호는 그대로 둡니다. 구간을 넘겨 써야 하면 먼저 해당 영역 담당자와 번호를 확인하세요.

이 방식은 **이미 V13이 적용된 DB에 나중에 V4가 추가되는 상황**을 전제로 합니다. Flyway 기본값은 이를
"resolved migration not applied to database" 검증 오류로 막으므로 local/prod 프로필에 `spring.flyway.out-of-order: true`를
켜 두었습니다(`FlywayOutOfOrderTest`가 두 동작을 재현). 새 프로필을 추가할 때도 이 옵션을 빠뜨리지 마세요.

`card`와 `loan`은 ERD 정의대로 `account`를 복합 외래키로 참조합니다. FK를 빼고 나중에 추가하는 방식은 데이터 적재 후 제약 추가가 어려워 택하지 않았습니다.

각 도메인 티켓은 자기 테이블만 다음 버전으로 추가합니다. DDL은 `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 해당 테이블 정의를 그대로 옮기고, 변경이 필요하면 ERD를 먼저 고칩니다. local/prod는 `ddl-auto: validate`이므로 엔티티와 DDL이 어긋나면 기동에 실패합니다.

2026-09-07 기준으로 ERD Markdown과 `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 구조 차이는 동기화했습니다.
Import DDL은 신규 스키마 검토용이며 기존 DB에 직접 실행하지 않습니다. 팀 승인 후 현재 운영 스키마와의 차이를 확인하고 별도 버전 마이그레이션을 작성하세요.

Seed와 실제 Migration은 다음 무결성 규칙을 지켜야 합니다.

- 예금·적금은 먼저 `account`에 `DEPOSIT` 또는 `SAVINGS`로 생성한 뒤 같은 `account_id`로 `deposit_saving` 상세행을 생성합니다.
- 마이너스통장도 먼저 `account`에 `MINUS`로 생성한 뒤 같은 `account_id`로 `minus_account` 상세행을 생성합니다.
- `goal_account`에는 연결할 Account의 `account_type`을 함께 넣으며 `DEMAND_DEPOSIT`, `DEPOSIT`, `SAVINGS`만 허용합니다.
- `goal_account`는 `saving_goal`에만 연결할 수 있으며 소비 목표에는 연결하지 않습니다.
- 한 Account는 하나의 Goal에만 연결하며 목표 삭제·연결 변경 시 GoalAccount 행을 같은 트랜잭션에서 제거·교체합니다.
- `subscription.card_id`가 있으면 반드시 같은 `user_id`의 Card를 참조해야 합니다.
- Card·Loan의 출금계좌는 `withdrawal_account_type=DEMAND_DEPOSIT`로 저장합니다.
- DepositSaving은 예금이면 출금계좌가 NULL일 수 있고, 적금이면 `DEMAND_DEPOSIT` 출금계좌가 필수입니다.
- `savings_payment.account_type`은 항상 `SAVINGS`이며 `installment_number`는 계좌 안에서 유일한 1 이상의 회차입니다.
- `cashflow_event.amount`는 항상 양수로 저장하고 `direction=INFLOW/OUTFLOW`로 방향을 구분합니다.
- 마이너스통장 Account의 `balance`는 `-used_amount`로 저장하고 총자산 합계에서 제외합니다.
- 외부 금융상품은 `source_type`과 `external_product_code`를 함께 저장하고 수동 Seed는 자연키를 사용합니다.
- Goal과 Report는 상위행과 유형별 하위행을 한 트랜잭션에서 함께 생성합니다.
- 상태·유형 문자열은 ERD 문서의 주요 코드값과 DDL `CHECK`를 기준으로 사용합니다.
