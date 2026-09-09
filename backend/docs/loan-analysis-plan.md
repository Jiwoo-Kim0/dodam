# Loan 부담·이자 계산 진행계획 (S15P21D105-532)

## 1. 문서 상태와 착수 조건

2026-09-09 구현 전에 작성하고 검토 지적을 반영한 계획이다. 이후 사용자의 구현 요청으로
`S15P21D105-532-api-10-loan`에서 구현을 진행했다. 이 문서는 계획 이력으로 유지하며
실제 구현 계약과 검증 결과는 [loan-analysis.md](loan-analysis.md)를 기준으로 한다.

- 현재 `develop`은 `22821fe`이며 531의 대출 조회·상환일정 구현을 포함한다. 직전 원격 조회에서도 같은 커밋을 확인했다.
- 계획 작성 시작 시 작업 트리는 깨끗했다. 현재 532 작업 브랜치는 없다.
- 구현 시작 시 원격 상태를 다시 확인하고 Jira의 Create branch에서 최신 develop 기준 `S15P21D105-532-api-10-loan-analysis`를 생성한다. 현재는 531에서 연쇄 분기할 필요가 없다.
- 연결된 Jira에서 532를 다시 조회했지만 “이슈가 존재하지 않거나 이를 볼 수 있는 권한이 없습니다”로 실패했다. 최신 이슈 상태·댓글·추가 완료 조건은 확인하지 못했다.
- 로컬 `SPRINT/Jira (4).csv`의 범위는 `[API 10] Loan 부담·이자 계산`, 설명은 `월상환·부담·예상이자·비용 비교 구현`이다. 이 자료는 최신 Jira를 대체하지 않는다.
- API 경로는 일반 CSV를 기준으로 한다. `_all.csv`는 별도 API로 세지 않는다.

### 근거 문서

| 자료 | 이 계획에서 사용하는 근거 |
| --- | --- |
| 루트 및 backend README | 브랜치 절차, 공통 인증·응답, 기술 환경 |
| `docs/API명세서/10 Loan 3cdbb6ea3564803eb613df84c254cb7e.csv` | 5개 API의 경로·입력·인증·제한 |
| `docs/기획안/기획안 작성 3c3bb6ea35648097a693e0e321b3e9c0.md` | 상환 부담률 = 월 대출 상환액 / 월 소득, 현재 조건 유지 시 대출별 예상 이자 및 비중 비교 |
| `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql` | 대출 조건, 실제 상환내역, 소득의 저장 구조와 null 제약 |
| [loan-query.md](loan-query.md) 및 Loan 코드 | 531의 현재 조회·계산 계약 |
| [installment-query.md](installment-query.md) 및 월 기간 정책 코드 | 미래 월 입력 검증과 기본값의 기존 사례 |
| [card-analysis-payment-settings.md](card-analysis-payment-settings.md) | 소득 미입력·0원 시 부담률 null 처리 사례 |

## 2. 구현 범위

모두 GET이며 인증된 사용자 본인 데이터에 한정한다. 응답은 기존 `ApiResponse`를 사용한다.

| 기능 | Endpoint | 명세의 입력 | 구현 목표 |
| --- | --- | --- | --- |
| 월 상환액 | `/api/v1/loans/monthly-repayments` | `startMonth?`, `endMonth?` | 월 전체 상환액의 계산 가능 여부와 남은 예정 원금·이자·상환액을 분리 제공 |
| 상환 부담률 | `/api/v1/loans/repayment-burden` | `yearMonth?` | 월 전체 상환액을 확인할 수 있을 때 현재 등록 월소득 대비 비율 제공 |
| 예상 이자비용 | `/api/v1/loans/{accountNo}/interest-forecast` | `accountNo` | 개별 대출의 남은 일정에 따른 예상 이자 |
| 전체 이자비용 | `/api/v1/loans/interest-summary` | `months?` | 같은 예측 기간에 대한 전체·월별 이자 합계 |
| 금융비용 비교 | `/api/v1/loans/cost-comparison` | `months?` | 동일 기간의 보유 대출별 이자와 비중 비교 |

533의 고금리 판정·시장금리 비교·상환 우선순위와 534의 마이너스통장은 포함하지 않는다. 금융비용 비교는 기획안의 “대출별 예상 이자비용 비교”를 적용하는 제안이며, 대환 전후 비용·중도상환수수료·카드·할부 비용까지 확장하지 않는다.

계산 결과는 저장하지 않고 기존 `loan`, `financial_profile`을 읽는다. 현재 범위에서는 새 테이블이나 Flyway 마이그레이션이 필요하지 않다. 531과 함께 들어온 V3·V10·V13 등 기존 마이그레이션 적용 여부는 MySQL 검증 시 확인한다.

## 3. 명세를 보완하는 제안 정책

이 절은 확정된 Jira 요구사항이 아니라 구현을 구체화하기 위한 **권장 기본안**이다. 원본 API 명세에는 응답 DTO, 기본 기간, 과거 월 지원 여부, 비교 항목이 없다. 구현 시 채택한 내용을 구현 문서에 명시하고, 추가 요구사항이 확인되면 먼저 이 계획과 대조한다.

### 3.1 예정 상환액과 실제 상환액의 구분

