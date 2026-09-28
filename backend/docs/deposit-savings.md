# 예·적금 공통 조회 API (S15P21D105-535)

## 구현 범위와 데이터 출처

2026-09-10, `S15P21D105-535-api-11-api`에서 구현했다. 구현 계획은 [deposit-savings-plan.md](deposit-savings-plan.md)다.

사용자 결정에 따라 로컬 시연 DB를 읽는다. 원본 API 11 일반 CSV의 목록 출처는 SSAFY지만 이번 구현은 외부 금융망 호출·동기화·거래고유번호 생성을 수행하지 않는다. 금융망 연결 여부와 관계없이 저장된 본인 데이터를 조회한다. 최신 Jira는 연결된 사이트의 권한 오류로 확인하지 못했으며 로컬 이슈 내보내기와 API·ERD를 근거로 했다.

예금 상세·금리 비교·중도해지·만기 예상은 536, 적금 상세·납입 API·일정·예상금액·가용금액·부담은 537 범위다. 기존 전체 금융자산 요약은 변경하지 않았다.

## 인증·공통 계약

- 모든 경로는 GET이며 인증이 필요하다. `AuthenticatedUserIdResolver`로 사용자 ID를 얻는다.
- 계좌·상세·납입 조인에는 사용자와 계좌 유형의 일치 조건을 적용한다. 사용자 ID를 요청으로 받지 않는다.
- 무인증은 401, 잘못된 enum·연월·페이지 입력 등은 400 공통 오류다. Controller 수치 범위 검증은 `VALIDATION_ERROR`, 서비스 정책 검증·타입 변환 실패는 기존 전역 예외 처리 계약을 따른다.
- 성공 응답은 `ApiResponse`, 목록은 `PageResponse`의 content/page/size/totalElements/totalPages/last다.
- page 기본 0, size 기본 20·최대 100. page는 0 이상, size는 1 이상이다. `page`, `size`는 원본 명세에 추가한 보완 입력이다.
- 계좌번호는 `accountNoMasked`만 반환한다. 원문·암호문·해시·외부 userKey를 조회 DTO에 포함하지 않는다.
- 금액 단위는 원이며 이자가 포함되지 않은 원금 잔액이다. 금액은 Long, 금리는 BigDecimal이다.
- DB 장애는 0이나 빈 결과로 대체하지 않는다.

## 목록 — GET /api/v1/deposit-savings

| 입력 | 기본값 | 허용 값 |
| --- | --- | --- |
| type | 예금·적금 모두 | DEPOSIT, SAVINGS |
| status | ACTIVE와 INACTIVE | ACTIVE, INACTIVE, CLOSED |
| page / size | 0 / 20 | 공통 페이지 정책 |

type/status의 빈 문자열·공백·소문자·미정의 코드는 거부한다. 정렬은 accountId 오름차순이다.

항목은 accountId, accountName, bankCode, accountNoMasked, type, status, balance, interestRate, openingDate, expiryDate, monthlyAmount, detailsAvailable을 제공한다.

account를 기준으로 상세를 LEFT JOIN한다. 상세행이 없는 계좌도 목록·요약에 포함하며 detailsAvailable=false, 상세 필드는 null이다. 상세가 있어도 expiryDate와 monthlyAmount는 null일 수 있다. 상품 카탈로그 매핑 유무는 목록에 영향을 주지 않는다. nullable 필드는 응답에 유지한다.

필터에 일치하는 계좌가 없거나 페이지가 끝을 넘으면 빈 content를 반환한다.

## 요약 — GET /api/v1/deposit-savings/summary

입력은 없다. 기본 목록과 같은 ACTIVE/INACTIVE 계좌 전체를 집계하며 CLOSED는 제외한다.

