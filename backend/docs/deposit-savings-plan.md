# 예·적금 공통 API 진행계획 (S15P21D105-535)

## 1. 상태와 결정 근거

2026-09-09 작성한 구현 전 계획이다. 사용자와 합의한 **로컬 시연 데이터 조회·계산** 및 **월 소득 대비 적금 납입률**을 기준으로 한다. 2026-09-10 사용자 요청으로 구현을 진행했다. 이 문서는 계획 이력이며 실제 계약·검증 결과는 [deposit-savings.md](deposit-savings.md)를 기준으로 한다.

- 로컬 Jira 내보내기의 535번 제목은 `[API 11] 예적금 공통 API`, 설명은 `목록·요약·만기예정·저축률 구현`이며 상위 이슈는 463번이다. 연결된 Jira에서 이슈 조회 권한 오류가 발생해 최신 완료 조건은 확인하지 못했다.
- 근거: 루트·백엔드 README, API 인덱스가 연결하는 일반 `11 보유한 Deposit & Savings ...csv`, ERD Markdown 및 `DODAM_ERDCLOUD_IMPORT.sql`, 기획안, 마이그레이션 README, 기존 정책 코드와 524번 seed.
- 목록 API 명세의 데이터 출처는 SSAFY다. 사용자 결정에 따라 이번 구현은 로컬 DB를 읽으며 외부 호출·거래고유번호 생성·금융망 동기화를 포함하지 않는다. 구현 문서에 이 차이를 기록하고 루트 명세는 수정하지 않는다.
- 기획안의 일반적인 저축률보다 범위가 좁다. 이번 `/rate`는 예금 신규 가입액이나 전체 순저축을 포함하지 않는 **적금 납입률**이다. 응답에도 계산 범위를 표시한다.
- 이하 기본값·정렬·DTO·기간 정책은 원본 명세에 없는 구현 제안이다. 코드 구현 시 실제 계약으로 문서화한다.

계획 작성 시 브랜치는 `S15P21D105-534-api-10-1-minus-account-api`였다. 구현 착수 시 origin을 fetch하고 develop과 같은 `4d10e73`에서 준비된 원격 `S15P21D105-535-api-11-api`를 추적하여 전환했다. 534는 미병합이나 직접 의존하지 않는다. 기존 미추적 `backend/docs/reviews/`는 보존한다. 원격 브랜치의 Jira 생성 이력은 확인하지 못했다.

## 2. 작업 범위

모두 인증이 필요한 GET API이며 `ApiResponse`를 사용한다.

| 경로 | 입력 | 결과 |
| --- | --- | --- |
| `/api/v1/deposit-savings` | `type?`, `status?`, 보완 입력 `page?`, `size?` | 예·적금 목록 `PageResponse` |
| `/api/v1/deposit-savings/summary` | 없음 | 보유 예금·적금별 개수와 원금 잔액 합계 |
| `/api/v1/deposit-savings/maturities` | `withinDays?`, 보완 입력 `page?`, `size?` | 만기 예정 목록 `PageResponse` |
| `/api/v1/deposit-savings/rate` | `yearMonth?` | 해당 월 적금 납입액, 소득 기준, 납입률 |

535에서 공통 엔티티·조회·납입 실적 집계를 만든다. 536의 예금 상세·금리비교·중도해지·만기예상, 537의 적금 상세·납입 API·일정·예상금액·가용금액·부담 계산은 포함하지 않는다. 현금흐름·전체 자산 요약의 기능 변경도 별도 범위다.

## 3. 데이터와 스키마

현재 브랜치에 `account`의 V3와 금융상품의 V10은 있으나 `deposit_saving`, `savings_payment` 마이그레이션·엔티티는 없다. 기존 상품 카탈로그 DTO와 사용자가 보유한 계좌 DTO는 구분한다.