- **월 전체 상환액(`monthlyRepaymentAmount`)과 기준일 당일부터 남은 예정액(`remainingRepaymentAmount`)을 분리한다.** 부담률 분자는 월 전체 상환액만 사용한다. 이번 달 72만원을 이미 납부했다고 월 부담률이 0%로 떨어지지 않아야 한다.
- 미래 월은 현재 조건 유지 가정 아래 월 전체 예정액과 남은 예정액이 같다. 현재 월은 이미 납부한 금액·미납분·앞으로의 예정액을 중복 없이 확인해야 월 전체 금액을 알 수 있다.
- 현재 저장 구조에는 월 전체 일정 스냅샷, 상환내역 완전성 표시, 예정 회차와 납부내역의 확정 매칭 키가 없다. `PAID` 행이 있다는 사실만으로 해당 월의 전체 상환액을 확정하지 않는다. 현재 잔액으로 과거 예정액을 역산하지 않는다.
- 따라서 **이번 구현에서 본인 대출 행이 하나라도 있으면 현재 월의 전체 금액과 부담률은 null**, `MONTH_TOTAL_UNAVAILABLE`로 반환하는 보수적 기본안을 적용한다. 남은 예정액은 독립적으로 계산해 제공한다. 이 정책은 기본 조회인 현재 월 부담률을 수치로 제공하지 못하는 제한이며, 기능 완료 보고와 구현 문서에 반드시 명시한다.
- 종료 대출만 있거나 잔액이 0이어도 이번 달 납부 사실을 배제할 수 없어 위 정책을 적용한다. 본인 대출 행 자체가 없으면 저장 데이터 범위에서 월 전체 금액은 0이다. 월초 당일에도 납부 시각을 구분할 수 없으므로 같은 정책을 적용한다.
- 현재 월 이전의 `startMonth`, `yearMonth`는 400으로 처리한다. 현재 월부터 12개월 이내의 예측을 제공한다.
- 현재 월 부담률을 수치로 제공하려면 월별 일정과 실적의 완전성·회차 매칭 계약이 먼저 필요하다. 이 계약이 확보되기 전에는 `PAID` 합계와 남은 예측을 단순 합산하지 않는다. 이번 532에서 과거 내역 집계나 스키마 확장을 추가하지 않는다.
- 기획안에는 “최근 몇 개월간 상환 부담 변화 추적”도 있다. 위 기본안만으로 이 요구는 충족되지 않는다. 과거 실적까지 532에 포함한다고 확인되면 구현 전에 별도 계약을 추가한다. `PAID` 내역 집계, 실패·연체·예정 상태 제외, 원금·이자 null 처리, 월별 소득 이력 부재를 함께 해결해야 한다.
- `financial_profile`은 현재 소득만 저장한다. 미래 월 부담률도 현재 등록 소득이 유지된다는 가정이다. 과거 월 소득이나 미래 소득 예측값으로 표현하지 않는다.

### 3.2 날짜와 기간

요청마다 Asia/Seoul의 `asOfDate`를 한 번 결정해 모든 조회·계산에 전달한다. 테스트는 고정 기준일을 사용한다.

| 입력 | 권장 기본안 |
| --- | --- |
| 월 상환액에 두 월 생략 | 현재 월부터 6개월, 양 끝 포함 |
| 시작월만 지정 | 시작월부터 6개월 |
| 종료월만 지정 | 현재 월부터 종료월 |
| 두 월 지정 | 시작월 ≤ 종료월, 최대 12개월 |
| `yearMonth` 생략 | 현재 월 |
| `months` 생략 | 12개월. 기획안의 향후 1년 이자 예시를 반영 |
| `months` 지정 | 정수 1~12 |
| 예측 가능한 마지막 월 | 현재 월 + 11개월. 짧은 구간을 먼 미래로 옮기는 요청도 거부 |
| `interest-forecast` | 기간 파라미터가 없으므로 남은 만기까지, 기존 360회 제한은 명시적으로 표시 |

연월은 ASCII 숫자 `YYYY-MM`, 연도 0001~9999만 허용한다. 빈 값·공백·`2026-9`·`2026-13`·`0000-01`·날짜 상한 초과를 거부한다. 기본값은 파라미터를 생략했을 때만 적용한다.

기존 `FutureYearMonthPeriodPolicy`를 활용하되 Loan 서비스에서 예측 종료월 상한을 추가 검증한다. 이 공통 정책은 기간 길이는 제한하지만 시작월이 먼 미래인 경우는 제한하지 않는다. `months=12` 기본값은 Loan에서 전달한다. 공통 기본값 6을 바꿔 할부 계약에 영향을 주지 않는다.

`QueryPeriodPolicy`는 과거 날짜 조회용이므로 미래 예측에 직접 적용하지 않는다. `YearMonthPolicy`의 기본 현재 월 계산을 반복 호출해 요청 중 기준일이 달라지는 구조도 피한다.

### 3.3 대상 대출과 불완전 데이터