| 필드 | 의미 |
| --- | --- |
| totalCount / totalBalance | 보유 예·적금 개수 / 원금 잔액 합 |
| depositCount / depositBalance | 예금 개수 / 원금 잔액 합 |
| savingsCount / savingsBalance | 적금 개수 / 원금 잔액 합 |
| reason | 정상 null, 금액 표현 범위 초과 시 AMOUNT_OUT_OF_RANGE |

빈 결과의 개수·금액은 0이다. 개별 합계가 Long 범위를 넘으면 해당 합계만 null로 두고 나머지 합계·개수는 유지한다. 총합만 범위를 넘으면 totalBalance만 null이다. DB SUM의 DECIMAL 값을 보존한 후 범위를 확인한다.

납입 이력을 잔액에 더하지 않으며 예상 이자·예상 수령액은 제공하지 않는다.

## 만기 예정 — GET /api/v1/deposit-savings/maturities

- withinDays 기본 30, 허용 1~365. page/size는 공통 정책이다.
- 서울 기준 `asOfDate < expiryDate <= asOfDate + withinDays`를 사용한다. 원본의 ‘기준일 이후’를 당일 제외로 해석했다.
- ACTIVE/INACTIVE만 포함한다. 종료 계좌·지난 만기·당일 만기·만기일 없음·상세행 없음은 제외한다.
- 정렬은 expiryDate, accountId 오름차순이다.
- content 항목은 목록 필드와 asOfDate, daysUntilMaturity를 함께 제공한다. balance는 예상 수령액이 아니다.
- 시연 seed 날짜에 맞춰 서버 기준일을 고정하지 않는다.

## 적금 납입률 — GET /api/v1/deposit-savings/rate

yearMonth는 YYYY-MM 형식이며 생략하면 서울 기준 현재 월이다. 미래 월·빈 값·공백·형식 오류는 400이다.

요청마다 `DepositSavingClock`에서 기준일을 한 번 얻는다. `YearMonthPolicy.resolveHistorical(value, asOfDate)`가 기본 월과 미래 월을 판정하고 같은 날짜를 집계 종료일과 응답 asOfDate에도 사용한다. 기존 YearMonthPolicy 호출 계약은 유지했다.

계산식은 다음과 같다.

```text
paidAmount = 조회 기간의 본인 적금 PAID 납입액 합
rate = paidAmount × 100 ÷ 현재 등록 monthlyIncome
```

- 과거 월은 월초~월말, 현재 월은 월초~기준일이다. 양 끝 날짜를 포함한다.
- payment_date를 납입 귀속일로 사용한다. SCHEDULED/FAILED/SKIPPED와 기준일 이후 PAID는 제외한다.
- 현재 CLOSED/INACTIVE 계좌의 과거 실적도 포함한다. 계좌 잔액·월 약정액·출금 거래를 추가로 합산하지 않는다.
- 과거 소득 이력이 없으므로 현재 프로필의 월 소득이 조회 월에도 일정했다고 가정한다.
- 소수 둘째 자리 HALF_UP이며 100% 초과를 허용한다. 600,000 / 3,000,000은 20.00%, 100,000 / 3,000,000은 3.33%다.
- **예금 신규 가입액·전체 순저축을 포함하지 않는 적금 납입률이다.** API 경로는 명세의 /rate를 유지하지만 일반적인 전체 저축률로 해석하지 않는다.

| 필드 | 의미 |
| --- | --- |
| yearMonth / asOfDate | 조회 월 / 요청 기준일 |
| periodStart / periodEnd | 실제 집계 날짜 범위 |
| paidAmount / monthlyIncome | 납입 실적 합 / 현재 등록 월 소득 |
| rate / reason | 비율 / 계산 불가 사유 |
| calculationBasis | SAVINGS_PAID_TO_PROFILE_INCOME |
| incomeBasis | CURRENT_PROFILE_ASSUMED_CONSTANT |

