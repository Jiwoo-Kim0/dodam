# 대출 부담·이자 계산 API 구현 기준 (S15P21D105-532)

## 상태와 근거

2026-09-09, `S15P21D105-532-api-10-loan`에서 GET API 5개를 구현했다.
브랜치는 사용자가 Jira에서 생성했으며 시작 커밋은 최신 develop의 `22821fe`다.

기준은 일반 `docs/API명세서/10 Loan 3cdbb6ea3564803eb613df84c254cb7e.csv`,
기획안의 월 상환 부담·이자비용 비교, ERD의 `loan`·`financial_profile`,
검토와 보완을 거친 [진행계획](loan-analysis-plan.md)이다.
Jira 최신 이슈는 연결된 사이트에서 조회하지 못했다. API 명세에 없는 아래 기간·응답·계산 불가 정책은
이번 작업에서 채택한 보완 계약이며 원본 API 명세나 ERD를 수정하지 않았다.

**현재 월 전체 상환액을 확정할 데이터가 없어 대출 보유자의 현재 월 부담률은 null이다.**
미래 월의 부담률은 계산 가능하다. 따라서 현재 월 부담률의 수치 제공까지 완료한 것으로 보지 않는다.

## 엔드포인트와 입력

모두 `/api/v1/loans` 하위 GET이며 본인 인증이 필요하다.

| 경로 | 입력 | 동작 |
| --- | --- | --- |
| `/monthly-repayments` | `startMonth?`, `endMonth?` | 월 전체 금액과 남은 예정 원금·이자·상환액 분리 |
| `/repayment-burden` | `yearMonth?` | 월 전체 금액 / 현재 등록 월소득 × 100 |
| `/{accountNo}/interest-forecast` | 숫자 10~20자리 계좌번호 | 남은 만기까지의 개별 예상 이자, 최대 360회 |
| `/interest-summary` | `months=12`, 1~12 | 요청 기간의 월별·전체 이자 |
| `/cost-comparison` | `months=12`, 1~12 | 동일 기간의 보유 대출별 이자·비중 비교 |

- 날짜는 요청마다 `LoanAnalysisClock`에서 한 번 얻은 Asia/Seoul의 `asOfDate`다.
- 연월은 ASCII `YYYY-MM`, 연도 0001~9999다. 현재 월 이전은 지원하지 않는다.
- 월 범위 두 값 생략 시 현재 월부터 6개월, 시작월만 있으면 그 월부터 6개월,
  종료월만 있으면 현재 월부터 종료월이다. 양 끝 포함 최대 12개월이며 종료월은 현재 월+11개월 이내다.
- 시작월만 지정했을 때 기본 6개월이 예측 상한을 넘으면 400이다. 종료월을 명시해 범위를 줄일 수 있다.
- 단일 월 생략 시 현재 월이며 같은 예측 상한을 적용한다.
- 빈 문자열·공백을 생략으로 취급하지 않는다. 미래 조회용 공통 `FutureYearMonthPeriodPolicy`를
  재사용하고 Loan의 예측 상한만 추가한다. 할부의 공통 기본값은 변경하지 않았다.

## 인증·소유권과 오류

`AuthenticatedUserIdResolver`로 사용자 ID를 얻고 모든 대출 조회에 `ownedBy(userId)` 또는
`findByUserIdAndAccountNoHash(userId, hash)`를 사용한다. 계좌번호는 기존 `SensitiveNumberHasher`로
HMAC 처리하며 원문·해시·암호문은 응답하지 않는다. 화면용 번호는 `accountNoMasked`만 제공한다.
소득은 같은 사용자 ID의 `FinancialProfileRepository`에서 읽고 조회 중 빈 프로필을 생성하지 않는다.

공통 `ApiResponse`를 사용한다. 정상 빈 결과와 계산 불가 결과는 200이고 다음 오류는 기존 흐름을 따른다.

| 조건 | 오류 |
| --- | --- |
| 무인증 | 401 |
| 개별 타인 대출·미존재 대출 | 404 `RESOURCE_NOT_FOUND`, 동일 메시지 |
| 계좌번호 형식, 숫자로 변환된 months 범위 위반 | 400 `VALIDATION_ERROR` |
| 빈 입력, 숫자 변환 실패, 연월 형식·과거·역전·상한 위반 | 400 `INVALID_REQUEST` |
| DB 오류·예상하지 못한 프로그래밍 오류 | 기존 전역 500 처리 |

## 계산 기준

향후 비용은 본인의 `ACTIVE`, `OVERDUE` 중 잔액이 양수인 대출을 집계한다.
종료·잔액 0 대출의 개별 예측은 정상 0원·빈 일정이다.
`LoanRepaymentScheduleCalculator`의 원금균등·원리금균등·만기일시 공식을 재사용한다.