- 향후 예측의 집계 대상은 본인의 `ACTIVE`, `OVERDUE` 중 잔액이 양수인 대출이다. 잔액 0과 `COMPLETED`, `CLOSED`는 향후 비용 0으로 처리한다. 현재 월 전체 금액의 계산 가능 여부는 종료 대출을 포함한 본인 대출 행 전체로 판단한다.
- 개별 API는 먼저 소유권을 확인한다. 본인의 종료 대출은 정상 응답으로 예측액 0·빈 일정을 반환한다.
- 만기일 또는 첫 상환일을 결정할 정보가 없으면 계산 불가다. 금리 null도 무이자로 추정하지 않는다. DB에서는 금리 NOT NULL이지만 계산 입력 경계에서도 확인한다.
- 만기가 기준일보다 지났는데 잔액이 남아 있거나 다음 상환일이 과거면, 새로운 상환일과 잔액을 임의로 추정하지 않고 계산 불가 사유를 제공한다.
- `OVERDUE`이면서 유효한 미래 일정이 있는 경우 현재 금리 기준 예측은 가능하다. 연체 가산이자·미납분은 데이터 계약이 없어 포함하지 않는다는 가정을 응답과 문서에 표시한다.
- 데이터가 불완전한 대출을 조용히 제외해 전체 부담을 0으로 표시하지 않는다. 계산 가능한 합계와 계산 불가 대출 수·사유를 함께 반환한다.
- 오류는 `MISSING_MATURITY_DATE`, `MISSING_REPAYMENT_DATE`, `STALE_REPAYMENT_DATE`, `MATURED_WITH_BALANCE`, `INVALID_TERMS` 등 DTO 내부 사유 코드로 구분한다. 이것은 HTTP 오류 코드를 새로 만드는 작업이 아니다.

## 4. 계산 설계와 기존 구현의 선행 점검

### 4.1 공통 계산

`LoanRepaymentScheduleCalculator`와 `LoanRepaymentTerms`를 재사용한다. 5개 API에 상환 공식을 각각 복제하지 않는다.

- 월이율 `r = 연이율(%) / 100 / 12`.
- 회차 이자 `직전 잔액 × r`, 원 단위 HALF_UP.
- 원금균등·원리금균등·만기일시는 531의 공식을 유지하고 마지막 회차가 남은 원금을 흡수한다.
- 금액 응답은 `long`/`Long`, 금리·비율은 `BigDecimal`이다. 계산·합산 범위 초과 시에는 4.5절의 null·사유 코드 계약을 적용한다.
- 월별 `repaymentAmount = principal + interest`, 기간 총액은 반환 월별 금액의 합과 일치해야 한다.
- 조회 시작월이 다음 달이어도 현재 시점부터의 상환에 따른 잔액 감소를 계산한 뒤 해당 월만 추출한다. 시작월에 현재 잔액으로 상환을 새로 시작하지 않는다.
- 현재 금리가 남은 기간 유지되고 추가 대출·조기상환·수수료·일할계산이 없다는 모의 예측이다. 은행의 확정 청구액으로 표현하지 않는다.

### 4.2 기존 코드에서 확인한 경계 문제

| 항목 | 현재 구현과 위험 | 532에서의 처리 계획 |
| --- | --- | --- |
| 과거 다음 상환일 | `nextRepaymentDate`가 있으면 기준일과 비교하지 않고 사용 | 새 예측 진입점에서 데이터 유효성을 검사하고 계산 불가 표시 |
| 만기 경과 | 구현 문서는 과거 만기를 빈 일정으로 설명하지만 코드에는 기준일 대비 만기 검사가 없음 | 재현 테스트를 먼저 추가하고 실제 코드·문서 차이를 최소 수정으로 해소 |
| 말일 복원 | 첫 회차 날짜의 일자를 후속 회차에도 사용하므로 2월 28일 이후 31일 약정이 28일로 고정될 수 있음 | 약정 `repaymentDay`를 보존하고 매월 말일 보정. 약정일이 없을 때만 첫 회차 일자 사용 |
| 만기 월의 상환일 | 회차 수가 월 단위여서 만기일 뒤로 마지막 상환일이 나갈 수 있음 | 저장된 다음 상환일의 모순과 약정일로 추론한 날짜를 구분하고 아래 만기 보정 규칙 적용 |
| 360회 제한 | `totalInterest()`는 반환된 회차 합계이며 전체 만기 이자가 아님 | `truncated`, `coveredThrough`, `fullTermInterest`의 null 여부를 분리해 표시 |
| 계산 불가와 0원 | 현재 빈 일정만으로는 두 경우 구분이 어려움 | 532 전용 예측 결과에서 상태·사유를 추가하고 기존 DTO 구조는 유지 |

공용 계산기를 수정하면 531의 요약·상환일정 결과에도 영향을 준다. 위 경계값에 한정해 변경하고 531 테스트를 함께 실행한다. 정상 데이터의 반올림이나 목록 정렬 등 무관한 계약은 변경하지 않는다. 계산 불가 상태 추가는 우선 새 분석 서비스 경계에 둔다.

만기 보정은 다음 순서로 수행한다.

1. 잔액이 남은 대출의 만기가 기준일보다 과거면 `MATURED_WITH_BALANCE`다.
2. 저장된 `nextRepaymentDate`가 있으면 우선한다. 기준일보다 과거면 `STALE_REPAYMENT_DATE`, 만기보다 미래면 `INVALID_TERMS`다. 저장된 모순을 날짜 보정으로 숨기지 않는다.
3. 저장된 날짜가 없고 `repaymentDay`가 있으면 기준일 이후 첫 약정일을 추론한다. 이 날짜가 만기를 넘으면 **만기일에 최종 1회 상환**하도록 보정한다. 예: 기준일 9월 9일·약정일 25일·만기 9월 20일이면 첫 회차는 9월 20일이다. 만기 당일 조회도 당일 최종 회차를 포함한다.
4. 약정일도 없으면 기존 계약대로 `MISSING_REPAYMENT_DATE`다. 만기만으로 그 이전 중간 회차를 임의 구성하지 않는다.
5. 첫 회차 이후는 약정일을 보존해 월별 보정하고 마지막 회차가 만기를 넘으면 만기일로 당긴다. 일정은 날짜 오름차순이며 같은 최종 회차를 두 번 생성하지 않는다.