양수 소득이고 PAID가 없으면 paidAmount=0, rate=0.00이다. 소득 행 없음·null은 INCOME_NOT_REGISTERED, 0원은 ZERO_INCOME이며 rate=null이다. 납입 합계가 Long 범위를 넘으면 paidAmount·rate=null, AMOUNT_OUT_OF_RANGE다. 여러 사유가 겹치면 금액 범위 초과를 먼저 표시한다. nullable 필드는 유지한다.

실적 0은 저장 데이터 기준이며 데이터 적재 완료를 뜻하지 않는다. 이력 기간 밖 실제 저축 여부·현재 월말 예상 저축액은 알 수 없다.

## 만기 예상 (accountId) — GET /api/v1/deposit-savings/{accountId}/maturity-forecast

2026-09-21, S15P21D105-678에서 추가했다. 예금(536) `GET /api/v1/deposits/{accountNo}/maturity-forecast`와 적금(537) `GET /api/v1/savings/{accountNo}/maturity-forecast`는 계좌번호 원문을 경로로 받는데, FE는 목록 응답의 `accountId`·`accountNoMasked`만 갖고 원문을 보관하지 않아 호출할 수 없었다. 목록 카드에서 바로 예상 만기금액을 여는 경로를 두되 계산은 두 도메인 서비스의 것을 그대로 쓴다(`DepositForecastService.maturityForecast(row, today)`, `SavingsForecastCalculator.calculate(..., early=false)`). 기존 원문 경로 두 개는 유지한다. 루트 `docs/` API 명세에는 없는 보완 경로다.

- `accountId`는 양의 정수다. 0·음수·숫자가 아닌 값은 400이다.
- 소유권은 `(user_id, account_id)`로 조회 조건에서 끝난다. 없는 계좌, 타인 계좌, 예·적금이 아닌 계좌(입출금·마이너스)는 모두 404 `RESOURCE_NOT_FOUND`다. 셋을 구분하지 않아 응답으로 타인 계좌 ID의 존재를 확인할 수 없다.
- 기준일은 `DepositSavingClock`에서 한 번만 읽어 두 도메인 계산에 같은 날짜를 넘긴다.
- 원문 계좌번호는 요청·응답·로그 어디에도 나오지 않는다. 응답에는 `accountNoMasked`만 있다.

| 필드 | 의미 |
| --- | --- |
| asOfDate / accountId / accountName / accountNoMasked / bankCode / type | 기준일과 계좌 식별. type은 DEPOSIT 또는 SAVINGS |
| status / reasons / assumptions | COMPLETE 또는 UNAVAILABLE. UNAVAILABLE이면 아래 금액 필드가 모두 null이고 reasons에 각 도메인의 사유 코드가 그대로 온다(예금 `MISSING_DEPOSIT_DETAIL`·`MISSING_EXPIRY_DATE`·`INVALID_TERMS`·`ACCOUNT_CLOSED`·`ZERO_PRINCIPAL`, 적금 `INCOMPLETE_SAVINGS_TERMS`·`PAYMENT_HISTORY_BALANCE_MISMATCH`·`FUTURE_PAID_PAYMENT`·`ACCOUNT_CLOSED` 등). assumptions도 도메인 것을 그대로 싣는다 |
| principal / interestRate / openingDate / expiryDate | 기준일 원금(예금은 잔액, 적금은 납입 원금 합. 이자 미포함)과 계약 조건. 상세행이 없으면 금리·날짜는 null |
| daysUntilMaturity | 기준일부터 만기일까지 남은 일수. 만기일이 없으면 null, 지났으면 음수 |
| expectedPrincipal | 만기 시점 원금. 예금은 principal과 같고, 적금은 납입 원금 + 만기 전 남은 예정 회차(저장 SCHEDULED 및 월 약정 추정) 합 |
| expectedInterest / tax / expectedNetInterest | 세전이자 / 이자소득세 15.4% 원 단위 절사 / 세후이자. 예금은 개설일~만기일 계약일수, 적금은 회차별 실제 예치일수의 ACT/365 단리 |
| expectedAmount | 만기 예상 수령액 = expectedPrincipal + expectedNetInterest. 추정치이며 우대금리·만기 후 이율·수수료는 반영하지 않는다 |