1. `domain/depositsaving/`에 controller, service, repository, entity, dto를 추가한다.
2. ERD의 두 테이블을 새 Flyway 마이그레이션으로 추가한다. 구현 기준 develop은 V13까지이며 V14는 미병합 534에서 사용하므로 V15를 사용한다. 적용된 기존 마이그레이션은 수정하지 않는다. MySQL 검증으로 발견한 출금계좌 유형 NULL 우회는 V15 CHECK에서 보완하며 ERD와의 차이는 구현 문서에 기록한다.
3. `deposit_saving.account_id`는 부모 account와 같은 PK다. `(user_id, account_id, account_type)` FK와 DEPOSIT/SAVINGS 제약을 유지한다.
4. 적금 출금계좌는 같은 사용자의 DEMAND_DEPOSIT 계좌가 필수이고, 예금은 NULL을 허용한다. 상품 매핑은 NULL을 허용하며 상품 정보가 없다고 계좌가 조회에서 사라지지 않게 한다.
5. `savings_payment`는 SAVINGS만 허용하고 계좌별 회차 UNIQUE, 양수 금액·회차, 상태 CHECK를 유지한다. 이 테이블은 535의 납입률에 필요하므로 이번에 추가하고 537에서 재사용한다.
6. account의 기존 매핑과 충돌하지 않도록 착수 시 Account 구현 상태를 확인한다. 계좌 공통 기능을 복제하거나 Account API를 확장하지 않는다.

잔액은 기존 seed 계약대로 **원금**이다. 예금은 보유 원금, 적금은 납입 원금이며 이자를 합산하지 않는다. CLOSED 계좌의 잔액은 0이다. summary에서 account 잔액과 savings_payment 금액을 더하면 중복되므로 잔액만 집계한다.

## 4. API별 보완 계약

### 목록

- `type`: DEPOSIT/SAVINGS, 생략 시 둘 다. `status`: ACTIVE/INACTIVE/CLOSED, 생략 시 보유 상태인 ACTIVE와 INACTIVE. CLOSED는 명시 조회한다.
- 기본 page=0, size=20, 최대 size=100. 정렬은 account_id 오름차순으로 고정한다.
- 항목 필드 후보: accountId, accountName, bankCode, accountNoMasked, type, status, balance, interestRate, openingDate, expiryDate, monthlyAmount.
- expiryDate·monthlyAmount는 저장 계약에 따라 null을 허용한다. 계좌번호 원문·암호문·해시는 반환하지 않는다.
- 부모 account를 기준으로 예·적금 목록을 유지하고 상세행은 LEFT JOIN한다. 상세행이 없으면 상세 필드를 null, detailsAvailable=false로 표시해 누락 사실을 드러낸다.
- 필터와 일치하는 데이터가 없으면 빈 페이지다.

### 요약

- ACTIVE와 INACTIVE를 포함하고 CLOSED는 제외한다. 기본 목록과 동일한 계좌 집합을 사용한다.
- totalCount, totalBalance, depositCount, depositBalance, savingsCount, savingsBalance를 반환한다.
- 합계는 페이지와 무관한 전체 사용자 집계다. 데이터가 없으면 개수·잔액은 0이다.
- 현재 월 납입액·예상 이자·만기 예상 수령액은 이 API에 추가하지 않는다.

### 만기 예정

- Asia/Seoul 기준일을 사용한다. 주입 가능한 Clock으로 테스트 날짜를 고정한다.
- withinDays 기본 30, 허용 1~365. 범위는 `기준일 < expiryDate <= 기준일 + withinDays`다. 명세의 ‘기준일 이후’에 따라 당일 만기는 제외한다.
- 보유 상태 ACTIVE/INACTIVE만 조회하되 저장된 만기일 조건을 그대로 적용한다. 상태를 날짜로 자동 변경하지 않는다.
- 만기일이 없거나 상세행이 없으면 대상에서 제외한다. 지난 만기와 CLOSED도 제외한다.
- expiryDate 오름차순, accountId 오름차순. 페이지 정책은 목록과 같다.
- 목록 항목에 daysUntilMaturity를 추가한다. 잔액을 이자 포함 예상 수령액으로 표시하지 않는다.

### 적금 납입률