이 보정은 일할계산을 도입하지 않는다. 단축된 최종 회차도 기존 월이율 모형을 적용한다는 가정을 응답 `assumptions`에 표시한다.

### 4.3 부담률

`burdenRate = monthlyRepaymentAmount / 현재 등록 monthlyIncome × 100`, 소수 둘째 자리 HALF_UP. `remainingRepaymentAmount`는 분자로 대체하지 않는다.

- 월소득은 `FinancialProfileRepository.findById(userId)`로 읽는다. 조회 중 빈 프로필을 생성하지 않는다.
- 소득 미입력·행 없음이면 `INCOME_MISSING`, 0원이면 `INCOME_ZERO`로 비율 null을 반환한다. 연소득/12나 거래내역으로 대체하지 않는다.
- 현재 월 전체 금액 미확정이면 `MONTH_TOTAL_UNAVAILABLE`, 대상 대출 계산 불가면 `INCOMPLETE_LOAN_DATA`, 전체 합산 범위 초과면 `AGGREGATE_AMOUNT_OUT_OF_RANGE`로 부담률을 null 처리한다. 여러 사유가 있으면 모두 반환한다.
- 남은 예정액의 완전성과 월 전체 금액·부담률의 완전성은 각각 표시한다. 미래 월에서는 완전한 남은 예정액을 월 전체 예정액으로 사용한다.
- 양수 소득이고 본인 대출 행이 없으면 0.00이다. 미래 월에 종료 대출만 있어 향후 예정액이 0인 경우도 0.00이다. 현재 월에 종료 대출만 있는 경우는 월 전체 금액을 알 수 없어 null이다. 100% 초과도 그대로 반환한다.
- 고정지출·저축을 뺀 가용금액, DSR, 위험 등급은 제공하지 않는다. 기획안의 월소득 기준 부담률과 다른 지표다.

### 4.4 전체 이자와 비용 비교

- 동일 기준일·동일 기간에서는 전체 이자 API의 합계와 비교 API의 대출별 이자 합계가 같아야 한다.
- 금융비용 비교는 `forecastInterest DESC, loanId ASC`로 정렬한다. 현재 금리순과 비용순은 다를 수 있다.
- `interestShare = 대출별 예상 이자 / 전체 예상 이자 × 100`, 소수 둘째 자리 HALF_UP. 반올림 때문에 비중 합계가 정확히 100이 아닐 수 있다.
- 전체 이자가 0이면 비중은 null이다. 불완전한 대출이 있으면 전체 기준 비중도 null로 둔다. 알려진 일부 합계를 전체처럼 분모에 쓰지 않는다.
- 계산 불가 대출은 비교 목록 뒤에 `loanId ASC`로 반환하고 금액·비중은 null, 사유를 표시한다.
- 원금 상환은 금융비용에 포함하지 않는다. 비교 결과는 비용 현황이며 상환 우선순위 추천이 아니다.

### 4.5 계산 범위 초과와 오류 격리

- 회차 금액의 `longValueExact()` 변환 및 원금+이자 덧셈은 범위 검사를 한다. 대출별 계산 경계에서 예상 가능한 숫자 범위 초과만 `CALCULATION_AMOUNT_OUT_OF_RANGE`로 변환한다. 해당 대출은 `UNAVAILABLE`이며 실패 직전 일부 회차를 정상 대출 합계에 넣지 않는다. 다른 대출 계산은 계속한다.
- 모든 예외를 계산 불가로 삼지 않는다. DB 오류·프로그래밍 오류는 기존 전역 오류 처리로 전달한다. 숫자 범위 초과를 잡는 위치는 순수 계산과 결과 변환으로 한정한다.
- 여러 정상 대출의 합은 중간 누적에 `BigInteger` 또는 정확한 `BigDecimal`을 사용하고 DTO 변환 직전에 `Long` 범위를 확인한다. 순서에 따라 일부 합만 남기는 `long` 누적을 사용하지 않는다.
- 개별 대출의 여러 회차를 합산할 때도 같은 정확한 누적 방식을 사용한다. 기존 `LoanRepaymentSchedule.totalInterest()`와 `totalPrincipal()`의 `mapToLong(...).sum()`을 532의 합산 경로에 그대로 사용하지 않는다. 회차 자체의 계산 실패와 정상 회차들의 기간 합계 범위 초과를 분리한다.
- 대출별 금액은 모두 표현 가능하지만 집계 합이 범위를 넘으면 해당 집계의 `complete=false`, `unavailableReasons`에 `AGGREGATE_AMOUNT_OUT_OF_RANGE`를 넣는다. 해당 `known...`과 `total...` 금액은 모두 null이다. 개별 대출 항목은 유지하며 이를 `unavailableLoans`에 잘못 추가하지 않는다.
- 월별 합은 정상이고 기간 합만 초과하면 월별 금액은 유지하고 기간 합만 null 처리한다. 원금·이자·상환액 중 초과한 필드만 null로 만들되 해당 금액 묶음의 `complete`는 false다. 정상적으로 표현 가능한 다른 금액은 유지한다.
- 부담률과 이자 비중은 필요한 전체 분자·분모 중 하나라도 null이면 null이다. 비율 계산도 `BigDecimal`로 수행한다. 음수로 래핑, 최대값으로 자르기, 숫자 대신 문자열 반환은 하지 않는다.

## 5. 응답 계약 초안

아래는 `ApiResponse.data`의 제안 필드다. 구현 전 DTO 확정 시 필드명·nullable 여부를 구현 문서에 반영한다. 엔티티를 직접 직렬화하지 않는다.