계산 불가를 0원으로 표현하지 않는다. 만기일이 없는 청약형 적금은 `UNAVAILABLE` / `INCOMPLETE_SAVINGS_TERMS`다. 적금 잔액이 PAID 합과 다르면 `PAYMENT_HISTORY_BALANCE_MISMATCH`로 답하고 금액을 만들지 않는다.

FE 인계(FE 담당 범위): 예적금 목록 카드에 `!` 아이콘 → 탭 시 이 API 호출 → 바텀시트에 `expectedAmount`를 크게, `expectedPrincipal`·`expectedNetInterest`·`expiryDate`·`daysUntilMaturity`를 보조로 표시. `UNAVAILABLE`이면 reasons별 문구(예: `INCOMPLETE_SAVINGS_TERMS` → "만기일이 없어 계산할 수 없어요"). "추정치" 안내를 붙인다.

## 스키마와 기존 seed

- `V15__create_deposit_saving_and_savings_payment.sql`은 ERD의 두 테이블을 추가한다. V14는 미병합 534번용으로 남겼다.
- **ERD와의 차이:** 출금계좌 CHECK의 두 번째 분기에 `withdrawal_account_type IS NOT NULL`을 보완했다. 원본은 ID가 있어도 유형이 NULL이면 평가값 UNKNOWN으로 CHECK를 통과하고 복합 FK도 생략될 수 있다. MySQL에서 확인했으며 같은 사용자 DEMAND_DEPOSIT 필수 정책을 보장하기 위한 수정이다. 루트 ERD SQL에도 같은 조건을 반영해야 하지만 이번 backend 범위에서는 수정하지 않았다.
- V3 account와 V10 financial_product가 선행한다. 같은 사용자의 계좌·출금계좌 복합 FK, SAVINGS 납입 유형, 회차 UNIQUE와 금액·상태 CHECK를 유지한다.
- 공통 Account의 쓰기 엔티티를 중복 생성하지 않고 JDBC로 필요한 컬럼만 조회한다. DepositSaving·SavingsPayment 엔티티를 추가해 다음 이슈에서 재사용할 수 있게 했다.
- 기존 `DODAM_DEPOSIT_SAVING_SEED.sql`은 수정하지 않았다. Flyway는 데이터를 자동 적재하지 않는다.
- 524번 seed의 선행 조건은 491번 사용자·입출금계좌와 같은 세션의 자연키·암호화 fixture 임시 테이블이다. 실제 키를 파일에 넣지 않고 FieldCryptor의 AES-GCM과 SensitiveNumberHasher를 사용한다.
- seed 주석의 ‘versioned codec’과 실제 FieldCryptor 형식에는 차이가 있다. 현재 형식은 버전 바이트 없는 `[12바이트 IV][암호문+태그]`다. 조회 API는 복호화를 수행하지 않는다.
- seed는 2026-09-07 스냅샷, 납입 실적은 2026년 3~8월, 9월은 예정 상태다. 실적 시연 예시는 `/api/v1/deposit-savings/rate?yearMonth=2026-08`이다. 이후 실제 날짜로 조회해 0이 나와도 과거 seed를 현재 실적으로 변경하지 않는다.

## 검증 방법과 결과

H2는 신규 API와 고정 날짜·인증·소유권·입력·빈 결과·계산을 검증한다. MySQL 전용 테스트는 아래 환경변수를 설정했을 때만 실행한다.

```powershell
$env:DEPOSIT_MYSQL_TEST_URL = 'jdbc:mysql://127.0.0.1:15355/dodam_535_test?rewriteBatchedStatements=true'
.\gradlew.bat test --tests '*DepositSaving*' --tests '*YearMonthPolicyTest'
```