- 요청마다 동일한 Clock에서 서울 기준일을 한 번 구한다. YearMonthPolicy에 기준일을 받는 오버로드를 추가해 기본 연월·미래 월 검증·집계 종료일·asOfDate에 같은 기준일을 사용한다. 기존 호출은 유지한다. yearMonth는 엄격한 YYYY-MM이며 생략 시 기준일의 월, 미래 월은 거부한다.
- 과거 월은 월초부터 월말까지, 현재 월은 월초부터 기준일까지 payment_date가 포함되는 PAID 금액을 합산한다. payment_date를 납입 실적 귀속일로 사용하는 시연 데이터 가정을 명시한다.
- SCHEDULED/FAILED/SKIPPED는 제외한다. 월 약정액·현재 잔액·출금계좌 거래를 추가 합산하지 않는다.
- 현재 CLOSED/INACTIVE인 계좌의 과거 PAID 이력도 포함한다. 목록의 보유 상태 필터를 납입 이력 집계에 재사용하지 않는다.
- 분모는 financial_profile.monthlyIncome이다. 과거 소득 이력이 없으므로 조회 월에도 **현재 등록 월 소득이 일정하게 유지되었다고 가정**한다. 실제 과거 소득으로 표현하지 않는다.
- 공식: `paidAmount × 100 ÷ monthlyIncome`, 소수 둘째 자리 HALF_UP. 100%를 넘는 결과도 자르지 않는다.
- 소득 행 없음·소득 null은 rate=null, reason=INCOME_NOT_REGISTERED. 소득 0은 rate=null, reason=ZERO_INCOME. 양수 소득이고 PAID가 없으면 0.00%다.
- 금액은 Long, 비율은 BigDecimal로 처리한다. 합산은 오버플로를 감지하고 표현 범위를 넘으면 해당 금액·비율을 null, reason=AMOUNT_OUT_OF_RANGE로 표시한다. DB 장애를 빈 결과나 0으로 바꾸지 않는다. summary의 합산도 같은 범위 정책을 적용한다.
- 응답 후보: yearMonth, asOfDate, periodStart, periodEnd, paidAmount, monthlyIncome, rate, reason, calculationBasis=SAVINGS_PAID_TO_PROFILE_INCOME, incomeBasis=CURRENT_PROFILE_ASSUMED_CONSTANT.
- rate와 reason 등 계약상 nullable 필드는 응답에서 유지한다. 현재 월 결과는 월중 누적 실적이며 월말 예상치가 아니다.
- PAID 이력이 없는 경우의 0은 저장 데이터 기준이다. 데이터 적재 완료를 의미하지 않는다. 고정 seed의 이력 기간 밖에서는 실제 저축 여부를 판단할 수 없다는 제한을 기록한다.

## 5. 시연 데이터 운영

신규 대량 seed를 만들기 전에 기존 `backend/database/seeds/DODAM_DEPOSIT_SAVING_SEED.sql`을 재사용한다.

- 기존 524번 seed는 491번 사용자·계좌 seed에 의존한다. 예금 2,000개, 적금 2,000개, 납입 이력 13,000개를 생성하도록 작성돼 있다. 이는 파일 설명 기준이며 실제 DB 적재 검증은 아직 하지 않았다.
- 기준일은 2026-09-07, 과거 납입 기간은 2026년 3~8월이다. 진행 중 적금의 9월 납입은 SCHEDULED이므로 `/rate?yearMonth=2026-08`을 실적 시연 예제로 사용한다. 월을 생략한 9월 조회에서 0이 나오는 것과 구분한다.
- 실행일에 맞춰 서비스 날짜나 데이터를 몰래 이동하지 않는다. 테스트는 고정 Clock을 사용하고 시연 설명에는 seed 기준일을 적는다.
- seed 실행에는 같은 DB 세션의 계좌 자연키·암호화 fixture 임시 테이블이 필요하다. 기존 계약과 현재 FieldCryptor/HMAC/마스킹 구현의 호환성을 먼저 확인한다. 실제 키를 SQL에 넣거나 임시 암호문을 만들지 않는다.
- 기존 seed는 자연키 upsert 등 재실행 동작을 검토한 후 전용 로컬 테스트 DB에서 검증한다. 실행 전제와 영향을 문서화하며, 운영 DB에는 실행하지 않는다. 기존 데이터를 전체 삭제하지 않는다.
- 정상·소득 없음·0원·타인·종료 계좌·만기 당일/상한·상세행 누락 등의 작은 결정적 fixture는 `src/test/resources/sql/`에 별도로 작성한다. 기존 사용자 seed를 경계 테스트 목적으로 변경하지 않는다.
- seed는 Flyway에 포함하지 않는다. 실제 seed를 실행할 수 없는 환경이면 자동 적재가 완료된 것처럼 보고하지 않는다.