### 공통 원칙

- 모든 최상위 응답에 `asOfDate`, `assumptions[]`를 제공한다. 이자 예측·요약·비교의 `calculationBasis`는 `REMAINING_SCHEDULE`, 월 상환액은 `MONTH_TOTAL_AND_REMAINING`, 부담률은 `MONTH_TOTAL`이다.
- 모든 집계 최상위 응답에 `complete`, `unavailableReasons[]`, `unavailableLoans[]`를 제공한다. 배열은 항상 존재하며 사유가 없으면 빈 배열이다. 대출 불가 항목은 `loanId`, `reasons[]`로 통일한다. 금액·부담률 등 서로 다른 완전성은 아래 하위 객체에도 표시한다.
- `known...` 금액은 계산 가능한 대출들의 정확한 부분 합이다. 계산 가능한 대출이 없으면 0이며, 부분 합 자체가 Long 범위를 넘으면 null이다. `total...` 금액은 해당 범위의 모든 대상 대출이 계산 가능하고 합이 표현 가능할 때만 숫자다.
- `assumptions[]`는 `FIXED_CURRENT_RATE`, `NO_EARLY_REPAYMENT_OR_NEW_BORROWING`, `EXCLUDES_FEES`, `MONTHLY_RATE_NOT_DAILY_PRORATED`를 기본으로 제공한다. 부담률에는 `CURRENT_INCOME_UNCHANGED`, 연체 대출이 포함되면 `EXCLUDES_OVERDUE_INTEREST_AND_ARREARS`를 추가한다. 완전성은 이 가정 안의 계산 완전성이며 은행 청구액의 확정성을 뜻하지 않는다.
- 번호 표시는 저장된 `accountNoMasked`만 사용한다. 원문·해시·암호문은 반환하지 않는다.
- 본인 대출 행이 없으면 정상 200, 금액 합계 0, 금액의 `complete=true`, 대출 목록은 빈 배열이다. 요청한 월 목록은 0원으로 채워 반환한다. 부담률 최상위의 `complete`는 소득도 필요하므로 소득 미입력·0원이면 false다.

| 응답 | 주요 필드 초안 |
| --- | --- |
| `LoanMonthlyRepaymentsResponse` | 공통 집계 필드, `calculationBasis`, `startMonth`, `endMonth`, `months: LoanMonthlyRepaymentItem[]` |
| `LoanMonthlyRepaymentItem` | `yearMonth`, `monthlyTotal: MonthlyTotalAmount`, `remaining: RemainingAmounts`, `loans: LoanRemainingItem[]` |
| `MonthlyTotalAmount` | `complete`, `unavailableReasons[]`, `monthlyRepaymentAmount: Long?` |
| `RemainingAmounts` | `complete`, `unavailableReasons[]`, `unavailableLoans[]`, `knownPrincipal: Long?`, `knownInterest: Long?`, `knownRepaymentAmount: Long?`, `totalPrincipal: Long?`, `totalInterest: Long?`, `remainingRepaymentAmount: Long?` |
| `LoanRemainingItem` | `loanId`, `accountName`, `accountNoMasked`, `status`, `reasons[]`, `principal: Long?`, `interest: Long?`, `repaymentAmount: Long?` |
| `LoanRepaymentBurdenResponse` | 공통 집계 필드, `calculationBasis`, `yearMonth`, `monthlyIncome: Long?`, `incomeBasis=CURRENT_PROFILE`, `monthlyTotal: MonthlyTotalAmount`, `remaining: RemainingAmounts`, `burdenRate: BigDecimal?` |
| `LoanInterestForecastResponse` | `asOfDate`, `assumptions[]`, `calculationBasis`, `loanId`, `accountNoMasked`, `status`, `reasons[]`, `maturityDate: LocalDate?`, `coveredThrough: LocalDate?`, `truncated`, `knownInterest: Long?`, `fullTermInterest: Long?`, `months: LoanInterestMonthItem[]` |
| `LoanInterestSummaryResponse` | 공통 집계 필드, `calculationBasis`, `startMonth`, `endMonth`, `knownInterest: Long?`, `totalInterest: Long?`, `months: LoanInterestMonthItem[]` |
| `LoanInterestMonthItem` | `yearMonth`, `complete`, `unavailableReasons[]`, `unavailableLoans[]`, `knownInterest: Long?`, `totalInterest: Long?` |
| `LoanCostComparisonResponse` | 공통 집계 필드, `calculationBasis`, `startMonth`, `endMonth`, `knownInterest: Long?`, `totalInterest: Long?`, `loans: LoanCostItem[]` |
| `LoanCostItem` | `loanId`, `accountName`, `accountNoMasked`, `institutionName`, `balance`, `interestRate`, `forecastInterest: Long?`, `interestShare: BigDecimal?`, `status`, `reasons[]` |

표의 공통 집계 필드는 `asOfDate`, `assumptions[]`, `complete`, `unavailableReasons[]`, `unavailableLoans[]`다. `?`는 JSON null 허용을 뜻하고 해당 필드를 생략하지 않는다. `status`는 계산 상태이며 대출의 ACTIVE 등 상태와 혼용하지 않는다. 사유 배열은 중복을 제거하고 코드명 오름차순, 불가 대출은 ID 오름차순, 월 항목은 연월 오름차순으로 정렬한다. 월별 대출 항목은 ID 오름차순이다.