MySQL 테스트는 localhost의 전용 빈 dodam_535_test 스키마만 허용한다. 비밀번호 없는 일회용 로컬 컨테이너를 전제로 하며 운영 환경에는 사용하지 않는다. Spring 시작 시 Flyway와 JPA validate를 수행하고 491 선행 계약을 충족하는 합성 사용자·입출금계좌를 만든 뒤 524 seed를 같은 세션에서 두 번 실행한다. seed가 COMMIT하므로 컨테이너 폐기로 정리한다. 재실행하려면 새 빈 테스트 DB를 준비한다.

2026-09-10 검증:

- 관련 테스트 34건 통과: API 통합 29, 서울 Clock 1, MySQL 통합 1, 공통 연월 정책 3.
- MySQL 8.0.46에서 Flyway V15와 JPA validate 통과. 출금계좌 소유권·유형 NULL·양쪽 NULL, 계좌 유형, 납입 회차 중복·음수 금액·미정의 상태를 거부하는 것을 확인했다.
- 기존 524 seed를 같은 세션에서 두 번 실행해 예·적금 4,000개·납입 13,000개 유지, 사용자 2의 계좌 ID·잔액 유지, 기존 입출금계좌 4,000개의 잔액 보존을 확인했다. 8월 실제 저장 PAID 집계와 서비스 응답 일치, 9월 실적 0도 확인했다.
- 전체 491 seed 자체를 실행한 검증은 아니다. 동일한 선행 자연키 계약의 합성 데이터를 준비했다. 금융상품 카탈로그는 적재하지 않았으므로 상품 연결 성공은 검증하지 않았다.
- 운영 DB와 금융망은 사용하지 않았고 검증용 컨테이너는 종료했다.
- 전체 `gradlew.bat test`: 374건 중 371건 통과, MySQL 전용 2건 생략, 기존 카드 테스트 1건 실패. 별도 MySQL 검증에서는 위 535 MySQL 테스트가 통과했다.
- `gradlew.bat clean build`: 컴파일·bootJar·jar 생성은 성공했으나 같은 카드 테스트 1건 때문에 전체 빌드는 실패했다. 전체 테스트 성공으로 보고하지 않는다.
- 실패는 `CardQueryApiIntegrationTest.getsBenefitsAndEstimatedBenefitUsage`의 performanceShortfall 기대 300,000원 / 실제 268,000원이다. 테스트가 `today.minusDays(40)`에 만드는 32,000원 거래가 2026-09-10 실행 시 전월(8월 1일)에 들어가므로 전월 실적 0이라는 기대와 어긋난다. 변경 전 `4d10e73`을 격리 복사한 동일 테스트에서도 같은 실패를 재현했다. 535 범위 밖 카드 코드는 수정하지 않았다.
- 변경 파일과 새 파일의 공백 검사를 통과했다. 커밋·push·MR·배포는 수행하지 않았다.

2026-09-21 (678) 검증:

- `DepositSavingForecastApiIntegrationTest` 12건 통과(H2, 기준일 2026-09-21 고정): 예금 COMPLETE(10,000,000·3.0%·365일 → 세전 300,000·세액 46,200·수령 10,253,800), 적금 COMPLETE(78회 납입 78,000,000 + 예정 6회 → 원금 84,000,000·세전 9,669,506·세액 1,489,103·수령 92,180,403), 만기 NULL 적금 `UNAVAILABLE/INCOMPLETE_SAVINGS_TERMS`, 상세 없는 예금 `UNAVAILABLE/MISSING_DEPOSIT_DETAIL`, 잔액 불일치 `PAYMENT_HISTORY_BALANCE_MISMATCH`, 입출금·타인·없는 ID 404, 0·음수·문자 ID 400, 무인증 401, 응답에 해시·암호문·원문 없음.
- 기존 예금(536)·적금(537)·예적금(535) API 테스트는 변경 없이 통과했다(조회 메서드 추가만 있고 원문 경로의 동작은 그대로다).
