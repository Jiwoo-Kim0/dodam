# Flyway migrations

| 버전 | 내용 | Jira |
| --- | --- | --- |
| V1 | `user`, `auth_session` 생성 (Auth API 최소 범위) | S15P21D105-494 |
| V2 | `user_setting`, `financial_profile`, `finance_connection` 생성 | S15P21D105-494, S15P21D105-497 |
| V3 | `account` 생성 (BE-A 소관 테이블 선반영) | S15P21D105-501 |
| V4 | `finance_sync` 생성 (금융 데이터 동기화 이력) | S15P21D105-499 |
| V5 | `goal` 생성 (BE-A 소관 테이블 선반영) | S15P21D105-543 |
| V6 | `transaction_category`, `transaction` 생성 + 카테고리 기준 행 적재 | S15P21D105-502, S15P21D105-504 |
| V7 | `saving_goal`, `spending_goal` 생성 (goal 하위행, 소비목표 비교용 선반영) | S15P21D105-507 |
| V8 | `goal_account` 생성 (목표 연결계좌) | S15P21D105-509 |
| V9 | `goal_history` 생성 (목표 변화 이력) | S15P21D105-511 |
| V10 | `financial_company`, `financial_product`, `financial_product_option` 생성 | S15P21D105-525, S15P21D105-526 |
| V11 | `card`, `card_billing_statement` 생성 | S15P21D105-527, S15P21D105-529 |
| V12 | `installment` 생성 | S15P21D105-530 |
| V13 | `loan`, `loan_repayment` 생성 | S15P21D105-531 |
| V14 | `minus_account` 생성 | S15P21D105-534 |
| V15 | `deposit_saving`, `savings_payment` 생성 | S15P21D105-535 |
| V16 | `cashflow_forecast`, `cashflow_daily_balance`, `cashflow_event`, `cashflow_risk` 생성 | S15P21D105-538 |
| V17 | `diagnosis`, `diagnosis_issue` 생성 | S15P21D105-543 |
| V18 | `recommendation`, `recommendation_reason`, `recommendation_history` 생성 (API 16 저장 기반) | S15P21D105-547 |
| V19 | `simulation`, `simulation_scenario`, `simulation_goal_impact` 생성 (API 17 공통 모델) | S15P21D105-550 |
| V30 | `subscription`, `subscription_payment_history` 생성 (API 7 정기결제) | S15P21D105-512, S15P21D105-513, S15P21D105-514 |
| V31 | `pattern_analysis` 생성 (API 3 사용자 패턴 분석) | S15P21D105-516, S15P21D105-517, S15P21D105-518 |
| V32 | `notification` 생성 (API 22 알림) | S15P21D105-519, S15P21D105-520 |
| V33 | `user_device` 생성 (FCM 푸시 기기 토큰) | S15P21D105-580 |
| V34 | `user_oauth_account` 생성 (소셜 로그인 카카오·구글 계정 연결) | S15P21D105-611 |
| V35 | `goal_account` 계좌 단일 연결 제약(`uk_goal_account_account`)을 예·적금에만 적용 — 생성 컬럼 `exclusive_account_id` + UNIQUE, 입출금은 여러 목표에 금액 분할 가능 (BE-B가 BE-A 테이블 변경, 2026-09-16 BE-A 확인·MySQL 8.0 검증) | S15P21D105-626 |
| V36 | `recommendation.source_issue_id` 추가 (추천-진단 문제 연결) | S15P21D105-628 |
| V37 | `improvement_action` 생성 (API 19 개선 행동) | S15P21D105-590 |
| V38 | `report`, `daily_report`, `monthly_report` 생성 (API 20 리포트) | S15P21D105-590 |
| V39 | `user.birth_date`, `gender`(CHECK MALE/FEMALE), `credit_rating`(금융망 등급명 A~E), `credit_rating_updated_at` 추가 — 모두 NULL 허용 | S15P21D105-629 |
| V40 | `pattern_analysis.peer_stats_json` 추가 (632 동년배 비교 결과 JSON, NULL 허용) | S15P21D105-632 |
| V41 | `monthly_report.efficiency_summary` 추가 | S15P21D105-635 |
| V42 | `user.credit_score`와 출처·산식 버전·산정 시각 추가 — SSAFY A~E 등급과 병행 | S15P21D105-680 |

## 버전 번호 할당

브랜치가 갈린 상태에서 같은 버전 번호를 잡으면 병합 시 반드시 충돌합니다. 담당 영역별로 번호 구간을 나눠 씁니다.

| 구간 | 담당 영역 |
| --- | --- |
| V1~V9 | BE-A (Auth/User, Account, Transaction/Spending, Goal 등) — V9로 소진 |
| V10~V17 | BE-B (금융상품, Card, Installment, Loan, 예적금, Cashflow, Diagnosis) — V17까지 사용 |
| V18~V29 | **BE-B·Infra 예비 구간** (Recommendation, Simulation, ProductSync, Dashboard, Improvement, Report 등). 2026-09-13 V18(recommendation, S15P21D105-547 브랜치)·V19(simulation) 사용, 다음은 V20 |
| V30~V49 | **BE-A 2차 구간** (Subscription, Notification, Preference 등). V30~V41 사용. 2026-09-22 V42(user 예상 신용점수, 680)·V43(credit_score SMALLINT→INT 정합, 475) 사용. 다음은 **V44** |