월 상환액 최상위 `complete`는 모든 월의 `monthlyTotal.complete`와 `remaining.complete`가 true일 때만 true다. 최상위 사유는 하위 사유의 합집합이다. 현재 월 전체 금액이 미확정이어도 `remaining.complete=true`일 수 있다. `monthlyTotal`을 알 수 없다는 이유만으로 예측 가능한 대출을 `unavailableLoans`에 넣지 않는다.

부담률 최상위 `complete`는 월 전체 금액과 양수 소득으로 비율을 계산할 수 있는지 나타낸다. 이자 요약·비교는 요청 구간의 전체 이자 계산 가능 여부를 나타낸다. 하위 객체에서 정상 반환한 값을 최상위 false라는 이유로 모두 null 처리하지 않는다.

개별 예측 `status`는 `COMPLETE`, `PARTIAL`, `UNAVAILABLE`을 제안한다. 360회 초과면 `PARTIAL`, `truncated=true`, `fullTermInterest=null`이다. 불완전한 합계를 전체 만기 이자로 오해하지 않도록 한다. 정상 종료·잔액 0 대출은 `COMPLETE`, 이자 0이다.

개별 `UNAVAILABLE`은 입력 조건이나 회차 자체를 계산할 수 없는 경우이며 `knownInterest=null`, `fullTermInterest=null`, `coveredThrough=null`, `truncated=false`, `months=[]`다. 정상 회차들의 기간 합계만 범위를 초과한 경우에는 이 상태를 사용하지 않는다.

개별 `PARTIAL`은 다음과 같이 구분한다.

| 원인 | `reasons` | `truncated` | 합계와 월별 결과 |
| --- | --- | --- | --- |
| 360회 제한만 해당 | `SCHEDULE_TRUNCATED` | true | `knownInterest`는 반환 회차 합, `fullTermInterest=null`, 정상 월 목록과 `coveredThrough` 유지 |
| 정상 월별 이자의 기간 합계만 Long 초과 | `AGGREGATE_AMOUNT_OUT_OF_RANGE` | false | `knownInterest=null`, `fullTermInterest=null`, 정상 월 목록과 `coveredThrough` 유지 |
| 360회 제한과 반환 회차 합계 초과가 동시 발생 | 위 두 사유 모두 | true | 두 합계는 null, 정상 월 목록과 `coveredThrough` 유지 |

`coveredThrough`는 실제 계산한 마지막 회차 날짜이며 금액 합계의 표현 가능 여부와 독립적이다. 완전한 종료 대출은 일정이 없으므로 null이다. 개별 월 항목의 `totalInterest`는 그 월 전체의 남은 예측 회차를 포함하고 월 합이 표현 가능할 때만 제공한다. 기간 합계만 초과하면 정상 월 항목의 `complete=true`와 금액을 유지한다.

전체 이자·비교는 요청 기간 안의 완전성을 판단한다. 만기가 360회를 초과해도 요청한 향후 12개월을 전부 계산했다면 그 기간의 `complete`는 true일 수 있다.

## 6. 구조와 조회 방식

| 위치 | 예정 작업 |
| --- | --- |
| `domain/loan/controller/LoanAnalysisController` | 신규 5개 경로, 입력 검증, 인증 사용자 식별, 공통 응답 |
| `domain/loan/service/LoanAnalysisService` | 읽기 전용 트랜잭션, 대출·소득 조회, API별 결과 조립 |
| `domain/loan/service/`의 예측 결과 타입 | 정상·부분·불가 상태, 날짜 범위와 계산값 표현 |
| `LoanRepaymentScheduleCalculator` 및 관련 타입 | 위 날짜 경계 수정과 기존 공식 재사용 |
| `domain/loan/dto/response/` | 5개 응답 및 필요한 월별·대출별 항목 record |
| 기존 `LoanRepository`, `LoanSpecifications` | `ownedBy(userId)`로 일괄 조회 후 전체 대출 존재 여부와 향후 예측 대상을 분리 |
| 기존 `FinancialProfileRepository` | 부담률에 필요한 현재 소득 조회 |
| 기존 `FutureYearMonthPeriodPolicy` | 월 범위 형식·길이 검사 재사용, Loan 고유 제한은 서비스에 배치 |

서비스는 사용자 대출을 한 번에 읽고 요청 안에서 각 대출의 일정을 한 번 계산한다. 대출마다 상세 API를 호출하거나 페이지 전체를 반복 조회하지 않는다. 목록 개수 N에 대해 예측 집계는 대략 O(N × 12), 개별 만기 예측은 최대 360회로 제한한다.

읽기 요청에서 외부 SSAFY API를 호출하지 않는다. 5개 API의 명세상 출처는 자체 계산이며 기존 저장 대출 조건으로 구현할 수 있다. 데이터 최신성은 금융 데이터 동기화 시점에 의존한다. 응답의 `asOfDate`는 계산 기준일이며 마지막 동기화 시각을 뜻하지 않는다.

## 7. 인증·입력·오류 처리

