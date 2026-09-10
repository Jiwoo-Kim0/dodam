# 예정 이벤트 통합·중복 제거 (S15P21D105-539)

## 범위와 근거

539는 계좌·구독·카드·대출·적금 원본을 예측 입력으로 변환한다. 저장 엔티티·마이그레이션은
538, 일별 잔액·캐시·멱등은 540, 실행·조회 API는 541, 위험·가용금액은 542 범위다.
이 작업은 HTTP 엔드포인트를 추가하지 않는다.

로컬 `SPRINT/Jira (4).csv`의 539 설명은 `계좌·구독·카드·대출·적금 이벤트 변환`이다.
연결된 Jira에서 해당 프로젝트 이슈를 조회하지 못했으므로 최신 완료 조건은 미확인이다.
기준은 `docs/API명세서/15 Cashflow ...csv`의 본인 리소스 접근·예정 이벤트 중복 반영 금지,
`docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 이벤트 저장 제약, 기획안의 공통 현금흐름 계산이다.

538과 독립된 브랜치 `S15P21D105-539-api-15`에서 작업했다. 538의 저장 타입을 import하지 않는다.

## 호출 계약

`CashflowEventCollector.collect(userId, baseDate, endDate)` → `CashflowEventCollection`

- `userId`는 인증 경계에서 `AuthenticatedUserIdResolver`로 얻은 양수 ID를 전달한다.
  HTTP 인증 검증은 541의 Controller 책임이다.
- 날짜는 둘 다 필수이고 양 끝을 포함한다. 미래 조회가 가능한 공통 `PaymentSchedulePeriodPolicy`의
  시작일 ≤ 종료일, 최대 12개월 정책을 재사용한다. horizon 기본값·상한 결정은 540에 남긴다.
- 기준일을 적금·대출 계산기의 기준 시점으로 전달한다. 현재 저장 상태를 사용하는 계산이며,
  과거 기준일 당시의 잔액·납입 상태를 복원하는 백테스트 기능은 아니다.
- 입력 오류는 기존 `InvalidRequestException`을 사용한다.
- 출력은 날짜, `dedupeKey` 오름차순이며 원본 조회 순서와 무관하다. 데이터가 없으면 빈 목록이다.
- `reasons`가 비어야 `complete()`가 true다. 누락 가능한 원본을 0원으로 확정해서는 안 된다.
  후속 예측·API는 불완전 사유를 유지하거나 계산을 거부해야 한다.
- `@Transactional(readOnly = true)`로 원본을 읽으며 저장·외부 금융망 호출은 하지 않는다.

## 원본별 변환

| 원본 | 변환 정책 | 이벤트 유형 |
| --- | --- | --- |
| 계좌 | `AccountScheduledEventSource` 구현체가 제공한 본인 예정 거래. 원본 도메인 식별자를 보존한다 | 제공자가 지정, 일반 입금은 `ACCOUNT_INCOME`, 출금은 `ACCOUNT_EXPENSE` 권장 |
| 카드 | 기존 `CardBillingStatementReader.readScheduled`의 `SCHEDULED` 청구액. 0원은 제외하며 카드 사용 건별 승인액·할부액은 별도로 더하지 않는다 | `CARD_PAYMENT` |
| 대출 | 본인 미종료 대출의 저장 `SCHEDULED` 회차 우선. 저장 회차와 계산의 일관성을 확인한 경우에만 나머지 회차 추정 | `LOAN_REPAYMENT` |
| 적금 | 기존 `SavingsScheduleCalculator` 재사용. 저장 회차가 있는 월에는 약정액을 보충하지 않음 | `SAVINGS_PAYMENT` |
| 구독 | ACTIVE, 카드 미연결 구독의 next_payment_date부터 주·월·년 반복, end_date 포함 | `SUBSCRIPTION_PAYMENT` |

계좌 제공자가 없다면 `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE`을 반환한다. **실제 계좌 예정 거래
어댑터는 아직 없다.** 현행 Account API와 transaction ERD에는 별도 예정 거래 저장 계약이 없고,
과거 거래·등록 월 소득만으로 날짜와 중복 여부가 확정된 미래 거래를 만들 수 없기 때문이다.
계좌 원본 담당자가 미래 거래 계약을 제공하면 이 인터페이스의 구현체를 등록해야 한다.
제공자 구현체는 userId를 SQL 조건에 포함해야 하며, 수집기도 반환 행 userId를 다시 검사한다.

구독은 ERD의 테이블을 JDBC로 읽는다. 현재 브랜치에는 구독 마이그레이션이 없으므로
메타데이터에서 해당 테이블이 없을 때만 `SUBSCRIPTION_SOURCE_UNAVAILABLE`을 반환한다.
권한·SQL 오류는 정상 빈 결과로 숨기지 않는다. 구독 테이블 생성은 구독 담당 작업의 선행 조건이다.

카드가 없는 구독은 직접 지출이라는 **서비스 가정**을 적용했다. 카드 연결 구독은 승인일에
출금으로 더하지 않는다. 청구서와 구독 승인을 연결하는 데이터가 없어 포함 여부를 입증할 수
없으므로 `CARD_SUBSCRIPTION_REQUIRES_BILLING_RECONCILIATION`을 반환한다.
날짜·금액이 같다는 이유로 구독 금액을 청구서에서 빼는 방식도 사용하지 않는다.

다음 결제일이 조회 종료일 이후이거나 구독 종료일이 조회 시작일 이전이면 이번 조회와
무관하므로 이벤트와 불완전 사유 모두에서 제외한다. 조회 시작·종료 당일은 포함한다.

구독 다음 결제일이 기준일보다 과거이거나 없으면 자동으로 미래로 이동시키지 않고
`SUBSCRIPTION_NEXT_DATE_UNAVAILABLE_OR_STALE`을 반환한다. next_payment_date를 원본의
미결제 다음 회차로 신뢰하며, 이력 result_code의 성공 여부를 임의로 해석하지 않는다.
월납은 계약일을 고정해 1/31 → 2/28 → 3/31, 연납은 최초 날짜 기준으로 윤년을 보정한다.
첫 회차는 next_payment_date를 그대로 사용한다.

대출은 `PAID/FAILED/OVERDUE`가 저장된 월도 추정 보충을 막는다. FAILED·OVERDUE 또는
기준일 이전 미결제 회차는 `LOAN_UNSETTLED_REPAYMENT`를 반환한다. 날짜가 지난 미납을
기준일 출금으로 옮기지 않는다. 계산 상한·일정 불가도 사유로 드러낸다.

다음 상환일이 기준일보다 과거이면 `LOAN_STALE_REPAYMENT_DATE`를 반환하고 해당 대출의
추정을 중단한다. 현재 잔액에서 과거 회차 원금을 차감한 결과를 미래 이벤트로 반환하지 않는다.
기간 내 저장 `SCHEDULED`는 알려진 일정으로 보존한다.

계산된 월에 저장 회차가 있다면 **단일 SCHEDULED 회차이며 날짜·총액이 계산과 동일한지**
확인한다. 다른 금액·날짜·상태·복수 회차 또는 계산과 매칭되지 않는 기간 내 저장 예정이 있으면
`LOAN_REPAYMENT_RECONCILIATION_REQUIRED`와 함께 해당 대출의 저장 일정만 반환한다.
원금·이자 배분과 정확한 회차 매칭 근거 없이 첫 회차 총액만 치환하면 후속 원금이 틀어지므로
추정액을 섞지 않는다. 일치하는 경우 저장 회차의 중복만 제거하고 후속 계산을 유지한다.
검증 대상 계산 일정은 조회 기간 내로 제한한다. 적금의 불완전 사유는 `SAVINGS_` 접두사로 전달한다.

## 중복 키와 538 연결

이벤트 필드는 `eventDate, type, name, direction, amount, sourceType, sourceId, dedupeKey`다.
금액은 양수 long이고 방향은 INFLOW/OUTFLOW다. 필드 길이는 ERD를 따른다.
표시명은 정적인 일반 명칭을 사용하며 계좌·카드번호와 해시·암호문을 읽어 담지 않는다.
계좌 제공자의 이름·식별자에도 민감번호를 넣으면 안 된다.

`CashflowEventKeys.key(userId, sourceType, sourceId, occurrence)`는 버전 문자열과 각 필드의
길이를 붙여 UTF-8 SHA-256 소문자 hex 64자로 만든다. 이것은 이벤트 식별용이며 민감번호
해싱이 아니다. 날짜·금액만으로 중복을 추측하지 않는다. 금액·표시명은 키에서 제외한다.

| 원본 | sourceType | sourceId | occurrence |
| --- | --- | --- | --- |
| 카드 청구 | CARD_BILLING | cardId | billingMonth (`YYYY-MM`) |
| 저장 대출 회차 | LOAN_REPAYMENT | loan_repayment_id | `stored` |
| 추정 대출 회차 | LOAN | loanId | 상환 연월 |
| 저장 적금 회차 | SAVINGS_PAYMENT | savings_payment_id | `stored` |
| 추정 적금 회차 | SAVINGS_ACCOUNT | accountId | 납입 연월 |
| 구독 회차 | SUBSCRIPTION | subscriptionId | 결제일 (`YYYY-MM-DD`) |

계좌 출금이 이미 카드·대출·적금 회차를 나타내면 위 원본 식별자를 사용해야 동일 이벤트가
한 번만 반영된다. 서로 다른 원본은 날짜·금액이 같아도 보존한다. 같은 키인데 날짜·금액·방향이
다르면 `InvalidRequestException`으로 거부한다. 임의의 첫 금액을 확정하지 않는다.
같은 키·날짜·금액·방향이면 도메인 원본을 계좌 표현보다 우선한다.

538 연결 시 DTO의 방향을 저장 enum으로 이름 매핑하고 나머지 필드를 그대로 옮긴다.
forecastId는 예측 실행 시 붙이고 `(forecast_id, dedupe_key)` 유일 제약을 유지한다.
538 Writer의 중복 방지는 최종 방어이며 회차 우선순위와 키 생성은 539 책임이다.

## 검증

`CashflowEventCollectorTest`: 원본 간 중복·충돌, 서로 다른 거래 보존, 계좌 소유권,
기간 경계, 빈 결과·미연결 구분, 카드 구독 중복 방지, 대출·적금 저장 회차 우선,
구독 월말·주·년 반복과 오래된 다음 결제일, 사용자별 중복 키 검증.

`CashflowSourceReaderTest`: H2에서 구독 테이블 미존재, 본인 ACTIVE 구독 조회,
대출 상환 본인 격리와 모든 상태 보존을 검증한다. MySQL DDL·제약 검증을 대신하지 않는다.

`CashflowEventCollectorRegressionTest`: 실제 `LoanRepaymentScheduleCalculator`로 과거
상환일의 원금 누락, 저장액 치환 후 잔액 불일치와 정상 일치 사례를 검증한다. 저장 일정 보존,
날짜 불일치, 조회 밖 회차·구독 제외와 종료일 경계도 검증한다.

실제 계좌 예정 거래 연결, 구독 마이그레이션·실데이터 연결, 카드 구독과 청구서 대사는
미완료 선행 조건이다. 저장·HTTP API 통합은 538·541 연결 후 별도 검증해야 한다.

### 2026-09-10 최초 구현 검증

- JDK 21.0.11, Gradle Wrapper 8.14.3 사용.
- 539 관련 테스트 16개 통과.
- 전체 `gradlew.bat test`: 558개 중 성공 525, 실패 30, 건너뜀 3.
- 신규 Cashflow 소스·테스트를 제외하고 별도 빌드 디렉터리에서 기준 코드를 다시 실행:
  542개 중 성공 509, 실패 30, 건너뜀 3. 실패 테스트 목록은 동일하다.
- 기존 실패: `CardQueryApiIntegrationTest.getsBenefitsAndEstimatedBenefitUsage`의
  `performanceShortfall` 기대 300000 / 실제 268000(현재 날짜에 따른 전월 거래 포함),
  `DepositSavingApiIntegrationTest` 29건의 fixture `account.currency` NOT NULL 위반.
- 이 작업에서 기존 테스트·fixture를 수정하지 않았다. 전체 테스트가 실패하므로
  `clean build` 성공을 주장하지 않는다. MySQL 원본 조회와 538 저장 통합은 미검증이다.
- 신규 파일까지 포함한 `git diff --no-index --check`와 `git diff --check`에 공백 오류 없음.

### 2026-09-10 검토 결함 수정 후 검증

- 검토에서 확인한 과거 상환일 원금 누락, 저장·추정 회차 혼합에 따른 원금 불일치,
  조회 기간 밖 카드 구독의 불완전 판정을 수정했다.
- 실제 대출 계산기를 사용하는 회귀 테스트 9개를 추가했다. 기존 16개와 합쳐 관련 25개 통과.
- 원금 1,000원·무이자·2회 상환에서 저장 첫 회차가 500원이면 합계 1,000원을 유지한다.
  저장 첫 회차가 100원이면 100원 저장 일정만 반환하고 `complete=false`와 대사 필요 사유를 표시한다.
- 과거 다음 상환일이면 저장된 미래 예정은 보존하고 잘못된 추정은 반환하지 않는다.
- 전체 테스트: 567개 중 성공 534, 실패 30, 건너뜀 3. 기준 코드의 실패 목록과 동일함을 비교했다.
  위 기존 카드·예적금 테스트 실패 외에 새 실패는 없다.
- 538 저장 통합과 실제 MySQL 원본 조회는 여전히 미검증이며 선행 조건은 앞 절을 따른다.