- 월이율 = 현재 연이율(%) / 100 / 12. 회차 이자는 직전 잔액 × 월이율을 원 단위 HALF_UP한다.
- 마지막 회차는 남은 원금을 모두 상환한다. 조회 시작월 이전 회차도 잔액 감소에 반영한다.
- 월별·요약·비교는 요청 종료월까지만 계산한다. 그 이후의 회차 금액 오류로 현재 구간을 무효화하지 않는다.
- 금액 응답은 Long, 금리·비율은 BigDecimal이다. 부담률과 이자 비중은 소수 둘째 자리 HALF_UP한다.
- 금융비용은 이자이며 원금은 포함하지 않는다. 대환·시장금리 비교나 상환 우선순위 추천은 533 범위다.

다음 가정은 모든 응답의 `assumptions` 배열에 제공한다.

| 코드 | 의미 |
| --- | --- |
| `FIXED_CURRENT_RATE` | 현재 금리 유지 |
| `NO_EARLY_REPAYMENT_OR_NEW_BORROWING` | 추가 대출·조기상환 없음 |
| `EXCLUDES_FEES` | 수수료 제외 |
| `MONTHLY_RATE_NOT_DAILY_PRORATED` | 월이율 모형, 단축된 최종 회차도 일할계산하지 않음 |
| `CURRENT_INCOME_UNCHANGED` | 부담률에서 현재 등록 소득 유지 |
| `EXCLUDES_OVERDUE_INTEREST_AND_ARREARS` | 연체 대출이 있으면 가산이자·미납분 미포함 |

이 결과는 은행의 확정 청구액이 아니다. `complete`는 위 가정 안에서의 계산 완전성을 뜻한다.

## 월 전체 금액과 부담률

`monthlyTotal.monthlyRepaymentAmount`와 `remaining.remainingRepaymentAmount`는 다르다.
현재 월의 남은 예정액은 기준일 당일 이후만 포함하며, 미래 월은 해당 월 전체 예정액이다.

현재 저장 구조에는 월별 일정 스냅샷·상환내역 완전성 표시·회차 매칭 키가 없다.
현재 잔액으로 과거를 역산하거나 PAID 내역과 예측을 단순 합산하지 않는다.
본인 대출 행이 하나라도 있으면 현재 월 `monthlyTotal`은 `MONTH_TOTAL_UNAVAILABLE`과 null이다.
종료된 대출만 있어도 이번 달 납부를 배제할 수 없어 같은 정책을 적용한다.
대출 행 자체가 없으면 저장 데이터 기준 월 전체 금액은 0이다.

미래 월은 완전한 남은 예정액을 월 전체 금액으로 사용한다.
`burdenRate`는 월 전체 금액과 양수 월소득을 모두 확보했을 때만 계산한다.
소득 없음은 `INCOME_MISSING`, 0원은 `INCOME_ZERO`, 일부 예측 불가는 `INCOMPLETE_LOAN_DATA`다.
비율 100% 초과를 자르지 않으며 연소득/12, 거래내역 또는 가용금액으로 소득을 대체하지 않는다.

## 일정 보완과 계산 불가

531과 공유하는 계산기에 다음 날짜 보완을 반영했다. 기존 요약·상환일정에도 이 보완이 적용된다.

- 과거 만기는 빈 일정으로 처리한다. 기존 코드가 기준일 대신 첫 상환월과 만기월만 비교하던 차이를 수정했다.
- 저장된 다음 상환일을 첫 회차로 유지하고 이후에는 약정 상환일을 보존해 말일 보정한다.
  2월 28일 다음 회차가 31일 약정이면 3월 31일로 복원한다.
- 저장된 다음 상환일이 만기 이후면 보정하지 않는다. 약정일로 추론한 첫 날짜가 만기를 넘으면 만기일에 최종 1회 상환한다.
- 이후 마지막 회차도 만기일을 넘지 않도록 보정한다. 만기 당일은 포함한다.

532의 `LoanForecastCalculator`는 계산에 앞서 다음 사유를 구분한다.
531의 기존 DTO에는 새 상태를 추가하지 않았다.

| 사유 | 조건 |
| --- | --- |
| `MISSING_MATURITY_DATE` | 만기 없음 |
| `MISSING_REPAYMENT_DATE` | 다음 상환일과 약정일 모두 없음 |
| `STALE_REPAYMENT_DATE` | 저장된 다음 상환일이 과거 |
| `MATURED_WITH_BALANCE` | 잔액이 남았는데 만기 경과 |
| `INVALID_TERMS` | 저장된 상환일이 만기 이후, 잘못된 금리·상환 조건 |
| `CALCULATION_AMOUNT_OUT_OF_RANGE` | 회차 금액·원금+이자가 Long 범위 초과 |

이 경우 개별 `UNAVAILABLE`, 금액 null, 빈 월 목록이며 다른 대출의 계산은 계속한다.
정상 대출의 알려진 부분 합은 유지하되 전체 합계·비중은 null로 표시한다.

## 응답 구조와 합계 완전성

실제 DTO는 `domain/loan/dto/response/Loan*Response.java`에 있다.
모든 최상위 응답에 기준일과 가정을, 집계 응답에 `complete`, `unavailableReasons`, `unavailableLoans`를 제공한다.
배열은 비어도 반환하고 nullable 필드는 JSON null로 반환한다.