- 모든 경로에 기존 Security를 적용하고 `AuthenticatedUserIdResolver`를 사용한다.
- 개별 계좌번호는 531과 같은 숫자 10~20자리, HMAC 기반 `findByUserIdAndAccountNoHash`를 사용한다.
- 타인 대출과 미존재 대출은 같은 `RESOURCE_NOT_FOUND`로 반환한다.
- 집계는 반드시 `loan.user_id = 인증 사용자`로 제한한다. 사용자 ID를 요청 파라미터로 받지 않는다.
- 연월 형식·기간 역전·과거 월·상한 초과·빈 값·숫자 변환 실패는 기존 `INVALID_REQUEST` 흐름을 사용한다.
- 숫자로 변환된 `months`의 1~12 범위 위반은 Controller 검증으로 `VALIDATION_ERROR`를 사용한다. 서비스 직접 호출도 범위를 방어한다.
- 계산 불가 데이터는 200 응답 내 상태로 구분한다. 예측 불가능한 대출이 하나 있다고 다른 대출까지 모두 500으로 실패시키지 않는다.
- 정적 경로 `interest-summary`, `cost-comparison`, `monthly-repayments`, `repayment-burden`이 기존 `/{accountNo}` 검증으로 잘못 유입되지 않는지 통합 테스트한다.
- 계좌번호가 포함되는 요청·검증 실패에서 입력값, 해시, 토큰을 로그·오류 본문에 새로 노출하지 않는다.

## 8. 구현 순서와 단계별 완료 조건

1. **기준과 계약 고정**: 최신 develop 및 532 브랜치 확인. 제안 정책을 구현 기준 문서로 옮기고 현재 월의 의미·과거 월 제외·비교 대상을 명시한다. Jira가 조회되면 추가 완료 조건과 대조한다.
2. **계산기 경계 검증**: 과거 상환일·만기 경과·2월 이후 약정일 복원·만기일 보정·360회 사례를 재현한다. 계산 불가 판정과 필요한 최소 수정을 적용하고 531 회귀 테스트를 통과시킨다.
3. **예측 결과와 기간 처리**: 기준일을 주입할 수 있는 순수 계산 경계, 월별 집계, 불완전 데이터 표현을 만든다. 생략·빈 값·기간 상한 계약을 검증한다.
4. **월 상환액과 개별 예상 이자**: 두 API를 먼저 구현해 월별 원금·이자, 만기까지의 합계, 부분 예측의 의미를 검증한다.
5. **상환 부담률**: 월 전체 금액과 `monthlyIncome`을 조합한다. 현재 월 전체 미확정·소득 없음·0원·대출 불완전·합산 범위 초과 시 null 계약을 검증한다. 남은 예정액으로 대체하지 않는다.
6. **전체 이자와 금융비용 비교**: 공통 예측 결과에서 합계·정렬·비중을 도출한다. 두 API의 기간과 총액 일치를 검증한다.
7. **전체 통합·문서화**: 인증·소유권·입력·빈 결과·회귀 테스트, 전체 Gradle 테스트 및 빌드, 가능한 MySQL 검증을 수행한다. 실제 구현 내용으로 문서 상태를 갱신한다.

각 단계가 끝나기 전 다음 API로 계산 로직을 복제하지 않는다. DTO 변경은 관련 테스트·문서와 함께 반영한다. 구현 중 531 계약에 영향을 주는 변경은 최종 diff와 결과에 따로 명시한다.

## 9. 검증 계획

### 계산 단위 테스트

| 범주 | 필수 사례 및 기대 결과 |
| --- | --- |
| 독립 수치 검산 | 잔액 1,200,000원, 연 12%, 3회 원금균등: 원금 각 400,000원, 이자 12,000/8,000/4,000원, 총 24,000원 |
| 만기일시 | 같은 조건: 이자 각 12,000원, 마지막 원금 1,200,000원, 총 이자 36,000원 |
| 원리금균등 | 수식으로 독립 산출한 기대값, 최종 잔액 0, 원금 합계 = 최초 잔액, 마지막 회차 반올림 보정 |
| 금리·잔액 | 0% 이자 0, 잔액 0, 소액 원금, 대출별 금액 범위 초과 시 해당 대출만 불가 처리 |
| 날짜 | 기준일 당일 포함, 미래 시작월 이전 상환 반영, 윤년·비윤년 2월, 29/30/31일 약정 복원 |
| 만기 | 지난 만기·당일 만기·만기 없음·360회·361회, 저장된 다음 상환일이 만기를 넘으면 INVALID_TERMS, 추론한 약정일이 만기를 넘으면 만기일 최종 1회 |
| 데이터 | 과거 nextRepaymentDate, 상환일 없음, OVERDUE 가정, 계산 불가와 진짜 0원의 구분 |
| 부담률 | 미래 월 전체 예정액 72만원·소득 280만원이면 25.71%, 현재 월에 72만원 납부 후 남은 예정액 0이어도 부담률 null, 대출 행 없음·양수 소득이면 0.00% |
| 종료 대출 | 현재 월에 종료 대출만 있으면 월 전체 금액·부담률 null, 미래 월 예정액 0이면 양수 소득에서 0.00% |
| 합산 범위 | 정상 개별 금액들의 합만 Long 초과 시 개별 항목 유지·해당 집계 금액 null, 월 합은 정상이고 기간 합만 초과하는 경우, 입력 대출 순서에 무관한 결과 |
| 개별 기간 합계 | 모든 회차 금액은 정상이나 전체 이자 합만 Long 초과하면 PARTIAL·AGGREGATE_AMOUNT_OUT_OF_RANGE·truncated=false, 두 이자 합계 null, 정상 월 목록·coveredThrough 유지 |
| 복합 부분 결과 | 361회 이상이고 반환된 360회 이자 합도 Long 초과하면 PARTIAL·두 사유·truncated=true, 정상 월 목록 유지 |
| DTO | 최상위 및 중첩 완전성·사유·null 필드 존재, 현재 월 전체 불가와 남은 예정액 정상의 동시 표현, 연체 제외·월이율 가정 반환 |
| 비교 | 같은 기간 24,000원/36,000원이면 합계 60,000원·비중 40.00/60.00%, 동률 시 ID 정렬 |
| API 간 일치 | 월 상환액 API의 monthlyTotal과 부담률 분자 일치(null 포함), 남은 예정액은 별도 비교, 전체 이자와 비교 합계 일치, 표현 가능한 월별 합계와 기간 합계 일치 |

