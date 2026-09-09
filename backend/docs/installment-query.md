# Installment 조회·일정 API 구현 기준 (S15P21D105-530)

## 상태와 근거

2026-09-08 기준 목록·상세·월별 일정·향후 예정액 예측 API 4개를 구현했다.
부담률 API는 월 예상 가용금액 제공 계약이 없어 아직 구현하지 않았다. 이 문서의 부담률 절은 후속 구현 계약이다.
사용자가 승인한 방향은 저장된 할부 데이터 기준 조회·예측이며, 월소득을 예상 가용금액으로 대체하지 않는다.

- 로컬 Jira 내보내기의 530 범위: 목록·상세·월일정·부담·예측.
  사용자가 완료 조건 파일 유실을 알리고 문서 기준 판단을 요청했으므로, API 명세·기획안·ERD와 이 문서의 보완 계약으로 완료 기준을 복원한다.
  연결된 Jira 조회 실패는 기록으로 남기되 최신 완료 조건 파일 확보를 구현의 선행 조건으로 두지 않는다.
- API 기준: `docs/API명세서/9 Installment 3cdbb6ea3564803bb3f3c4a662ceebef.csv` (일반 CSV).
- 저장 기준: `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 `installment`, `card`.
- 기획 기준: 월 할부 예정금액 / 월 예상 가용금액. 기존 카드 부담률의 월소득 기준과 다르다.
- 착수 시 원격 확인 결과: develop은 `cc2604e`, 529의 `f093c22`는 미병합이며 원격 530 브랜치는 없었다.
- 목록·상세에 필요한 Card와 공통 인증·응답은 develop에 있다. 529의 단일 연월 정책은 미래 일정 전용 정책과 달라 530의 필수 의존성으로 삼지 않는다.

## 인증과 공통 응답

모든 API는 인증 필수다. `AuthenticatedUserIdResolver`로 본인 ID를 얻고,
`installment.card_id -> card.user_id` 소유권 조건을 DB 조회에 포함한다.
공통 `ApiResponse`와 목록의 `PageResponse`를 사용한다.
카드번호 원문·암호문·해시는 응답하지 않으며 화면용 번호가 필요하면 기존 마스킹 값만 사용한다.

인증 실패는 기존 401 흐름을 따른다. 상세의 타인·미존재 할부는 모두 `RESOURCE_NOT_FOUND`다.
목록에 명시한 cardId가 타인·미존재 카드이면 동일한 `RESOURCE_NOT_FOUND`를 반환한다.
본인 카드에 할부가 없는 경우는 정상 빈 페이지다.

## 엔드포인트와 입력

| Method | Endpoint | 입력 및 기본값 | 상태 |
| --- | --- | --- | --- |
| GET | `/api/v1/installments` | `cardId?` 양수, `status?`, `page=0`, `size=20` (1~100) | 구현 |
| GET | `/api/v1/installments/{installmentId}` | 양수 ID | 구현 |
| GET | `/api/v1/installments/monthly-schedule` | `startMonth?`, `endMonth?` | 구현 |
| GET | `/api/v1/installments/burden` | `yearMonth?`, 생략 시 현재 월 | 미구현 |
| GET | `/api/v1/installments/forecast` | `months=6`, 1~12 | 구현 |

목록 상태는 `ACTIVE`, `COMPLETED`, `CANCELED`, `OVERDUE`만 허용하고 생략하면 전체 상태를 조회한다.
목록 정렬은 `purchase_date DESC, installment_id DESC`로 고정한다.
입력 오류는 모두 HTTP 400이며 기존 예외 처리에 맞춰 다음 코드로 구분한다.

| 유형 | 오류 코드 |
| --- | --- |
| 숫자 변환 성공 후 양수·페이지·크기·개월 수 제약 위반 | `VALIDATION_ERROR` |
| 숫자 타입 변환 실패·범위 초과, 알 수 없는 상태 | `INVALID_REQUEST` |
| 명시한 빈 값 또는 공백뿐인 값 | `INVALID_REQUEST` |
| 연월 형식·과거 월·역전 기간·12개월 초과·날짜 상한 위반 | `INVALID_REQUEST` |

기본값은 파라미터 자체가 없을 때만 적용한다. `?months=`, `?page=`, `?cardId=`,
`?yearMonth=` 등을 Spring 기본값·null 변환으로 누락 처리하지 않도록 원본 파라미터를 확인한다.
구현된 Controller는 기본값 변환 후에도 원본 입력을 검사하여 빈 값을 거부한다.
`yearMonth` 검증은 부담률 API 후속 구현 범위이며 아직 테스트하지 않았다.

날짜 기준은 요청에서 한 번 정한 Asia/Seoul의 `asOfDate`다. 연월은 정확한 `YYYY-MM` 형식이며 연도는 0001~9999다.
파라미터를 명시한 빈 문자열은 생략으로 간주하지 않는다.

- 월별 일정에 두 값이 없으면 현재 월부터 6개월이다.
- 시작월만 있으면 해당 시작월부터 6개월, 종료월만 있으면 현재 월부터 종료월이다.
- 시작월·종료월을 모두 포함하며 최대 12개월, 시작월은 종료월 이하이고 현재 월 이전일 수 없다.
- 부담률은 현재·미래 월만 허용한다. 미래 월의 가용금액이 없으면 계산 불가다.
- forecast는 현재 월부터 요청한 개월 수를 반환한다.
- 날짜 상한을 넘어서는 기간은 입력 오류다. 회차 계산은 요청 구간 내 월만 계산해 거대한 잔여 회차 때문에 반복이 늘어나지 않도록 한다.

기간 기본값·최대값과 정렬은 원본 명세에 없는 보완 계약이다. 최신 Jira 확인 시 충돌 여부를 검토한다.

## 구현된 응답

목록 항목은 `installmentId`, `cardId`, `merchantName`, `totalAmount`, `months`,
`remainingPrincipal`, `remainingMonths`, `monthlyAmount`, `nextPaymentDate`, `status`를 제공한다.
상세에는 `feeRate`, `purchaseDate`, `firstPaymentDate`를 추가한다.
`remainingPrincipal`, `feeRate`, `nextPaymentDate`의 ERD null 가능성을 유지한다.

월별 일정과 forecast는 같은 구조·같은 계산을 사용한다.

```json
{
  "asOfDate": "2026-09-08",
  "startMonth": "2026-09",
  "endMonth": "2026-10",
  "complete": false,
  "excludedInstallmentCount": 1,
  "exclusions": [{ "reason": "OVERDUE", "count": 1 }],
  "months": [
    { "yearMonth": "2026-09", "scheduledAmount": 120000, "installmentCount": 1 },
    { "yearMonth": "2026-10", "scheduledAmount": 0, "installmentCount": 0 }
  ]
}
```

`scheduledAmount`는 계산 가능한 건의 합계다. 월은 오름차순이며 예정 건이 없는 월도 0원으로 제공한다.
할부가 전혀 없으면 `complete=true`, 제외 건수 0이고 요청한 모든 월이 0원이다.
계산 제외가 있으면 `complete=false`이므로 0원이어도 확정된 무부담을 의미하지 않는다.
제외 사유는 본인 데이터의 사유별 건수만 반환하고 할부 ID 목록을 무제한으로 반환하지 않는다.
기간에 포함되는지 자체를 알 수 없는 제외 건이 있으므로 완전성은 응답 전체에 보수적으로 적용한다.
제외 사유 순서는 아래 계산 정책의 우선순위이며 건수가 0인 사유는 배열에서 생략한다.
위 JSON은 공통 응답의 `data` 내부 예시다.

## 일정 계산 정책

현재 시점 이후 남은 예정액을 제공한다. 현재 월에 이미 납부한 금액을 포함한 월 전체 청구액이 아니다.
회차별 납부 이력이 없으므로 과거 결제 이력을 복원하지 않는다.

1. `COMPLETED`, `CANCELED`는 예측 대상이 아니며 제외 오류 건수에도 포함하지 않는다.
2. `OVERDUE`는 `OVERDUE` 사유로 계산에서 제외한다. 과거 연체액을 현재 월로 자동 이동하지 않는다.
3. `ACTIVE`의 잔여 회차가 0이면 상태 불일치(`NO_REMAINING_PAYMENTS`)로 제외한다.
4. 다음 결제일이 null이면 `MISSING_NEXT_PAYMENT_DATE`로 제외한다.
5. 구매일 > 최초 결제일 또는 다음 결제일 < 최초 결제일이면 `INCONSISTENT_PAYMENT_DATES`로 제외한다.
   ERD는 다음 결제일과 최초 결제일의 순서를 보장하지 않으므로 조회 계산에서 확인한다.
6. 다음 결제일이 기준일보다 과거이면 `STALE_NEXT_PAYMENT_DATE`로 제외한다. 같은 날 결제는 포함한다.
7. 정상 ACTIVE는 다음 결제일을 첫 남은 회차로 삼고 `remaining_months`만큼 월별 결제를 계산한다.

한 건에 여러 문제가 있으면 위 우선순위로 한 사유만 선택하여 제외 건수 중복을 막는다.
예정 금액은 저장된 `monthly_amount`다. `fee_rate`로 수수료를 추가하지 않고,
`remaining_principal`로 마지막 회차를 임의 보정하지 않는다. 잔여 원금과 총 예정 납부액은 다른 값이다.

첫 남은 결제일은 저장값을 그대로 사용한다. 이후 회차는 그 다음 결제일의 일자를 기준으로 각 대상 월의 말일까지 보정한다.
연속 `plusMonths`로 직전 보정일을 다시 사용하지 않는다(1월 31일 -> 2월 28일 -> 3월 31일).
다음 결제일 자체가 2월 말일인 경우 원래 30일/31일 결제였는지 스키마만으로 알 수 없다.
최초 결제일·현재 카드 결제일을 임의로 덮어쓰는 근거로 삼지 않고 이 한계를 문서화한다.

원 단위는 `long`, 합산은 오버플로 검출을 적용하여 음수로 뒤집힌 금액이 응답되지 않게 한다.
오버플로는 일반 서버 오류 흐름을 따르며 부분 계산 성공으로 숨기지 않는다.

## 부담률 선행 조건

확인된 코드에는 `FinancialProfile.monthlyIncome`, `fixedExpense`만 있고 월 예상 가용금액 산출 구현이 없다.
`monthlyIncome - fixedExpense`도 기획상 월 예상 가용금액과 같다는 근거가 없으므로 대체하지 않는다.
로컬 Jira 자료의 542는 `available-today` 작업이다. 일별 가용액이 월 가용액 계약을 충족한다고 가정하지 않는다.

필요 계약은 사용자·대상 월·기준일에 해당하는 **동일 기간의 할부 차감 전 예상 가용금액**과 그 계산 근거다.
분자와 분모의 기간은 현재 월이면 기준일부터 월말까지, 미래 월이면 해당 월 첫날부터 월말까지이며 양 끝을 포함한다.
할부 차감 후 가용액, 일별 잔액 합계, 현재 월 전체의 가용액을 현재 월 잔여 예정액의 분모로 대체하지 않는다.
제공 도메인은 대상 사용자·기간·산출 기준일과 할부 차감 전 값임을 보장해야 한다.
값은 있어도 이 조건을 확인할 수 없으면 가용금액 미산출과 동일하게 처리한다.
데이터 출처가 준비된 뒤 다음 응답을 구현한다.

- `asOfDate`, `yearMonth`, `periodStart`, `periodEnd`, `scheduledAmount`, `complete`, 제외 요약
- `availableAmount`: 산출값이 없으면 null
- `burdenRate`: 예정액 / 예상 가용금액 * 100, 소수 둘째 자리 HALF_UP
- `unavailableReason`: 정상은 null, 미산출은 `AVAILABLE_AMOUNT_UNAVAILABLE`,
  분모 0 이하는 `NON_POSITIVE_AVAILABLE_AMOUNT`, 일정 불완전은 `INCOMPLETE_SCHEDULE`

일정 불완전, 가용액 미산출, 분모 0 이하 순으로 우선 사유를 정하고 비율은 null로 반환한다.
정상 일정에서 예정액 0원이고 가용액이 양수이면 비율은 0이다.
항상 null을 반환하는 임시 구현을 부담률 기능 완료로 처리하지 않는다.
목록·상세·월일정·예측은 구현했지만 530 전체 완료는 가용금액 계약과 정상 계산 검증 이후다.
현재 `/burden` 경로는 등록하지 않았다. 호출하면 상세 ID 경로의 숫자 변환에서 HTTP 400 `INVALID_REQUEST`가 발생하므로 프론트엔드에서 연결하지 않는다.

## DB와 브랜치 준비

Flyway에는 V1·V2만 있고 `card`, `installment` 생성 경로가 없다.
522 시드는 기존 테이블에 데이터를 넣는 자료이며 임시 테이블 생성은 실제 도메인 스키마 마이그레이션이 아니다.
관련 마이그레이션 작업과 최소 필요 테이블을 확인한 뒤 530 범위를 결정한다.
전체 도메인 DDL 복사, 운영 DB 초기화, 루트 ERD 수정은 수행하지 않는다.

Jira 접근 실패와 원격 530 브랜치 부재를 확인한 뒤, 사용자의 즉시 작업 진행 요청에 따라
`origin/develop`에서 로컬 `S15P21D105-530-installment-api`를 생성했다.
Jira Create branch 규칙과 다른 로컬 생성 방식임을 알렸으며 Jira 연결은 남아 있다.
최신 완료 조건 파일은 유실되어 문서 기준으로 판단한다.
커밋·push 상태는 Git 이력과 원격 브랜치를 기준으로 확인한다. MR에는 부담률 미구현과 MySQL 미검증 범위를 명시한다.

## 구현 경계와 검증

- 카드 조회의 기존 JDBC 패턴에 맞춰 `InstallmentReader`와 `JdbcInstallmentReader`로 데이터 접근을 분리했다.
  목록 count·페이지·상세·집계 원본 쿼리에 카드 소유권을 적용한다.
- 조회 서비스는 `@Transactional(readOnly = true)`이며 529의 코드에 의존하지 않는다.
- `FutureYearMonthPeriodPolicy`는 과거 청구서 정책과 구분된 미래 일정 정책이다.
- `InstallmentScheduleCalculator`는 기준일을 인자로 받아 일정과 forecast에 같은 계산을 적용한다.
- 통합 테스트는 별도 H2 DB와 카드·할부 fixture를 사용한다. MySQL 운영 스키마·SQL 검증을 대체하지 않는다.
- 검증 결과: 530 관련 55개 테스트 통과 후 `gradlew.bat clean build` 성공.
  전체 134개 테스트가 실패·오류·건너뜀 없이 통과했다. 신규 파일을 포함한 공백 검사도 통과했다.
- 522 시드의 다음 결제일은 2026년 9월에 고정되어 있다. 날짜가 지나면 ACTIVE도 오래된 결제일 사유로 제외될 수 있다.
  API가 현재 날짜에 맞춰 데이터를 자동 갱신하지 않으며 시드 수정은 이번 작업에 포함하지 않는다.

## 검증된 항목

미완료 작업과 완료 조건은 [백엔드 남은 작업](remaining-work.md)에서 계속 관리한다.

- [x] 명세·기획안·ERD에서 530 완료 기준 복원 및 로컬 530 브랜치 확인
- [x] 본인 목록·상세, 카드·상태 필터, 정렬, 페이지 상·하한, 빈 결과
- [x] 무인증 401, 타인 상세·타인 카드 필터와 미존재 리소스의 동일 404
- [x] 구현한 4개 API의 잘못된 상태·ID·연월·기간·개월 수 입력
- [x] 숫자 제약 오류와 타입·빈 값·기간 오류 코드 구분 (`months=`, `page=`, `cardId=` 포함)
- [x] 완료·취소 제외, 연체·누락·오래된 결제일·잔여 0회차의 사유와 우선순위
- [x] 다음 결제일 < 최초 결제일, 구매일 > 최초 결제일 제외 및 사유 우선순위
- [x] 현재 월의 이미 지난 결제와 당일 결제 구분
- [x] 월말·윤년·연말·마지막 회차, 저장 금액 유지, 합산 오버플로
- [x] 일정과 forecast의 같은 구간 합계·건수·제외 요약 일치
- [x] 관련 테스트 후 전체 테스트
- [x] 구현 후 문서 초안 상태 갱신, diff 및 `git diff --check` 확인