| 응답 | 주요 구조 |
| --- | --- |
| 월 상환액 | `months[{yearMonth, monthlyTotal, remaining, loans}]` |
| 부담률 | `monthlyIncome`, `monthlyTotal`, `remaining`, `burdenRate`, `incomeBasis=CURRENT_PROFILE` |
| 개별 이자 | `status`, `reasons`, `maturityDate`, `coveredThrough`, `truncated`, `knownInterest`, `fullTermInterest`, `months` |
| 전체 이자 | `knownInterest`, `totalInterest`, `months[{yearMonth, complete, unavailableReasons, unavailableLoans, knownInterest, totalInterest}]` |
| 비용 비교 | `knownInterest`, `totalInterest`, `loans[{loanId, accountName, accountNoMasked, institutionName, balance, interestRate, forecastInterest, interestShare, status, reasons}]` |

`remaining`은 알려진 원금·이자·상환액(`knownPrincipal`, `knownInterest`, `knownRepaymentAmount`)과
완전한 원금·이자·상환액(`totalPrincipal`, `totalInterest`, `remainingRepaymentAmount`)을 구분한다.
월 전체 금액의 불가와 남은 예정액의 정상 계산은 동시에 표현될 수 있다.

회차·대출 합계는 BigInteger로 정확하게 누적한 뒤 Long 범위를 확인한다.
기존 일정 객체의 long 합계 메서드는 532 합산에서 사용하지 않는다.
합계만 범위를 넘으면 `AGGREGATE_AMOUNT_OUT_OF_RANGE`와 해당 합계 null을 반환하고 정상 월·대출 항목을 유지한다.
알려진 부분 합 자체가 범위를 넘을 때도 `known...`은 null이다. 이를 계산 불가 대출로 추가하지 않는다.

개별 이자의 부분 결과는 다음과 같다.

| 경우 | 상태·사유 | 결과 |
| --- | --- | --- |
| 최대 360회로 잘림 | `PARTIAL`, `SCHEDULE_TRUNCATED` | truncated=true, fullTermInterest=null, 반환 회차 합 유지 |
| 기간 합계만 Long 초과 | `PARTIAL`, `AGGREGATE_AMOUNT_OUT_OF_RANGE` | truncated=false, 두 합계 null, 정상 월·coveredThrough 유지 |
| 두 조건 동시 발생 | `PARTIAL`, 두 사유 | truncated=true, 두 합계 null, 정상 월·coveredThrough 유지 |

개별 만기 예측은 실제 계산 회차가 있는 월만 반환하며 최대 360개 월이다.
월 범위 집계는 요청한 월을 0원으로 채워 반환한다. 무이자 등 전체 이자가 0이면 비용 비중은 null이다.
합계나 대상 데이터가 불완전한 경우도 비중은 null이다. 소수 반올림으로 비중 합이 정확히 100이 아닐 수 있다.
비용 비교는 이자 내림차순·ID 오름차순, 불가 항목은 뒤에 ID 오름차순이다.
월 목록은 연월, 월별 대출·불가 대출은 ID, 사유와 가정은 코드명 오름차순이다.

## 데이터 출처와 검증

저장된 대출·현재 소득만 읽고 계산 결과를 저장하지 않는다. 새 스키마나 외부 SSAFY 호출은 없다.
`asOfDate`는 계산 기준일이며 금융망 동기화 시각을 뜻하지 않는다.

- Loan 계산기·분석 서비스·H2 API 통합·기존 531 회귀 테스트 통과.
- 전체 `test`: 238개 통과, 실패·오류·건너뜀 0개. Loan 관련 89개(분석 H2 API 34,
  분석 서비스 24, 기존 조회 API 16, 일정 계산기 14, MySQL 통합 1)를 포함한다.
- MySQL 8.0.46 일회용 컨테이너에서 실제 Flyway 적용, 엔티티 `ddl-auto: validate`,
  신규 5개 API의 정상 계산과 타인 대출 404를 확인했다. H2용 임시 DDL로 대체하지 않았다.
- `clean build`도 통과했다. 238개 테스트를 다시 실행했고 실행 JAR 패키징까지 완료했다.
  로컬 설치 JDK 21을 `JAVA_HOME`에 지정하고 작업 디렉터리의 Gradle 캐시를 사용했다.
  검증 명령은 `gradlew.bat test --offline --no-daemon`, `gradlew.bat clean build --offline --no-daemon`이며
  위 MySQL 테스트 URL을 해당 실행 프로세스에만 설정했다. 검증용 컨테이너는 종료·정리했다.
- MySQL 선택 테스트는 `LOAN_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_532_test`일 때만 실행한다.
  로컬 일회용 MySQL의 빈 비밀번호 root 테스트 계정만 사용하며 실제 Flyway와 `ddl-auto: validate`를 적용한다.
  운영 DB를 연결하지 않는다. fixture는 테스트용 암호화를 사용하고 테스트 트랜잭션으로 롤백한다.
- 과거 부담 추세, 현재 월 전체 상환액의 확정, 외부 금융망 데이터 최신성은 검증 완료 범위가 아니다.