### API 통합 테스트

- 5개 API 각각 정상 200 및 공통 응답 형태, 무인증 401.
- 사용자 A/B 데이터를 함께 적재해 집계 섞임 방지와 개별 타인·미존재 계좌의 동일 404 확인.
- 숫자 계좌번호 길이 9/10/20/21, 비숫자 입력과 민감값 미노출.
- 연월 생략·한쪽 생략·빈 문자열·공백·형식 오류·기간 역전·과거·12개월·13개월·먼 미래 요청.
- `months` 생략·1·12·0·13·음수·소수·문자·정수 범위 초과, 날짜 연도 상한.
- 대상 대출 없음, 종료 대출만 있음, 소득 행 없음, 일부 대출 계산 불가, 전체 이자 0, 잘린 만기 예측.
- 현재 월 남은 예정액에 기준일 이전 상환을 포함하지 않는지, 월 전체 금액 미확정 시 부담률을 0으로 반환하지 않는지 확인한다. 납부일 전·당일·다음 날, 종료 대출만 있는 경우를 포함한다.
- 대출 하나의 계산 범위 초과가 정상 대출까지 실패시키지 않는지, 전체 합산 범위 초과 시 200과 null·사유를 반환하는지 확인한다. DB 오류를 정상 200으로 삼키지 않는 것도 검증한다.
- 개별 예상 이자 API에서 기간 합계만 범위를 초과하면 HTTP 200과 `PARTIAL`을 반환하고 정상 월별 결과를 보존하는지 검증한다. 같은 데이터의 향후 12개월 합이 표현 가능하면 전체 이자·비교 API에서는 해당 요청 범위의 정상 금액을 반환해야 한다.
- 기존 531 목록·요약·상세·상환내역·일정의 정상 계약과 경로 매핑 회귀 검증.

### 실행과 환경별 검증

`backend/`에서 새 계산/분석 테스트와 기존 Loan 테스트를 먼저 실행한 뒤 다음을 수행한다.

```powershell
.\gradlew.bat test
.\gradlew.bat clean build
```

- H2 통합 테스트용 결정적 fixture를 `backend/src/test/resources/sql/`에 추가한다. 기존 시연 seed나 운영 DB를 수정하지 않는다.
- MySQL 검증 환경이 있으면 기존 Flyway 적용과 `ddl-auto: validate`, 저장 데이터 기반 신규 조회를 확인한다. 새 SQL이 들어가면 실제 MySQL에서도 실행한다.
- MySQL 환경이 없으면 H2 성공과 MySQL 미검증을 분리해 보고한다. 과거 531 검증 결과를 이번 변경 검증으로 대체하지 않는다.
- 문서만 작성하는 현재 단계에서는 Gradle을 실행하지 않는다. 구현 완료로 표시하지 않는다.

## 10. 문서화와 완료 기준

구현 문서는 `backend/docs/loan-analysis.md`에 작성할 예정이다. 이 계획은 계획으로 남기고, 실제 구현 계약은 별도 문서에 기록한다.

- [ ] 신규 5개 Endpoint가 구현되고 정상·입력·인증·소유권·빈 결과 테스트가 통과한다.
- [ ] 월 전체 금액과 남은 예정액을 분리하며 현재 월 부담률 미제공 제한과 현재 소득 유지 가정이 DTO와 문서에 드러난다.
- [ ] 저장된 날짜 모순과 추론된 약정일의 만기 초과를 구분하고 정상 최종 상환을 누락하지 않는다.
- [ ] DTO의 공통·중첩 필드와 null 계약이 일치하고 대출별·집계별 계산 범위 초과 처리 및 테스트가 반영된다.
- [ ] 계산 불가와 0원이 구별되고, 부분 합계를 전체 비용·부담률로 표현하지 않는다.
- [ ] 360회 제한과 금리 고정·연체 가산이자 미포함 등 계산 제한이 명시된다.
- [ ] API 간 합계가 일치하고 기존 531의 변경 영향이 검증된다.
- [ ] 관련 테스트, 전체 테스트, clean build 결과와 MySQL 검증 여부를 기록한다.
- [ ] `loan-query.md`의 계산 경계 설명을 실제 수정 결과와 맞춘다. `remaining-work.md`는 구현 완료된 항목만 반영한다.
- [ ] 원본 명세에 없는 기본값·기간 제약·응답 구조를 보완 계약으로 기록한다. 루트 docs 변경 필요사항은 별도로 보고한다.
- [ ] 최종 `git status --short --branch`, 관련 diff, `git diff --check`를 확인한다.
- [ ] 변경 범위는 backend에 한정한다. 커밋·push·MR 생성·배포는 별도 요청이 있을 때 수행한다.

남아 있는 주요 확인 사항은 Jira 최신 완료 조건, 현재 월 전체 상환액을 확정할 데이터 계약, 과거 월 상환 실적 지원 여부다. 현재 기본안은 저장된 대출 조건만으로 구현 가능하지만 현재 월 부담률은 본인 대출이 있으면 계산 불가다. 현재 월 부담률의 수치 제공·과거 부담 추세·대환 비교까지 완료한 것으로 보고하지 않는다.