## 6. 구현 순서와 검증

1. **착수·계약:** 최신 Jira 접근과 develop, 공통 Account 코드·마이그레이션 번호 확인. 이번 결정을 구현 문서 `backend/docs/deposit-savings.md`로 구체화한다.
2. **스키마·조회 기반:** 두 테이블, 엔티티, 소유권 조건, 테스트 fixture를 작성한다. 기존 account·상품 스키마와의 참조를 검증한다.
3. **목록·요약:** 필터, 페이징, 누락 상세, 잔액 집계와 두 API의 대상 일치를 검증한다.
4. **만기 예정:** Clock, 날짜 범위, 정렬·페이지 경계를 검증한다.
5. **납입률:** PAID 월별 집계와 소득·null·반올림 계약을 검증한다.
6. **시연·전체 검증·문서:** 기존 seed 호환성, 실적 예시, 전체 테스트·빌드 및 가능한 MySQL 검증을 수행하고 남은 제한을 기록한다.

필수 검증 사례:

| 범주 | 확인 항목 |
| --- | --- |
| API | 4개 경로 정상 200, 무인증 401, 공통 응답, nullable 필드 존재 |
| 소유권 | 사용자 A/B의 목록·개수·금액·만기·납입률 격리; 연결 출금계좌도 동일 소유자 |
| 입력 | 잘못된 enum, 빈 값·공백, page 음수, size 0/100/101, withinDays 생략/0/1/365/366/문자, 연월 형식·미래 월 |
| 목록·요약 | ACTIVE/INACTIVE/CLOSED 구분, 기본 목록 전체와 summary 일치, 상세행·상품 매핑 없음, 빈 결과 |
| 만기 | 어제·오늘·내일·상한일·상한 다음 날, 만기 없음, 윤년·연도 변경, 정렬 동률과 페이지 이동 |
| 납입 | 상태 4종, 월초·월말·현재 기준일·기준일 다음 날, 여러 계좌, 종료 계좌의 과거 PAID 포함 |
| 계산 | 60만원/300만원=20.00%, 10만원/300만원=3.33%, 양수 소득·실적 없음=0.00%, 소득 null/0, 100% 초과, 합산 범위 초과 |
| 보안 | 계좌번호 원문·해시·암호문·키가 응답·검증 오류에 노출되지 않음 |
| MySQL | 신규 Flyway 적용·JPA validate, 계좌 소유권·유형 FK, 적금 출금계좌 필수, 납입 회차 중복·음수 금액 거부 |
| seed | 선행 데이터·암호화 fixture 전제, 적재 수량·8월 실적 확인, 재실행 중복과 기존 데이터 영향 |

backend에서 관련 테스트를 먼저 실행한 뒤 `./gradlew.bat test`, `./gradlew.bat clean build`를 수행한다. H2 테스트 성공과 MySQL DDL·seed 검증은 별도로 보고한다. 문서만 작성하는 이번 단계에서는 Gradle을 실행하지 않는다.

## 7. 완료 기준

- [x] 4개 API와 두 테이블이 구현되고 인증·소유권·입력·계산·빈 결과 테스트를 통과한다.
- [x] 기본값, 상태별 포함 범위, 원금 잔액, 적금 납입률의 좁은 의미와 현재 소득 유지 가정이 문서·DTO에 드러난다.
- [x] SSAFY 미연동, seed 기준일과 실적 기간, 536·537과의 경계를 문서화한다.
- [x] 기존 seed를 재사용할 수 있는 전제와 실제 검증 여부를 기록한다.
- [x] 관련·전체 테스트와 빌드 결과를 기록했다. 관련 34건 통과, 전체 테스트·clean build의 기존 카드 날짜 의존 실패는 변경 전 커밋에서도 재현했다.
- [x] 최종 git status, 관련 diff, git diff --check를 확인했다. 변경은 backend로 한정하며 기존 reviews 파일을 보존했다.

최신 Jira 완료 조건과 실행 환경의 seed 적재 가능 여부는 착수 시 확인할 사항이다. 외부 연동과 전체 순저축률은 이번 완료 조건에 포함하지 않는다.
