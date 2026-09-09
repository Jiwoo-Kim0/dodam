# Flyway migrations

| 버전 | 내용 | Jira |
| --- | --- | --- |
| V1 | `user`, `auth_session` 생성 (Auth API 최소 범위) | S15P21D105-494 |
| V2 | `user_setting`, `financial_profile`, `finance_connection` 생성 | S15P21D105-494, S15P21D105-497 |
| V10 | `financial_company`, `financial_product`, `financial_product_option` 생성 | S15P21D105-525, S15P21D105-526 |
| V11 | `card`, `card_billing_statement` 생성 | S15P21D105-527, S15P21D105-529 |

## 버전 번호 할당

브랜치가 갈린 상태에서 같은 버전 번호를 잡으면 병합 시 반드시 충돌합니다. 담당 영역별로 번호 구간을 나눠 씁니다.

| 구간 | 담당 영역 |
| --- | --- |
| V1~V9 | BE-A (Auth/User, Account, Transaction/Spending, Goal 등) |
| V10~ | BE-B (금융상품, Card, Installment, Loan, 예적금, Cashflow 등) |

Flyway는 버전이 연속일 필요가 없으므로 빈 번호는 그대로 둡니다. 구간을 넘겨 써야 하면 먼저 해당 영역 담당자와 번호를 확인하세요.

`card`와 `loan`은 ERD 정의대로 `account`를 복합 외래키로 참조합니다. `account` 테이블은 BE-A 소관(S15P21D105-501)이므로 해당 버전이 들어오기 전에는 V11 이후가 적용되지 않습니다. FK를 빼고 나중에 추가하는 방식은 데이터 적재 후 제약 추가가 어려워 택하지 않았습니다.

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