2026-09-11 BE-A는 팀원 부재 중에 Subscription 마이그레이션이 필요해 V18을 건너뛰고 **V30**을 잡았다. 돌아온 팀원이 "마지막 다음 번호" V18을
자연스럽게 잡아도 충돌하지 않게 하기 위한 것이다. `out-of-order: true`라 V17 → V30 다음에 V18이 들어와도 적용에 문제가 없다.
V18~V29가 다 차면 V50 이후를 다시 나눈다. 다른 구간이 필요하면 이 표를 먼저 고치고 팀 채널에 공유한다.

`V3`의 `account`는 BE-A 소관 테이블이지만, 이미 병합된 카드 도메인이 이 테이블을 매핑해 develop이
기동하지 못하는 상태였으므로 스키마만 먼저 추가했습니다. 계좌 API(S15P21D105-501)는 BE-A에서
진행하며 같은 테이블을 다시 만드는 마이그레이션을 추가하지 않습니다.

`V5`의 `goal`도 같은 처리입니다. `diagnosis.goal_id`가 ERD상 `goal(user_id, goal_id)`를 복합 외래키로
참조하는데 `goal` 마이그레이션이 없어 V17을 만들 수 없었습니다. `goal` 한 테이블만 ERD 정의 그대로
추가했고 `saving_goal`·`spending_goal`·`goal_account` 하위행은 넣지 않았습니다. Goal API
(S15P21D105-508~511)는 BE-A에서 진행하며 같은 테이블을 다시 만들지 않습니다.
**V5 번호는 BE-A와 확인이 필요합니다**(구간을 넘겨 쓴 것은 아니지만 BE-A 구간의 번호를 소비했습니다).
→ 2026-09-11 BE-A 확인: V5(goal)를 그대로 두고, Transaction 테이블은 V6으로 올렸다(S15P21D105-502). BE-A 구간(V1~V9)은 V9 goal_history로 모두 소진됐다. 다음 BE-A 테이블은 V18 이후 번호를 BE-B와 확인해 쓴다.

Flyway는 버전이 연속일 필요가 없으므로 빈 번호는 그대로 둡니다. 구간을 넘겨 써야 하면 먼저 해당 영역 담당자와 번호를 확인하세요.

이 방식은 **이미 V13이 적용된 DB에 나중에 V4가 추가되는 상황**을 전제로 합니다. Flyway 기본값은 이를
"resolved migration not applied to database" 검증 오류로 막으므로 local/prod 프로필에 `spring.flyway.out-of-order: true`를
켜 두었습니다(`FlywayOutOfOrderTest`가 두 동작을 재현). 새 프로필을 추가할 때도 이 옵션을 빠뜨리지 마세요.

`card`와 `loan`은 ERD 정의대로 `account`를 복합 외래키로 참조합니다. FK를 빼고 나중에 추가하는 방식은 데이터 적재 후 제약 추가가 어려워 택하지 않았습니다.

## 엔티티와 타입이 맞는지 먼저 확인하세요

운영·local은 `ddl-auto: validate`입니다. **컬럼 타입이 하나라도 어긋나면 Hibernate가 기동을 막습니다.**
더 나쁜 것은 validate가 **첫 번째 불일치에서 멈춘다**는 점입니다 — 하나를 고쳐 배포하면 다음 컬럼에서 또
같은 장애가 납니다. 2026-09-17에 `monthly_report.year_month`(DDL `CHAR(7)` vs 엔티티 기대 `varchar(7)`)로
실제로 기동이 막혔습니다.

`SchemaTypeConsistencyTest`(`backend/src/test/java/com/ssafy/dodam/global/schema/`)가 DB 없이 이것을
검사합니다. 엔티티 매핑과 이 폴더의 DDL을 타입 계열 단위로 맞춰 보고, 어긋나는 컬럼을 **전부 한 번에**
보여 줍니다. `./gradlew test`에 포함되므로 따로 실행할 필요는 없습니다.

가장 자주 나오는 것이 **고정 길이 문자열**입니다. 엔티티에서 `String`을 그냥 매핑하면 Hibernate는
`varchar`를 기대하므로, DDL이 `CHAR(n)`이면 엔티티에도 알려 줘야 합니다.

```java
@Column(name = "account_no_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
private String accountNoHash;

// 또는
@JdbcTypeCode(SqlTypes.CHAR)
@Column(name = "member_email_hash", length = 64)
private String memberEmailHash;
```

이미 적용된 마이그레이션 파일은 고치지 않습니다(Flyway 체크섬이 깨져 기동이 아예 안 됩니다).
DDL을 바꿔야 한다면 새 버전으로 `ALTER TABLE ... MODIFY`를 추가하고, 엔티티만 고쳐도 되는 경우라면
그쪽이 운영 DDL을 건드리지 않아 더 안전합니다.

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
