# 백엔드 남은 작업

마지막 갱신: 2026-09-14

백엔드 작업 중 확인한 미완료 항목을 이 파일에 계속 기록한다. 현재 등록 범위는 S15P21D105-499, 500, 530, 532, 533, 535, 536, 537, 538, 542와 501~514, 516~518, 540~551이다.

## S15P21D105-535 — 예·적금 ERD 제약 동기화

구현 계약: [deposit-savings.md](deposit-savings.md)

- [ ] 루트 ERD SQL의 `chk_deposit_saving_withdrawal_account`에 V15와 같은 `withdrawal_account_type IS NOT NULL` 조건을 반영한다.
- 근거: MySQL CHECK는 UNKNOWN을 허용한다. 원본은 출금계좌 ID가 있어도 유형 NULL로 복합 FK를 우회할 수 있어 V15에서 보완했다.
- 선행 조건: 루트 docs 수정은 사용자 명시 요청 범위가 아니므로 이번에는 변경하지 않았다.
- 완료 조건: ERD와 V15가 같은 출금계좌 NULL 조합·유형·소유권 제약을 표현한다.

## S15P21D105-536 — 예금 상세·금리비교·예상금액

구현 계약: [deposit-detail.md](deposit-detail.md)

### 중도해지이율 공시값 확보

- [ ] 상품별 공시 중도해지이율을 확보해 `DepositEarlyTerminationRatePolicy`의 구간표를 대체한다.
- 근거: 명세 11-1에 계산식이 없고 ERD `deposit_saving`에는 계약이율만 있어 중도해지이율 컬럼이 없다. 현재 값은 시중은행 관행으로 정한 **서비스 추정치**이며 응답의 `ratePolicy`·`rateTier`와 `ESTIMATED_EARLY_TERMINATION_RATE`로 드러낸다.
- 선행 조건: `financial_product_option`에 중도해지이율을 담을 컬럼이 없다. ERD 변경과 상품 동기화 범위를 팀이 정해야 한다.
- 완료 조건: 상품별 공시값으로 계산하고 추정 가정 코드를 응답에서 제거한다. 자료가 없는 상품은 추정치로 떨어진다는 사실을 응답에서 구분한다.

### 과세 구분 반영

- [ ] 비과세·세금우대·분리과세를 반영한다. 현재는 일반과세 15.4% 단일 세율이다.
- 근거: 사용자별 과세 구분을 담은 테이블이 없어 `GENERAL_INTEREST_INCOME_TAX_15_4` 하나로 계산한다.
- 선행 조건: 과세 구분 저장 위치(사용자 프로필 또는 계좌 단위)를 정해야 한다.
- 완료 조건: 과세 구분별 세액이 계산되고 적용한 구분을 응답에 표시한다.

### MySQL 검증

- [ ] 예금 조회 4개 경로를 MySQL 8.0에서 실행해 `DepositAccountReader`의 조회와 `ddl-auto: validate` 통과를 확인한다. H2 통합 테스트만 수행했다.
- 근거: 새 마이그레이션과 새 테이블이 없어 검증할 DDL 변경은 없다. 그래도 H2 통과가 MySQL 제약·타입 검증을 대신하지는 않는다.
- 선행 조건: 없다. 전용 빈 `dodam_536_test` 스키마를 쓴다.
- 완료 조건: 상품 카탈로그와 524 seed를 적재한 MySQL에서 4개 경로의 응답과 금액을 확인한다.

### 시연 데이터 실측 확인

- [ ] 금융상품 카탈로그를 적재한 환경에서 금리비교가 실제 시연 예금 4종(3.15·3.05·3.30·3.20%)에 대해 성립하는지 확인한다.
- 근거: 금리비교 테스트는 fixture 상품으로 검증했다. 카탈로그가 없으면 `COMPARISON_GROUP_UNKNOWN`으로 떨어지고 상세의 상품 필드가 null이 된다.
- 선행 조건: `DODAM_FINANCIAL_PRODUCTS_CUMULATIVE_B01_B05` seed 적재.
- 완료 조건: 시연 예금 4종의 `referenceRate`·`rateDifference`·후보 목록을 실제 데이터로 확인하고 문서의 예시를 실측값으로 갱신한다.

### 기존 테스트 실패 해소(536 범위 밖, 머지 전 확인 필요)

- [ ] `DepositSavingApiIntegrationTest` 29건이 실패해 `gradlew clean build`가 깨진다. 535 파일이라 이번에 수정하지 않았다.
- 근거: fixture의 `account` INSERT가 `currency`·`created_at`·`updated_at`을 채우지 않는데, H2가 `MinusAccountOwner` 매핑으로 `account`를 만들면 이 컬럼이 NOT NULL이 된다. `account`를 부분 매핑한 엔티티가 둘(`CardWithdrawalAccount`, `MinusAccountOwner`)이라 어느 쪽으로 DDL이 생성되는지에 따라 결과가 갈린다. `origin/develop`(`aff8056`)을 별도 worktree로 복사해 같은 실패를 재현했으므로 536이 만든 문제가 아니다.
- 선행 조건: 535 담당이 fixture를 고칠지, `account` 부분 매핑을 하나로 합칠지 정한다.
- 완료 조건: `gradlew clean build`가 통과한다. `CardQueryApiIntegrationTest.getsBenefitsAndEstimatedBenefitUsage` 1건도 같은 빌드에서 실패하며 535 문서에 기록된 날짜 의존 문제다.

## S15P21D105-537 — 적금 상세·납입·예상금액·현금흐름

구현 계약: [savings.md](savings.md)

### 예금과 적금의 세금 처리 통일

- [ ] 적금 예상금액에도 이자소득세를 반영하거나, 예금·적금 모두 세전/세후를 함께 반환하도록 맞춘다.
- 근거: 예금(536)은 15.4%를 원천징수한 세후 수령액을, 적금(537)은 세전 금액을 반환한다. 두 예상금액을 나란히 놓으면 적금이 유리해 보인다.
- 선행 조건: 두 이슈가 모두 병합돼야 한다.
- 완료 조건: 같은 원금·이율·기간에서 두 API의 세금 처리가 같고 문서의 가정 코드가 일치한다.

### 중도해지이율 정책 통합과 공시값 확보

- [ ] `SavingsEarlyTerminationRatePolicy`와 `DepositEarlyTerminationRatePolicy`를 공용 위치로 합친다.
- [ ] 상품별 공시 중도해지이율을 확보해 두 표를 대체한다.
- 근거: 같은 구간표를 두 도메인에 따로 두었다. 536과 537이 서로 다른 브랜치에서 병합될 수 있어 의존을 만들지 않았다. 현재 값은 시중은행 관행 기반 **서비스 추정치**다.
- 선행 조건: 두 이슈 병합. 공시값은 `financial_product_option`에 담을 컬럼이 없어 ERD 변경 검토가 필요하다.
- 완료 조건: 구간표가 한 곳에만 있고, 공시값이 있는 상품은 추정 가정 코드를 응답에서 제거한다.

### 남은 대출 상환 일정 반영

- [ ] 현금흐름 부담의 `remainingLoanRepaymentAmount`를 계산된 상환 일정으로 채운다.
- 근거: 현재는 `loan_repayment`의 `SCHEDULED` 행만 더한다. 스키마 CHECK는 그 상태를 허용하지만 현재 seed에 예정 회차가 없다면 항상 0이 되어 월 중반의 부담이 실제보다 작게 나온다.
- 선행 조건: 대출 도메인(532·533)의 `LoanRepaymentScheduleCalculator`를 적금에서 쓸지, 명세 15 Cashflow가 통합 계산을 소유할지 정해야 한다.
- 완료 조건: 저장된 실적과 계산된 예정이 중복 없이 합산되고 그 근거가 응답에 표시된다.

### MySQL 검증

- [ ] 적금 9개 경로를 MySQL 8.0에서 실행해 조회와 `ddl-auto: validate` 통과를 확인한다. H2 통합 테스트만 수행했다.
- 근거: 새 마이그레이션과 새 테이블이 없어 검증할 DDL 변경은 없다. `loan`·`loan_repayment`는 H2에 엔티티가 없어 테스트가 최소 테이블을 만들어 쓴다.
- 선행 조건: 없다. 전용 빈 테스트 스키마를 쓴다.
- 완료 조건: 상품 카탈로그와 524 seed를 적재한 MySQL에서 9개 경로의 응답과 금액을 확인한다.

## 기록 방식

- 이슈별로 남은 작업, 선행 조건, 담당 근거, 완료 조건을 기록한다.
- 완료한 항목은 검증 결과를 해당 구현 문서에 반영한 뒤 이 목록에서 제거한다. 완료 이력은 이 파일에 누적하지 않는다.
- API 계약·계산식은 해당 구현 문서를 기준으로 하고, 남은 작업의 진행 상태는 이 파일에서 갱신한다.
- 담당자는 확인된 자료를 근거로 기록하며 새 담당 배정을 의미하지 않는다. 불명확하면 확인 필요로 남긴다.
- 이 파일에 기록하는 것만으로 이슈 범위를 축소하거나 커밋·push·MR 생성·배포를 승인한 것으로 보지 않는다.

## S15P21D105-499 — Finance Data 동기화

구현 계약: [finance-data.md](finance-data.md)

### 도메인별 동기화 구현

- [ ] `FinanceSyncScopeHandler` 구현을 도메인별로 등록한다. ACCOUNT는 501에서 `AccountSyncHandler`로 등록했다(2026-09-11). CARD·LOAN·SAVING은 구현이 없어 SKIPPED로 보고된다.
- 담당 근거: 테이블 소유 도메인이 구현한다. ACCOUNT는 BE-A(S15P21D105-501, `Account` 엔티티와 함께), CARD·LOAN·SAVING은 BE-B 영역이다. 새 담당 배정을 의미하지 않는다.
- 선행 조건: 시연 데이터를 로컬 DB에서 읽는 현재 방식과 금융망 실시간 동기화의 역할 분담을 팀이 정한다. 금융망에서 받은 행은 `source_type = 'SSAFY'`, 시연 데이터는 `SEED`로 구분해 공존시키는 것을 전제로 한다.
- 완료 조건: scope별 handler가 금융망 조회 → 로컬 upsert를 수행하고, 실행 응답의 `syncedCount`와 `last_synced_at` 갱신을 통합 테스트로 확인한다.

### MySQL 검증

- [x] V13까지 적용된 MySQL에 V4를 추가하면 `FlywayValidateException`이 나는 문제(MR 리뷰, 2026-09-09)를 `spring.flyway.out-of-order: true`(local/prod)로 해결했다. 번호 구간 분할 자체가 이 옵션을 전제로 한다. `FlywayOutOfOrderTest`로 재현·검증.
- [x] `V4__create_finance_sync.sql`을 MySQL 8.0에 적용해 `ddl-auto: validate` 통과와 `FinanceSync` 엔티티 매핑(VARCHAR(30) enum, DATETIME)을 확인한다. 2026-09-11 확인: Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용한 뒤 백엔드를 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 32개 전부 통과, `Started DodamApplication`(6.2초). 오류·경고 없음. out-of-order 적용은 리뷰어가 기존 DB에서 재확인 예정.
- [ ] `SELECT ... FOR UPDATE`(`findByUserIdForUpdate`) 기반 동시 실행 방지가 MySQL InnoDB에서 의도대로 직렬화되는지 동시 요청으로 확인한다.

## S15P21D105-500 — Finance Data 통합 요약·신용등급

구현 계약: [finance-data.md](finance-data.md)

### 금융망 실호출 대조

- [x] SSAFY 금융망 API 문서(2.3.1 공통 Header, 2.7.1 신용등급 기준 조회, 2.7.4 내 신용등급 조회)와 필드명·경로·Header 규칙을 대조해 수정했다(2026-09-09). 기준 조회는 `inquireAssetBasedCreditRatingList`(userKey 제외, REC `ratingUniqueNo/ratingName/minAssetValue/maxAssetValue`), 내 신용등급 REC에는 `creditScore`가 없다. 정상 코드는 `H0000` 하나이며 `H1000~H1010`은 오류다.
- [ ] 실제 금융망 호출로 200 응답을 확인한다. 2026-09-09 로컬에서 curl로 시도했으나 전달받은 API Key가 사용자 계정 API에서 `E4004`, 거래 API에서 `H1008`(API KEY 무효)로 거절됐다.
- 선행 조건: SSAFY 개발센터에서 유효한 앱 API Key 확인(만료·재발급 여부). 키는 레포·문서에 기록하지 않는다.
- 완료 조건: 회원 생성 → 내 신용등급 조회 → 기준 조회의 200 응답과 REC 필드를 확인한다. 차이가 있으면 record·문서·테스트 픽스처를 함께 고친다.

### 통합 요약 범위

- [ ] `deposit_saving`, `installment`, `subscription` 테이블이 마이그레이션되면 요약 포함 여부를 정한다. 현재 예·적금은 `account`의 DEPOSIT·SAVINGS 행으로만 집계하고 할부·구독은 포함하지 않는다.
- [ ] 집계 SQL(`JdbcFinanceSummaryReader`)을 MySQL 8.0에서 `PREPARE`로 확인한다. H2에서만 검증했다.

## S15P21D105-530 — Installment API

구현 계약: [installment-query.md](installment-query.md)

완료 조건 파일 유실에 따라 API 명세·기획안·ERD와 구현 문서의 보완 계약으로 판단한다.
부담률을 별도 이슈로 분리하기로 합의하지 않은 상태에서는 아래 부담률 작업이 남아 있는 동안 530 전체를 완료 처리하지 않는다.

### 월 예상 가용금액 제공 계약과 데이터

- [ ] 동일 기간의 할부 차감 전 예상 가용금액을 제공할 계산식·원천 데이터·제공 코드를 확정하고 준비한다.
- 담당 근거: 로컬 Jira 자료상 정현진의 현금흐름 영역(538~542)과 관련된다. 월 가용금액 제공 작업의 구체적인 소속 이슈는 확인 필요다.
- 선행 조건: 기존 월소득·고정지출을 그대로 가용금액으로 간주하지 않는다. 542의 `available-today`가 월 가용금액을 제공한다고 가정하거나 542 전체 완료를 필수 선행 조건으로 두지 않는다.
- 완료 조건: 사용자·기준일·조회 기간이 확인되고 할부 차감 전 값임을 보장하는 데이터를 제공한다. 현재 월은 기준일부터 월말, 미래 월은 해당 월 전체를 대상으로 한다.

### 할부 부담률 API와 테스트

- [ ] `GET /api/v1/installments/burden`을 구현하고 입력·인증·소유권·계산 검증을 완료한다.
- 담당 근거: 로컬 Jira 자료상 530 담당 정현진.
- 선행 조건: 위 가용금액 제공 계약과 데이터.
- 완료 조건: `yearMonth` 기본값·형식·빈 값·과거 월 검증, 정상 부담률, 예정액 0원, 가용금액 누락·0·음수, 불완전 일정 처리를 검증한다. 분자·분모의 기간 및 기준일 일치와 할부 차감 전 값 여부를 확인하고 관련 테스트 후 전체 테스트를 실행한다.
- 항상 null을 반환하는 임시 구현은 완료로 보지 않는다. API 구현 후 실제 계약과 검증 결과를 구현 문서에 반영한다.

### 카드·할부 스키마 준비

- [x] 누락된 카드·할부 및 참조 테이블의 마이그레이션을 작성한다. 529 브랜치에 `V10`(금융상품 3종), `V11`(`card`, `card_billing_statement`), 530 브랜치에 `V12`(`installment`)를 두고 `db/migration/README.md`에 버전 번호 할당 규칙(V1~V9 BE-A, V10~ BE-B)을 명시했다.
- [x] `account` 마이그레이션을 529 브랜치에 `V3`로 선반영했다. 이미 병합된 카드 도메인이 이 테이블을 매핑해 develop이 `Schema-validation: missing table [account]`로 기동하지 못하는 상태였고, 담당자 부재로 대기할 수 없어 ERD 정의대로 스키마만 추가했다. 계좌 조회 API는 BE-A(S15P21D105-501)에 그대로 남으며 같은 테이블을 다시 만들지 않도록 인수인계가 필요하다.
- [ ] 병합 순서를 529 -> 530으로 유지한다. `installment.card_id`가 `card`를 참조하므로 순서가 뒤집히면 V12가 실패한다.
- 담당 근거: [마이그레이션 지침](../src/main/resources/db/migration/README.md)에 따라 도메인 테이블은 도메인 작업에서 준비한다. 카드·할부는 정현진 담당 영역이며, `account`는 BE-A 소관이다. 인프라 558은 "빈 DB와 계정"까지만 보장하는 범위로 이미 완료됐다.
- 선행 조건: 시드 SQL을 실제 테이블 생성 마이그레이션으로 대체하지 않는다. 시드 적재 경로 확정은 별도 팀 결정 사항이다.
- 완료 조건: `account` 반영 후 기존 데이터가 있는 DB의 적용 방법까지 확인한다.

### MySQL 검증

- [x] 스키마·제약조건·조회 SQL을 MySQL 8.0에서 확인했다(2026-09-09). 일회용 컨테이너에 `V1`, `V2`, `V10`~`V13`과 BE-A 참조 테이블(`account`, `transaction`, `transaction_category`, ERD DDL 임시 생성)을 순서대로 적용해 테이블 16개, CHECK 57개, 외래키 20개가 생성됐다. 카드·할부의 조회 SQL 12개를 MySQL `PREPARE`로 검증했고, 조각으로 조립되는 할부 목록·건수 쿼리는 조립된 형태로 확인했다. 앱을 이 DB에 붙여 `ddl-auto: validate` 통과와 정상 기동을 확인했다.
- [ ] 데이터 기준 동작은 확인하지 못했다. 소유권 격리, 정상·빈 결과, 경계값은 검증용 데이터가 없어 H2 통합 테스트 범위에 머문다. 시드 적재 경로 확정과 암호화 픽스처 생성기가 선행 조건이다.
- 담당 근거: 530 도메인 구현 담당. 검증은 로컬 Docker MySQL 8.0 컨테이너에서 수행했고 운영 DB와 인프라 구성을 건드리지 않았다.
- 선행 조건: 안전한 검증용 데이터. 운영 자격증명을 문서에 기록하지 않는다.
- 완료 조건: 목록·상세·월별 일정·예측의 소유권 격리와 정상·빈 결과를 실제 데이터로 확인하고, 부담률 구현 후에는 해당 API도 검증한다. H2 성공으로 MySQL 검증을 대체하지 않는다.

검증 중 `card`의 `card_no_hash`, `expiry_month`가 ERD의 `CHAR`와 달리 엔티티에서 `varchar`로 기대돼 `validate`에 실패하는 문제를 확인하고 529 브랜치에서 수정했다(`columnDefinition` 명시). 530 브랜치의 `Card`에는 이 수정이 없으므로 529를 먼저 병합해야 한다.

### Jira 연결 정리

- [ ] 접근이 가능해지면 로컬 `S15P21D105-530-installment-api` 브랜치의 Jira 연결을 확인한다.
- 선행 조건: 실제 프로젝트 Jira 접근. 현재 브랜치는 Jira Create branch가 아닌 로컬 생성 방식이다.
- 완료 조건: 이슈와 브랜치의 연결을 확인한다. 유실된 완료 조건 파일 복구는 요구하지 않으며, 이 항목을 API 구현의 선행 조건으로 두지 않는다.
- MR을 작성할 때 미완료 범위와 MySQL 검증 여부를 명시한다. MR 생성 자체는 별도 요청 시 진행한다.

## S15P21D105-532 — Loan 부담·이자 계산

구현 계약: [loan-analysis.md](loan-analysis.md)

### 현재 월 전체 상환액과 부담률 수치 제공

- [ ] 월별 일정·납부내역의 완전성과 회차 매칭 계약을 확보해 현재 월 전체 상환액을 계산한다.
- 현재 동작: 대출 행이 있는 사용자의 현재 월 전체 금액·부담률은 `MONTH_TOTAL_UNAVAILABLE`과 null이다.
  남은 예정액과 미래 월 부담률은 제공한다. 이 제한은 검토한 532 계획의 보완 계약이다.
- 선행 조건: 이미 납부한 금액·미납분·앞으로의 예정액을 중복 없이 판단할 데이터.
  PAID 합계와 현재 잔액 기반 예측을 단순 합산하거나 남은 예정액을 월 전체 금액으로 대신하지 않는다.
- 완료 조건: 납부 전·당일·후에도 동일 월 전체 기준이 유지되고, 종료 대출의 당월 납부와 누락 데이터가 검증된다.
- 현재 월 수치 제공까지 완료한 것으로 보고하지 않는다. 과거 월 부담 추세의 이슈 포함 여부는 최신 Jira 확인 시 검토한다.

### Jira 최신 완료 조건 대조

- [ ] 실제 프로젝트 Jira 접근이 가능해지면 최신 532 완료 조건과 구현·남은 제한을 대조한다.
- 현재 브랜치는 사용자가 생성한 `S15P21D105-532-api-10-loan`이며 최신 develop `22821fe` 기준이다.
- 기존 계획·명세에 근거한 구현을 진행했으며 Jira 조회 실패를 기능 완료 조건 확인으로 대신하지 않는다.

## S15P21D105-533 — Loan 금리비교·상환우선순위

계획: [loan-rate-priority-plan.md](loan-rate-priority-plan.md), 구현 계약: [loan-rate-priority.md](loan-rate-priority.md)

3개 API 중 상환 우선순위만 구현했다. 아래가 남아 있는 동안 533 전체를 완료 처리하지 않는다.

### 시장금리 비교와 고금리 판정 API

- [ ] `GET /api/v1/loans/{accountNo}/rate-comparison`과 `GET /api/v1/loans/high-interest`를 구현한다.
- 현재 동작: 두 경로는 존재하지 않는다. `GET /api/v1/loans/repayment-priority`만 동작한다.
- 선행 조건: 계획 8절 4단계의 공통 비교 계산기와 전용 테스트 fixture. 계획 5절의 비교군·상환방식 대응표와 기간 밴드, 최저금리 중앙값 기준은 확정 상태다.
- 완료 조건: 계획 8절 5단계 게이트(시연 데이터에서 판정 1건 이상, 0건이면 기준 방식 재검토)를 통과하고 `rateSpreadThreshold`를 편차 분포 근거와 함께 확정한다. 확정값이 통계적 기준이 아니라 서비스 초기 임계값임을 응답과 문서에 남긴다.

### goalId 미지원

- [ ] 목표 기반 상환 우선순위 지원 여부를 최신 Jira 범위와 대조해 후속 이슈로 분리할지 정한다.
- 현재 동작: 형식이 유효한 goalId도 `INVALID_REQUEST` 400이다. 존재 여부를 조회한 것처럼 404를 반환하지 않는다.
- 선행 조건: `goal` 테이블 마이그레이션과 Goal 도메인(명세 12). 현재 저장소에 둘 다 없다. 533에 임시 테이블이나 대체 저장소를 만들지 않는다.
- 제약: ERD `goal_account`의 `chk_goal_account_type`이 예금 계좌만 허용해 목표에 대출을 연결할 수 없고, 예산 항목은 소비목표 전용 `monthly_budget`뿐이다. 목표를 반영한 우선순위는 명세 16의 `GET /api/v1/recommendations/priorities`가 소유한다.

### MySQL 검증과 미검증 분기

- [ ] 기존 `LoanAnalysisMySqlIntegrationTest`와 같은 `@EnabledIfEnvironmentVariable` 조건부 테스트를 533에도 추가한다.
- 현재 동작: 상환 우선순위는 H2로만 검증했다. MySQL 조회·매핑은 미검증이다.
- 금리 무효(`INVALID_TERMS`) 분기는 테스트로 도달할 수 없다. `sql/loan-query-test-data.sql`이 ERD의 `interest_rate >= 0` CHECK를 복제해 H2에서도 음수를 넣을 수 없다. 서비스에는 방어 코드로만 남아 있다.

### Jira 최신 완료 조건 대조

- [ ] 실제 Jira 접근이 가능해지면 최신 533 완료 조건과 구현·남은 제한을 대조한다.
- 현재 브랜치는 사용자가 생성한 `S15P21D105-533-api-10`이며 `1247b18` 기준이다. 이후 develop에 추가된 4커밋은 모두 Infra(473)로 백엔드 변경이 없다.
- 로컬 `SPRINT/Jira (4).csv`의 제목·설명만 근거로 범위를 정했다. Jira 조회 실패를 완료 조건 확인으로 대신하지 않는다.

## S15P21D105-534 — 마이너스통장 API

구현 계약: [minus-account.md](minus-account.md), 계획과 결정 배경: [minus-account-plan.md](minus-account-plan.md)

7개 엔드포인트를 모두 구현했고 전체 378건이 통과했다(H2 35건, MySQL 조건부 1건 포함).
아래 항목이 남아 있는 동안 534 전체를 완료 처리하지 않는다. 남은 항목은 모두 다른 이슈의 합의나
산출물을 선행 조건으로 하며 534에서 단독으로 끝낼 수 없다.

### finance-data sync가 로컬 상환분을 덮어쓰는 문제

- [ ] `source_type = 'SEED'` 계좌를 `POST /api/v1/finance-data/sync`(scope `ALL`/`ACCOUNT`) 대상에서 제외하는 합의를 받는다.
- 현재 동작: 상환·이자납부가 우리 DB의 `minus_account`와 `account`만 바꾸는 로컬 상태 전이다(2026-09-09 사용자 결정). 동기화가 SSAFY 기준으로 계좌를 덮어쓰면 상환 결과가 사라진다.
- 담당 근거: 동기화는 `4 Finance Data`(BE-A, dev_plan Day 3) 소관이다. 534에서 단독으로 정하거나 동기화 로직을 고치지 않는다.
- 완료 조건: 제외 규칙이 합의되고 시연 계좌에서 상환 후 동기화를 실행해도 결과가 유지되는지 확인한다. 미해결 상태로 시연하면 상환 결과가 사라질 수 있으므로 MR 시 명시한다.

### 알림 연결 규약과 실데이터 검증

- [ ] `source_type = 'MINUS_ACCOUNT'`와 `source_id = account_id` 규약을 S15P21D105-458과 합의한다.
- 현재 동작: `JdbcMinusAccountAlertReader`가 그 규약으로 `notification`을 읽기만 한다. 테이블이 없어 항상 빈 페이지다.
- 선행 조건: 458의 `notification` 마이그레이션. dev_plan상 Day 9~10이라 534(Day 4)보다 늦다. 534에서 테이블이나 알림 생성 배치를 만들지 않는다.
- 완료 조건: 규약 확정 후 실제 알림 데이터로 `unreadOnly`, 소프트 삭제 제외, 정렬을 확인한다. 규약이 바뀌면 리더의 상수만 고친다.

### 중복 요청 방지

- [ ] 상환·이자납부의 재전송 멱등성을 구현한다.
- 현재 동작: 행 잠금(`PESSIMISTIC_WRITE`)으로 동시 요청의 경쟁 조건만 막는다. 같은 요청을 두 번 보내면 두 번 처리된다. 명세 제한사항의 "중복 요청 방지"를 충족하지 못한다.
- 선행 조건: `Idempotency-Key` 헤더와 처리 결과 저장(테이블 또는 Redis)의 계약. TTL과 응답 재생 방식이 정해지지 않았다. `(user, account, amount)` 시간창 차단은 정상적인 연속 상환을 막아 채택하지 않았다.
- 완료 조건: 같은 키의 재요청이 잔액을 두 번 바꾸지 않고 첫 응답을 그대로 돌려주는지 확인한다.

### 상환의 거래원장 반영

- [ ] 상환·이자납부를 `transaction`에 기록할지 BE-A와 정한다.
- 현재 동작: 기록하지 않아 상환 직후 거래내역 조회에 그 상환이 나타나지 않는다.
- 선행 조건: 시스템 생성 거래의 `transaction_id` 규칙, `category_id`, `source_type` 계약. 명세의 유일한 거래 생성 경로인 `POST /api/v1/transactions`조차 Body에 계좌 지정 필드가 없어 규약 자체가 없다. 합의 전에 임의 규칙으로 남의 원장에 쓰지 않는다.
- 완료 조건: 기록하기로 정하면 `account.last_transaction_date`도 같은 트랜잭션에서 갱신한다(ERD 계산 기준).

### account 잔액 쓰기 방식 인수인계

- [ ] `JdbcAccountBalanceWriter`의 존재를 S15P21D105-501 담당자에게 전달한다.
- 현재 동작: `account`가 BE-A 소관이라 쓰기 엔티티를 만들지 않고 도메인 전용 JDBC writer로 UPDATE한다. 조건절에 `user_id`·`account_type`을 넣고 갱신 행 수가 1이 아니면 롤백한다.
- 완료 조건: 501이 `Account` 엔티티를 만들면 이 어댑터를 교체한다. 같은 테이블에 쓰기 엔티티를 중복 매핑하지 않는다.

### MySQL 검증

- [x] 스키마와 상환 정합을 MySQL 8.0에서 확인했다(2026-09-09). `MinusAccountMySqlIntegrationTest`를 532와 같은 `@EnabledIfEnvironmentVariable` 방식으로 추가했고, 빈 `dodam_534_test` 스키마에 실제 Flyway가 `V1`~`V14`를 적용한 뒤 `ddl-auto: validate` 통과, CHECK 7개와 복합 외래키 3컬럼 생성, CHECK가 살아 있는 상태의 상환·이자납부 정합을 검증했다. 일회용 컨테이너에서 수행했고 운영 DB와 인프라 구성은 건드리지 않았다.
- [ ] 데이터 기준 동작은 확인하지 못했다. seed 적재에 암호화 픽스처 임시 테이블 3개가 필요하며 530·532와 같은 선행 조건이다. 위 테스트는 테스트가 만든 최소 fixture 기준이다.

### seed의 next_interest_date 고정

- [ ] seed(S15P21D105-523)의 `next_interest_date`가 1,000행 모두 `2026-09-25`인 점을 보완한다.
- 현재 동작: 그날이 지나면 이자 API의 `daysToNextInterestDate`와 `estimatedInterestUntilNextDate`가 시연 계좌 전체에서 `null`이 된다.
- 담당 근거: 534 코드 문제가 아니라 seed 데이터 문제다. 발견 사항으로 보고한다.

### API 5 거래내역과의 중복

- [ ] `GET /api/v1/accounts/{accountNo}/transactions`(명세 5)와 응답 계약이 갈라지지 않게 501 구현과 대조한다.
- 현재 동작: 두 명세 모두 승인된 문서라 10-1의 7개를 그대로 구현했다. 경로만 다르고 입력과 제한사항 문구가 동일하다.
- 완료 조건: 응답 필드·정렬·기간 정책을 501과 맞추거나, 한쪽으로 통합할지 팀에서 정한다.

### Jira 최신 완료 조건 대조

- [ ] 실제 Jira 접근이 가능해지면 최신 534 완료 조건과 구현·남은 제한을 대조한다.
- 현재 브랜치는 사용자가 지정한 `S15P21D105-534-api-10-1-minus-account-api`이며 develop `cf854be` 기준으로 로컬 생성했다. Jira `Create branch`가 아니므로 이슈 연결 확인이 남는다.
- 로컬 `SPRINT/Jira (4).csv`의 제목·설명만 근거로 범위를 정했다.

## S15P21D105-538 — Cashflow 저장 계층

구현 계약: [cashflow.md](cashflow.md)

### MySQL 검증

- [x] `V16`을 MySQL 8.0에서 검증했다(2026-09-10). 빈 `dodam_538_test` 스키마에 Flyway가 `V1`~`V16`을 적용한 뒤 `ddl-auto: validate` 통과, CHECK 5개 생성, 유니크·금액·심각도·horizon 제약 동작, 조회 3종의 MySQL 방언 실행을 확인했다. 일회용 컨테이너에서 수행했고 운영 DB와 인프라 구성은 건드리지 않았다.
- 검증 중 `dedupe_key`·`request_hash`의 `CHAR(64)` 매핑 누락을 발견해 고쳤다. 엔티티가 VARCHAR로 매핑돼 `validate`에 실패했다(529의 `card` CHAR 컬럼과 같은 사례).
- [ ] 데이터 기준 동작은 확인하지 못했다. 위 테스트는 최소 fixture 기준이며 시연 seed를 적재한 상태는 검증하지 않았다.

### 미확정 정책 반영

- [ ] `calculation_version` 표기 규칙을 팀에서 정하고 `CashflowCalculationVersion.CURRENT`를 교체한다. 현재는 `"v1"` 상수다.
- [ ] `cashflow_event.type`과 `cashflow_risk.type`의 값 집합을 ERD 주요 코드값 표에 추가한다. 539·542가 코드에서 확정했고(`cashflow_risk.type`은 `BALANCE_SHORTAGE` 하나) ERD 반영만 남았다.
- [x] `request_hash` 생성 규칙(540)과 `available_today` 계산 정의(542, `max(0, minimumBalance)`)를 반영했다.

### Jira 최신 완료 조건 대조

- [ ] 실제 Jira 접근이 가능해지면 최신 538 완료 조건과 구현 범위를 대조한다. 로컬 `SPRINT/Jira (4).csv`의 제목·설명만 근거로 범위를 정했다.

## S15P21D105-540 — 일별 잔액·캐시 정책

구현 계약: [cashflow-daily-balance.md](cashflow-daily-balance.md)

### 미확정 정책 반영

- [ ] 540이 정한 기본값을 팀에서 확인한다. `horizonDays` 기본 90·상한 365, 완료 캐시 유효기간 당일,
  시작 잔액 = 활성 `DEMAND_DEPOSIT` 합계, 과거 기준일 거부. 모두 명세에 값이 없어 정한 기본안이다.
- [x] `request_hash` 생성 규칙은 540에서, `available_today`는 542에서 확정했다.

### 계산 정확도 한계

- [ ] 요일별 평균 변동지출을 반영하지 못한다. 입력이 될 `transaction` 테이블의 마이그레이션이 아직 없다
  (현재 `V1`~`V4`, `V10`~`V16`). 그때까지 예상잔액은 실제보다 낙관적이다. 담당 이슈를 정해야 한다.
- [ ] `AccountScheduledEventSource` 구현체가 없어 급여 등 예정 입금이 반영되지 않는다. 현재 예측은 확정
  출금 위주다.
- [ ] 수집 사유(`reasons`)를 저장할 컬럼이 ERD에 없다. 실행 응답에서만 노출되고 `latest` 조회와 캐시
  재사용 응답에서는 사라진다. 컬럼 추가가 필요하면 ERD 변경을 먼저 논의한다.

### 중복 요청 방지의 경합

- [ ] 같은 요청이 동시에 들어오면 두 요청 모두 새 예측을 만들 수 있다. `(user_id, request_hash, status)`에
  유니크 제약이 없고 조회와 생성이 한 트랜잭션이 아니다. 순차 재요청만 막는다.
- [ ] 진행 중 인정 시간 10분은 임의값이다. 수집·계산의 실제 소요 시간을 측정한 뒤 조정한다.

### 검증 범위

- [x] H2 테스트로 기간 정책·지문·캐시 판정·잔액 계산·저장 경로를 검증했다(2026-09-10). 아래 H2 fixture
  문제를 함께 고친 뒤 전체 `clean build`가 611건 전부 통과한다.
- [ ] MySQL 검증은 하지 않았다. 스키마를 바꾸지 않고 538의 테이블만 읽고 쓴다. 새 SQL은
  `CashflowBalanceReader`의 집계 한 건이다.

### Jira 최신 완료 조건 대조

- [ ] 실제 Jira 접근이 가능해지면 최신 540 완료 조건과 구현 범위를 대조한다. 로컬 `SPRINT/Jira (4).csv`의
  제목·설명만 근거로 범위를 정했다.

## S15P21D105-541 — 현금흐름 실행·조회 API

구현 계약: [cashflow-query.md](cashflow-query.md)

### 미확정 계약 반영

- [ ] 조회 API가 **완료 시각 기준 최신 완료 예측 1건**을 대상으로 삼는 규칙은 이 작업에서 정한
  기본안이다. 명세에 `forecastId` 파라미터가 없어 정했다. 팀 확인이 필요하다.
- [x] 538이 정의한 기준일 내림차순 최신 조회를 완료 시각 기준으로 바꿨다(2026-09-10). 미래 기준일
  예측이 계속 최신으로 남는 문제를 막는다. `CashflowForecastRepository`의 메서드 두 개를 이름까지
  바꿨고 538 테스트의 호출부와 [cashflow.md](cashflow.md)의 계약 표를 함께 고쳤다.
- [ ] 완료된 예측이 없을 때 404(`RESOURCE_NOT_FOUND`)로 응답한다. 빈 목록으로 바꿀지 확인이 필요하다.
- [ ] `events`의 `type`은 형식만 검증하고 값 집합을 고정하지 않았다. 538의 남은 항목(`cashflow_event.type`
  값 집합 확정)이 해소되면 허용값 검증으로 바꿀지 결정한다.
- [ ] `availableToday`와 위험 목록은 응답에 없다. 542에서 계산이 정해지면 실행·최신 조회 응답에 추가한다.

### 검증 범위

- [x] H2 통합 테스트 7건으로 인증·소유권 격리·입력 검증·기본 기간·빈 결과·캐시 재실행·최신 판정을 검증했다
  (2026-09-10). 전체 `test`도 통과한다.
- [ ] MySQL 검증은 하지 않았다. 스키마를 바꾸지 않고 538의 테이블만 읽는다. 새 SQL은 없다.
- [ ] 프론트엔드 연동은 확인하지 않았다. 응답 계약이 확정되면 `frontend/`의 API 타입 추가가 필요하다.

### Jira 최신 완료 조건 대조

- [ ] 실제 Jira 접근이 가능해지면 최신 541 완료 조건과 구현 범위를 대조한다. 로컬 `SPRINT/Jira (4).csv`의
  제목·설명만 근거로 범위를 정했다.

## 공통 — 테스트 소스 인코딩 (540 외부)

- [ ] `build.gradle`에 `compileJava.options.encoding`이 없어 javac가 플랫폼 인코딩(MS949)으로 소스를 읽는다.
  `gradle.properties`의 `-Dfile.encoding=UTF-8`은 Gradle 데몬 설정이라 컴파일 인코딩을 정하지 않는다.
- 영향: 소스의 한글 문자열 리터럴이 깨진 채 컴파일된다. 테스트는 양쪽이 같이 깨져 비교가 통과하므로
  드러나지 않지만, 이벤트 표시명처럼 응답·DB에 나가는 한글 상수도 같은 영향을 받는다.
- 완료 조건: `compileJava`/`compileTestJava`에 `options.encoding = 'UTF-8'`을 추가하고 기존 한글 리터럴이
  정상 출력되는지 확인한다. 빌드 설정 변경이라 540 범위 밖으로 두고 담당 이슈를 정해야 한다.

## 공통 — develop의 H2 테스트 실패 (해결됨)

- [x] 2026-09-10 해결했다. 원인이 하나가 아니라 둘이었다.
  - `DepositSavingApiIntegrationTest` 29건: `deposit-saving-test-data.sql`의 `account` INSERT가
    `currency`·`account_no_hash`·`created_at`·`updated_at`을 채우지 않아 NOT NULL 위반으로 실패했다.
    `savings-test-data.sql`과 같은 방식으로 채웠다.
  - `CardQueryApiIntegrationTest` 1건: `currency`와 무관했다. fixture가 자체 `CREATE TABLE`을 쓰므로
    NOT NULL 문제가 없다. `TX-C1-003`을 `today.minusDays(40)`에 넣은 탓에 오늘 날짜에 따라 그 거래가
    전월에 들어가고, 전월 실적이 0이라는 혜택 테스트의 전제가 매달 10일경부터 깨졌다.
    `today.minusMonths(2)`로 바꿔 날짜 의존을 없앴다.
- 아래 기록은 정정 전 내용이다. 30건을 모두 `currency` 문제로 본 것은 틀렸다.
- 근거: `MinusAccountOwner`가 `account.currency`를 `nullable = false`로 매핑하는데, H2는 엔티티 기준으로 스키마를 만들어 `V3`의 `DEFAULT 'KRW'`가 없다. 두 테스트의 seed SQL은 `currency`를 넣지 않는다.
- 영향: Jenkins의 Build & Test 단계가 `./gradlew clean build`를 돌리므로 어느 브랜치의 MR이든 이 실패를 함께 본다.
- 완료 조건: `card-query-test-data.sql`과 `deposit-saving-test-data.sql`의 `account` INSERT에 `currency`를 채우거나, 엔티티 매핑에 기본값을 반영한다. 담당 이슈를 정해야 한다.

## S15P21D105-501 — 계좌 목록·상세·잔액

구현 계약: [account-query.md](account-query.md)

### 금융망 잔액 실시간 조회

- [ ] `GET /api/v1/accounts/{accountNo}/balance`가 금융망(2.4 계좌 잔액 조회)을 호출할지, 우리 DB 값만 돌려줄지 확정한다.
- 현재 동작: `account.balance`와 `updated_at`(`balanceAsOf`)을 돌려준다. 명세는 "SSAFY"로 표기하지만 카드·대출·예적금이 모두 로컬 테이블을 읽는 방식과 맞췼다.
- 근거: 2026-09-09 기준 전달받은 API Key가 거래 API에서 `H1008`로 거절돼 실호출 검증이 불가능했고, 마이너스통장 상환(534)이 로컬 잔액만 바꾸므로 금융망 값을 그대로 보이면 상환 결과가 사라진 것처럼 보인다.
- 완료 조건: 실시간 조회로 정하면 `SsafyFinanceClient`에 잔액 조회를 추가하고 응답에 조회 시각을 싣는다. 로컬로 확정하면 명세의 "SSAFY" 표기를 고친다.

### 금융망 계좌 목록 실호출 검증

- [x] ACCOUNT 동기화 핸들러(`AccountSyncHandler`)를 구현했다(2026-09-11). 금융망 2.4 계좌 목록 → `account` upsert, SEED·MANUAL 보존, 목록에서 사라진 SSAFY 계좌 CLOSED 처리까지 Mock 기반 통합 테스트로 확인했다.
- [ ] 실제 금융망 호출로 `inquireDemandDepositAccountList` 응답 필드명·형식(`accountBalance` 문자열, `lastTransactionDate` yyyyMMdd·빈 문자열)을 대조한다. 2026-09-09 기준 API Key가 거래 API에서 `H1008`로 거절돼 실호출 검증을 못 했고, 응답 계약은 금융망 문서 예시를 따랐다.
- [ ] 계좌가 하나도 없는 사용자에게 금융망이 `REC`를 생략하는지 빈 배열로 주는지 확인한다. 구현은 둘 다 빈 목록으로 해석한다.
- 완료 조건: 시연 계정으로 `POST /finance-data/sync`(ACCOUNT) 실행 후 목록·상세·잔액이 금융망 값과 일치한다.

### 부분 매핑 엔티티 교체

- [ ] `CardWithdrawalAccount`(card), `MinusAccountOwner`(minusaccount), `JdbcAccountBalanceWriter`(minusaccount)를 `Account` 엔티티로 교체할지 정한다.
- 현재 동작: 세 엔티티가 `account`를 함께 매핑한다. 읽기는 문제없지만 쓰기 엔티티가 둘(`MinusAccountOwner`, `Account`)이 되면 같은 행을 다른 영속성 컨텍스트 상태로 들고 있을 수 있다. 501은 읽기만 하므로 지금은 충돌이 없다.
- 담당 근거: 카드·마이너스통장은 BE-B 영역이라 501에서 고치지 않았다.
- 완료 조건: 마이너스통장 상환이 `Account`로 잔액을 쓰거나, 쓰기 주체를 하나로 명시한다.

### 엔티티 nullable 완화

- [ ] `Account.accountNoEncrypted`·`sourceType`이 DDL은 NOT NULL인데 엔티티는 nullable이다.
- 근거: H2 테스트는 엔티티로 스키마를 만드는데 `deposit-saving-test-data.sql`, `savings-test-data.sql`, `deposit-detail-test-data.sql`, `CashflowBalanceReaderTest`가 두 컬럼 없이 `account`를 적재한다. NOT NULL로 매핑하면 그 테스트가 깨진다. local/prod `validate`는 NULL 허용 여부를 검사하지 않는다.
- 완료 조건: 네 fixture가 두 컬럼을 채우도록 고친 뒤 엔티티를 NOT NULL로 되돌린다. 담당 이슈를 정해야 한다.

### MySQL 검증

- [x] `Account` 엔티티가 V3 DDL과 `ddl-auto: validate`를 통과하는지 MySQL 8.0에서 확인한다. 2026-09-11 확인: Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용한 뒤 백엔드를 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 32개 전부 통과, `Started DodamApplication`(6.2초). 오류·경고 없음.
- 근거: `account_no_encrypted`는 `VARBINARY(512)`↔`byte[]`, `account_no_hash`·`currency`는 CHAR 고정 길이 매핑이다. 카드·대출과 같은 `columnDefinition` 처리를 했지만 실검증은 남았다.
- 완료 조건: 빈 스키마에 V1~V16 적용 후 기동, 목록·상세·잔액 SQL을 MySQL에서 실행.

## S15P21D105-502 — 계좌 거래내역 Adapter

구현 계약: [account-query.md](account-query.md) "계좌 거래내역·거래 단건", "거래내역 Adapter"

### 거래내역 동기화 기간·증분 전략

- [ ] 매 동기화마다 계좌별 최근 12개월 거래를 통째로 받아 없는 것만 넣는다. 거래가 많아지면 금융망 호출·응답이 커진다.
- 선행 조건: 금융망 거래내역 API가 페이지네이션을 지원하는지(문서 예시에는 `totalCount`·`list`만 있다), 계좌별 마지막 동기화 시각을 어디에 둘지(`account` 컬럼 추가는 ERD 변경) 정한다.
- 완료 조건: 두 번째 동기화부터는 마지막 적재 거래일 이후만 받거나, 페이지 단위로 받는다. 시연 규모(계좌 수 건, 거래 수백 건)에서는 현재 방식으로 충분하다.

### 금융망 거래내역 실호출 검증

- [ ] `inquireTransactionHistoryList` 요청 Body(`accountNo`, `startDate`, `endDate`, `transactionType = A`, `orderByType = DESC`)와 응답(`REC.totalCount`, `REC.list[].transactionType "1"/"2"`, 금액 문자열, 시각 HHmmss)을 실호출로 대조한다. API Key 문제(499 절)로 문서 예시만 따랐다.
- [ ] 계좌에 거래가 없을 때 `REC`가 생략되는지 `list: []`로 오는지 확인한다. 구현은 둘 다 빈 목록으로 해석한다.

### V6 마이그레이션과 504 브랜치 정리

- [ ] `V6__create_transaction_category_and_transaction.sql`은 504 브랜치(`S15P21D105-504-api-6-transaction-query`)의 V5 커밋을 cherry-pick한 뒤 **V6으로 번호를 옮긴 것**이다. develop 병합 시 543이 `V5__create_goal.sql`로 V5를 먼저 써서 충돌했다(2026-09-11).
- 완료 조건: 504 브랜치를 이어갈 때 develop을 병합하고 그쪽의 V5 마이그레이션 커밋을 걷어낸다(테이블은 이미 V6으로 develop에 있다). `Transaction` 엔티티·fixture의 "V5" 표기도 V6으로 고친다.

### 빈 DB에서 Flyway가 V6에서 멈춤 (새 환경 기동 불가)

- [ ] **빈 스키마에 Flyway를 돌리면 V6에서 실패한다.** V6의 `fk_transaction_card`가 V11이 만드는 `card`를 참조하는데, 빈 DB에서는 Flyway가 버전 순서로 V1→V6을 먼저 적용하므로 `Failed to open the referenced table 'card'`로 기동이 막힌다. 2026-09-11 Docker `mysql:8.0` 빈 스키마 + `local` 프로필로 재현했다(`Script V6__create_transaction_category_and_transaction.sql failed`). 운영 DB는 V11~V17이 먼저 적용된 뒤 V6이 out-of-order로 들어가 영향이 없었고, 534·538 절의 빈 스키마 검증(09-09·09-10)은 V6이 develop에 들어오기 전이라 통과했다.
- 영향: 새 개발 PC, 재생성한 CI/테스트 DB, 볼륨을 지운 운영 DB에서 백엔드가 기동하지 않는다. 손으로 적용할 때는 V1~V5 → V10~V11 → V6~V9 → V12~V17 순서로 우회할 수 있다(`seeds/README.md` 검증 상태).
- 수정 후보: (a) 새 마이그레이션(BE-B와 번호 협의)에서 `information_schema`로 존재 여부를 확인해 `fk_transaction_card`를 조건부로 추가하고, V6에서는 카드 외래키를 빼되 이미 적용된 운영 DB의 체크섬 불일치는 `flyway repair`로 맞춘다. (b) V6 파일은 그대로 두고 `card`만 만드는 최소 마이그레이션을 V6 앞 번호로 넣는 방법은 V1~V5 번호가 모두 찼으므로 불가. 팀에서 결정한다.

### 카드·마이너스통장 거래내역과의 통합

- [ ] `GET /accounts/{accountNo}/transactions`(5), `GET /cards/{cardNo}/transactions`(8), `GET /minus-accounts/{accountNo}/transactions`(10-1)가 같은 원장을 계좌·카드 축으로 따로 읽는다. 응답 필드는 맞췼지만 reader 구현은 세 개다.
- 완료 조건: 공용 reader로 합치거나 세 구현을 그대로 두기로 팀에서 정한다(534 "API 5 거래내역과의 중복" 항목과 함께).

## S15P21D105-503 — 계좌 집계·휴면 탐지

구현 계약: [account-query.md](account-query.md) "계좌 집계·휴면 탐지"

### 휴면 기준 기본값 확정

- [ ] `inactiveMonths` 기본값 6개월, `maxBalance` 기본 없음(잔액 > 0만 요구)으로 구현했다. 기획안 예시는 "최근 8개월"을 쓰고 금융권 휴면 기준은 1년이라 팀 확정이 필요하다.
- 완료 조건: FE 기본 호출 값과 진단(14 Diagnosis)의 "휴면성 소액계좌" 이슈 판정이 같은 기준을 쓴다.

### 현금성 자산 정의

- [ ] 현금성 자산 = 수시입출금 잔액(CLOSED 제외, INACTIVE 포함)으로 정의했다. Finance Data 통합 요약(500)의 `totals.cashAssets`는 예·적금까지 포함하므로 두 값이 다르다. 명칭을 맞추거나 500의 필드명을 바꿀지 정한다.
- 근거: 기획안 9-2가 현금성 자산과 저축성 자산을 구분한다.

### 거래 원장 기반 휴면 판정

- [ ] 휴면 판정은 `account.last_transaction_date`만 본다. 동기화 전 seed 계좌는 seed가 넣은 값에, 수동 거래(API 6 등록)는 이 컬럼을 갱신하지 않으면 판정에 반영되지 않는다.
- 완료 조건: 수동 거래 등록(505)이 `last_transaction_date`를 갱신하거나, 판정 시 `transaction`의 최근 거래일과 비교해 더 최신 값을 쓴다.

### MySQL 검증

- [ ] `JdbcAccountFlowReader`의 `YEAR()`/`MONTH()` GROUP BY와 `SUM(CASE …)`을 MySQL 8.0에서 실행한다. H2(MODE=MySQL)만 확인했다. `ONLY_FULL_GROUP_BY` 모드에서도 SELECT 항목이 모두 GROUP BY 식 또는 집계이므로 통과해야 한다.
## S15P21D105-543 — Diagnosis 저장 구조·실행 골격

구현 계약: [diagnosis.md](diagnosis.md)

### V5 번호와 goal 테이블 확인(병합 전 필수)

- [ ] `V5__create_goal.sql`의 번호와 선반영 자체를 BE-A와 확인한다.
- 근거: `diagnosis.goal_id`가 ERD상 `goal(user_id, goal_id)`를 참조하는데 `goal` 마이그레이션이 없어
  V17을 만들 수 없었다. V3의 `account` 선례대로 스키마만 먼저 추가했다. BE-A 구간(V1~V9)의 번호를
  소비했으므로 `db/migration/README.md`의 "구간을 넘겨 써야 하면 담당자와 번호를 확인" 규칙이 걸린다.
- 완료 조건: BE-A가 V5 번호를 확인하고, Goal API(508~511)가 `goal` 테이블을 다시 만들지 않는다는 것에
  합의한다. 번호 충돌이 있으면 병합 전에 파일명을 바꾼다.

### MySQL 검증

- [x] `V5`·`V17`을 MySQL 8.0.46에서 적용하고 `DiagnosisMySqlIntegrationTest` 3건을 통과시켰다
  (2026-09-10). `ddl-auto: validate`, `fk_diagnosis_goal`, JSON 컬럼 타입, CHECK 5개를 확인했다.
- [x] 운영 배포 경로(V16까지 적용된 DB에 V5·V17이 뒤늦게 도착)를 실제 파일로 재현했다. V5가
  `OUT_OF_ORDER`로 적용되고 FK가 정상 생성되며 재기동에도 문제가 없다.

### 미확정 정책

- [ ] `rule_version` 표기 규칙을 팀에서 정하고 `DiagnosisRuleVersion.CURRENT`를 교체한다. 현재는 `"v1"`
  상수이며 cashflow의 `CashflowCalculationVersion`과 같은 상황이다. 두 값의 표기를 함께 정한다.
- [ ] `diagnosis_issue.domain`과 `type`의 값 집합을 544·545에서 확정하고 ERD 주요 코드값 표에 추가한다.
  ERD에 목록이 없어 `String`으로 두었다.
- [ ] `input_snapshot`의 형식과 항목을 544에서 정한다.
- [ ] 종합 상태(`overall_status`) 판정 규칙을 544에서 정한다. 현재는 호출자가 넘긴 값을 그대로 저장한다.
- [ ] 544의 진단 실행에서 `goalId` 소유권을 애플리케이션 단계에서 검증한다. 복합 FK
  `(user_id, goal_id)`가 남의 목표를 막아 주지만 DB까지 가서 막히므로 500이 된다. 400 또는
  `RESOURCE_NOT_FOUND`로 바꿔야 한다. H2 테스트는 FK가 없어 이 경로를 재현하지 못한다.

### 심각도 enum 통합 여부

- [ ] `diagnosis`의 `IssueSeverity`와 `cashflow`의 `RiskSeverity`를 `global/`로 합칠지 정한다.
- 근거: 값 집합(`LOW`/`MEDIUM`/`HIGH`/`CRITICAL`)이 같고 ERD 주요 코드값 표의 같은 항목을 가리킨다.
  한쪽 도메인의 enum을 다른 도메인이 import하면 서로의 코드값 변경에 묶여 별도 enum을 두었다.
- 선행 조건: cashflow 파일을 건드리면 병합 대기 중인 S15P21D105-542와 충돌한다. 542 병합 후에 판단한다.
- 완료 조건: 합치기로 했다면 두 도메인이 같은 enum을 쓰고 ERD 코드값 표가 그 사실을 반영한다.

### 실패 상태 표현

- [ ] 진단 실행 실패를 어떻게 표현할지 정한다. ERD `diagnosis`에 상태 컬럼이 없어 실패한 진단이
  접수된 채 `completed_at` NULL로 남고 진행 중인 진단과 구분되지 않는다.
- 근거: cashflow는 `ForecastStatus`로 `FAILED`를 남기지만 diagnosis에는 대응 컬럼이 없다.
- 선행 조건: 컬럼 추가가 필요하면 ERD 변경을 먼저 논의한다.
- 완료 조건: 실패한 진단을 재시도 판단에서 진행 중인 진단과 구분할 수 있다.

### Jira 최신 완료 조건 대조

- [ ] 실제 Jira 접근이 가능해지면 최신 543 완료 조건과 구현 범위를 대조한다. 로컬
  `SPRINT/Jira (4).csv`의 제목(`[API 14] Diagnosis 기반`)과 설명(`Diagnosis·Issue 관계와 실행 골격`)만
  근거로 범위를 정했다.
## S15P21D105-504 — 거래 조회·메모·카테고리·숨김

구현 계약: [transaction-query.md](transaction-query.md)

### 금융망 메모 API 반영

- [ ] 명세 6은 거래 메모 수정을 "SSAFY" 연동으로 표기하지만 현재는 우리 DB `transaction.memo`만 갱신한다.
- 근거: 금융망 거래 메모 API(2.4 거래내역 메모)에 넘길 `accountNo`·`transactionUniqueNo`와 우리 원장의 대응 규약이 501(계좌 동기화)에서 아직 정해지지 않았다. 2026-09-09 기준 전달받은 API Key가 거래 API에서 `H1008`로 거절돼 실호출 검증도 불가능했다.
- 선행 조건: 501이 `transaction_id`를 금융망 `transactionUniqueNo`로 채우는지, 계좌 거래만 대상인지 확정.
- 완료 조건: SSAFY 출처 계좌 거래의 메모 수정 시 금융망에도 반영되거나, 로컬 전용으로 확정해 명세의 "SSAFY" 표기를 고친다.

### 거래 seed 데이터

- [ ] `backend/database/seeds`에 `transaction` seed가 없다. 시연 계정으로 거래 목록·카드 사용내역·마이너스통장 거래·소비 기반 카드 추천을 보이려면 필요하다.
- 근거: 카드·마이너스통장·상품 추천 도메인은 `transaction`을 JDBC로 읽지만 데이터 적재는 어느 티켓도 맡지 않았다. 카테고리 기준 행(V6)의 코드를 그대로 써야 한다.
- 완료 조건: 시연 사용자별 3개월치 거래가 V6 카테고리 코드·`payment_method`(CARD/ACCOUNT) 규약으로 적재되고, 501 동기화가 `source_type = 'SEED'`를 덮어쓰지 않는 규칙(534에서 제기한 것과 같은 문제)이 정해진다.

### MySQL 검증

- [x] V6를 MySQL 8.0에서 적용해 `ddl-auto: validate` 통과와 복합 외래키(`account`, `card`) 생성을 확인한다. 2026-09-11 확인: Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용한 뒤 백엔드를 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 32개 전부 통과, `Started DodamApplication`(6.2초). 오류·경고 없음. 복합 외래키 `fk_transaction_account`·`fk_transaction_card`는 V11 뒤에 V6을 적용하는 순서에서 생성됐다.
- 근거: `hidden`·`active`는 TINYINT(1)↔boolean, `transaction_time`은 TIME↔LocalTime 매핑이며 H2와 MySQL의 타입 검증 결과가 다를 수 있다. 기존 DB에는 V17 뒤에 V6이 들어가므로 out-of-order 적용도 함께 본다.
- 완료 조건: 빈 스키마에 V1~V16 적용 후 기동, 목록·상세·PATCH 3종 SQL을 MySQL에서 실행.

### 명세 외 추가 항목 확정

- [ ] `GET /api/v1/transactions/categories`와 목록 파라미터 `hidden`을 API 명세 6에 반영할지 팀에서 정한다.
- 근거: FE가 카테고리 수정 Body의 `categoryId`와 필터 코드를 알 방법이 명세에 없었고, 숨긴 거래를 다시 보이게 할 목록도 없었다. 구현은 문서에 "명세 외 추가"로 표시했다.
- [ ] 목록에 `transactionType`(INCOME/EXPENSE/TRANSFER) 필터를 추가할지 정한다. 가계부 화면의 기본 필터이지만 명세에 없어 넣지 않았다.

### 카드·마이너스통장 fixture와의 스키마 차이

- [ ] `card-query-test-data.sql`, `minus-account-test-data.sql`, `product-comparison-recommendation-test-data.sql`이 만드는 `transaction`에는 `balance_after`·`created_at`·`updated_at`이 없다. 이 fixture가 남은 상태에서 `Transaction` 엔티티로 쓰기를 하면 실패한다.
- 현재 동작: 504 테스트는 자기 fixture로 V6 컬럼 전체를 다시 만들어 영향이 없다. 다른 도메인 테스트는 읽기만 하므로 역시 영향이 없다.
- 완료 조건: 공용 fixture 하나로 합치거나, 각 fixture가 V6 컬럼을 모두 갖도록 맞춘다. 담당 이슈를 정해야 한다.

## S15P21D105-505 — 수동 거래 CRUD

구현 계약: [transaction-query.md](transaction-query.md) "수동 거래 등록·수정·삭제"

### 계좌 연결 여부

- [ ] 수동 거래는 계좌·카드에 연결되지 않는다(`account_id` null). 명세 Body에 계좌 필드가 없어서다. 그래서 계좌 거래내역(5)·계좌 잔액·`last_transaction_date`·현금흐름 예측에는 잡히지 않고, 전체 거래 목록(6)과 소비 집계에만 나타난다.
- 선행 조건: FE가 수동 거래 화면에서 계좌를 고르게 할지 팀에서 정한다. 넣기로 하면 Body에 `accountId?`를 추가하고 소유권·계좌 유형(DEMAND_DEPOSIT) 검증, `last_transaction_date` 갱신 여부를 함께 정한다(503 휴면 판정 항목과 연결).
- 완료 조건: 명세 6의 Body 정의와 구현이 일치한다.

### 소비유형 재계산

- [ ] 등록 시 `spending_type`은 카테고리 기본값에서만 채운다. 카테고리 없이 등록하면 null이라 고정·변동지출 집계(`/spending/fixed`, `/spending/variable`)에서 빠진다.
- 완료 조건: 3 Preference(자동 분류)가 미분류 거래의 카테고리·소비유형을 채우는 규칙을 정하거나, 등록 시 거래 유형별 기본값(EXPENSE → VARIABLE 등)을 두기로 결정한다.

### 삭제 방식

- [ ] 물리 삭제로 구현했다. 삭제 이력이 필요하면(진단·리포트가 과거 집계를 다시 계산할 때) soft delete 컬럼이 없어 ERD 변경이 필요하다. 현재 ERD에는 `deleted_at`이 없다.

## S15P21D105-506 — 소비 기간·카테고리 집계

구현 계약: [spending-aggregation.md](spending-aggregation.md)

### 티켓 범위 확정

- [ ] 506을 `/spending/**` 11개 중 집계 6개(월 소비 요약, 월 누적 소비, 일별 소비, 카테고리별 소비, 카테고리 소비 추이, 결제수단별 소비)로 잡았다. 소비·저축 비율, 소비 급증, 소비목표 비교, 고정·변동지출은 다음 하위 태스크로 남겼다. Jira 하위 태스크 설명과 다르면 재배치한다.

### "소비" 정의 합의

- [ ] EXPENSE이면서 `spending_type`이 NON_SPENDING이 아닌 거래(미분류 포함)를 소비로 정의했다. 진단(14)·리포트(20)·소비 기반 카드 추천(18)이 같은 정의를 쓰는지 대조한다. 카드 추천 reader(`JdbcCardRecommendationSpendingReader`)는 현재 카드 거래만 본다.
- 완료 조건: 소비 정의를 공용 문서(ERD 「데이터 무결성 규칙」 또는 spending-aggregation.md)에 한 번만 두고 각 도메인이 참조한다.

### 소비 거래 seed

- [ ] `transaction` seed가 없어 시연 계정의 소비 집계가 전부 0이다(504 절 "거래 seed 데이터"와 같은 항목). 카테고리·결제수단·`spending_type`이 골고루 들어간 3개월치가 필요하다.

### MySQL 검증

- [ ] `JdbcSpendingReader`의 다섯 SQL(월·일·카테고리·월×카테고리·결제수단 GROUP BY)을 MySQL 8.0 `ONLY_FULL_GROUP_BY`에서 실행한다. H2만 확인했다. SELECT 항목이 모두 GROUP BY 식 또는 집계라 통과해야 한다.

## S15P21D105-507 — 목표·이상소비·결제수단 분석

구현 계약: [spending-aggregation.md](spending-aggregation.md) 507 절

### 급증 판정 기준값 확정

- [ ] 기준 증가율 20%, 소액 보정 10,000원은 기획안 예시(38% 증가)가 잡히도록 잡은 기본값이다. 기획안이 남긴 "소액·고액 보정계수 + 물가상승률" 논의가 끝나면 값을 바꾸고, 필요하면 `application.yml` 설정으로 뺀다.
- 완료 조건: 진단(14)의 "변동지출 급증" 이슈가 같은 기준으로 판정된다.

### 저축률 정의

- [ ] 저축률을 `(소득 − 소비) / 소득`(잔여)으로 정의했다. 실제 저축 이체(`savingTransfer`)는 참고 수치로만 준다. 어느 카테고리·이체를 "저축"으로 볼지 규약이 정해지면 저축률 분자를 실제 저축액으로 바꿀지 결정한다.
- 근거: 기획안은 "소비 : 저축 목표 비율"을 말하지만 저축 거래 식별 규칙이 없다.

### 소득 출처

- [ ] `financial_profile.monthly_income`이 있으면 그 값을, 없으면 그 달 INCOME 거래 합을 소득으로 쓴다. 급여가 두 번 들어온 달이나 환급 입금이 있는 달은 거래 합이 과대평가될 수 있다. 3 Preference(패턴 분석)가 "급여" 입금을 식별하면 그 값으로 바꾼다.

### V7 goal 하위행 선반영

- [ ] `saving_goal`, `spending_goal`을 V7로 만들었다. Goal API(508~511)는 같은 테이블을 다시 만들지 않고 `goal_account`, `goal_history`만 추가한다. 소비목표에 카테고리가 없어 `goal-comparison`은 월 전체 소비와 비교한다. 카테고리별 예산이 필요하면 ERD 변경이다.

### MySQL 검증

- [ ] V7 적용과 `JdbcSpendingAnalysisReader`의 SQL(spending_type별 월·카테고리 집계, goal JOIN spending_goal)을 MySQL 8.0에서 실행한다. H2만 확인했다.

## S15P21D105-508 — Goal CRUD

구현 계약: [goal.md](goal.md)

### 명세 Body와 ERD의 불일치(줄이기 목표)

- [ ] 명세 12 생성 Body는 `targetAmount`·`targetDate` 필수, `monthlyBudget` 선택인데 ERD `spending_goal`에는 `monthly_budget`만 있다. 줄이기(SPENDING) 목표에 목표 금액·목표일이 오면 400으로 거절하도록 구현했다. FE 목표 설정 화면(481·485)이 줄이기 목표에도 금액·기한을 받는다면 ERD에 컬럼을 추가하거나 명세를 고쳐야 한다.
- 완료 조건: 명세 12 Body 정의와 ERD·구현이 일치한다.

### 달성 자동 전이

- [ ] 모인 금액이 목표 금액에 도달해도 상태는 ACTIVE로 남는다(510은 `paceStatus = ACHIEVED`, `riskLevel = NONE`으로만 알린다). 자동 ACHIEVED 전이를 넣을지, 사용자가 확인 후 PATCH로 바꾸게 둘지 팀 결정. 자유입출금 계좌는 잔액이 다시 줄 수 있어 자동 전이가 되돌아가는 문제가 있다.

### 삭제 시 연결 해제

- [x] 509에서 처리 방식을 정했다: 목표 soft delete 시 `goal_account` 행은 남기되, 다른 목표가 그 계좌를 연결하면 삭제된 목표의 연결을 해제하고 이어받는다(`GoalAccountService.replaceAccounts`). 삭제 시점에 바로 지우지 않은 이유는 삭제된 목표의 이력(511)이 연결 계좌를 참조할 수 있기 때문이다.

### MySQL 검증

- [x] `Goal`·`SavingGoal`·`SpendingGoal` 엔티티가 V5·V7 DDL과 `ddl-auto: validate`를 통과하는지 MySQL 8.0에서 확인한다. 2026-09-11 확인: Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용한 뒤 백엔드를 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 32개 전부 통과, `Started DodamApplication`(6.2초). 오류·경고 없음.

## S15P21D105-509 — 목표 연결계좌

구현 계약: [goal.md](goal.md) "연결계좌"

### 계좌 잔액 변경 시 모인 금액 재계산

- [x] 510에서 처리했다: progress·required-saving·forecast·risk가 조회 시점에 연결 계좌 잔액 합으로 다시 계산하고 `goal.current_amount`도 갱신한다. 목록·요약(508)은 저장값을 그대로 쓰므로 계산 API를 한 번 부른 뒤에 맞아진다.
- [ ] 목록·요약도 실시간으로 맞추려면 ACCOUNT 동기화(502) 뒤에 사용자 목표를 갱신하는 훅이 필요하다. 동기화 응답 시간에 영향이 있어 팀 결정으로 남긴다.

### 기여 금액(contribution_amount)

- [ ] 명세 Body가 `accountNos[]`만 받아 계좌별 기여 금액을 지정할 수 없다. 급여통장처럼 잔액 일부만 목표에 쓰는 경우를 위해 ERD가 둔 컬럼이다. FE에 입력 UI가 생기면 Body를 `[{accountNo, contributionAmount?}]`로 바꾸고 모인 금액 계산에 반영한다.

### MySQL 검증

- [x] V8 적용(복합 외래키 3개: goal, saving_goal, account)과 `GoalAccount` 엔티티(`@IdClass`)의 `ddl-auto: validate` 통과를 MySQL 8.0에서 확인한다. 2026-09-11 확인: Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용한 뒤 백엔드를 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 32개 전부 통과, `Started DodamApplication`(6.2초). 오류·경고 없음. 493 seed가 `goal_account` 4,750건을 외래키가 살아 있는 상태로 적재했다.

## S15P21D105-510 — 목표 진행률·필요 저축액

구현 계약: [goal.md](goal.md) "계산"

### 저축속도 정의

- [ ] 현재 저축속도를 "확보 금액 / 목표 생성 후 경과 개월(최소 1)"로 정의했다. 생성 전부터 있던 계좌 잔액이 첫 달 속도에 그대로 잡혀 과대평가된다(기획안 예시의 "현재 월평균 저축액"은 최근 저축 흐름을 뜻한다).
- 완료 조건: 이력(511, `goal_history`)이 쌓이면 최근 3개월 `current_amount` 증분의 평균으로 바꾼다. 그 전까지는 현재 정의를 쓴다.

### 위험 판정 기준값

- [ ] 페이스 허용 ±5pt, 일정 지연 10pt, 속도 부족률 30%, 예산 경고 80%는 기본값이다. 진단(14 Diagnosis)의 "모으기 목표 달성 위험"·"줄이기 목표 초과 위험" 이슈가 같은 기준을 쓰도록 543 담당과 맞춘다. 543의 `diagnosis_issue` 심각도 enum과 `riskLevel`(LOW/MEDIUM/HIGH) 대응도 정한다.

### 자유입출금 출금 경고

- [ ] 기획안 "자유입출금통장의 경우 출금을 할 경우 경고메시지 출력"은 구현하지 않았다. 511 이력(`AMOUNT_UPDATED`의 `current_amount` 감소)으로 감지할 수 있게 됐으니, 위험 요인 `BALANCE_DECREASED`를 추가할지 정한다.

### MySQL 검증

- [ ] 계산 API는 새 SQL이 없다(엔티티 조회 + 507 reader 재사용). V8 검증(509 절)과 함께 확인한다.

## S15P21D105-511 — 목표 예측·위험·이력

구현 계약: [goal.md](goal.md) "변화 이력"

### 티켓 범위

- [ ] 511 제목의 "예측·위험"은 510(`forecast`, `risk`)에서 이미 구현했고 511에서는 `goal_history`(V9)와 이력 조회·기록만 추가했다. Jira 하위 태스크 설명과 다르면 재배치한다.

### 주기적 스냅샷

- [ ] 이력은 사용자 행동(생성·수정·연결·계산 API 호출)이 있을 때만 남는다. 아무 호출이 없는 달에는 행이 없어 "월별 진행 추이" 차트(FE `GoalHistoryPoint`)에 빈 달이 생긴다.
- 완료 조건: 일 1회(또는 ACCOUNT 동기화 뒤) 모으기 목표의 `AMOUNT_UPDATED` 스냅샷을 남기는 스케줄러를 두거나, FE가 빈 달을 직전 값으로 채우기로 정한다.

### 저축속도 재정의(510 절 후속)

- [ ] 이력이 쌓이면 저축속도를 "최근 3개월 `current_amount` 증분 평균"으로 바꾼다(510 절 "저축속도 정의"). 지금은 생성 이후 평균이다.

### MySQL 검증

- [x] V9 적용과 `GoalHistory` 엔티티(`DECIMAL(10,4)`↔`BigDecimal`)의 `ddl-auto: validate` 통과를 MySQL 8.0에서 확인한다. 2026-09-11 확인: Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용한 뒤 백엔드를 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 32개 전부 통과, `Started DodamApplication`(6.2초). 오류·경고 없음.

## S15P21D105-492 — Transaction·Spending·Subscription Fixture

산출물: `database/seeds/DODAM_TRANSACTION_SEED.sql`, `database/seeds/DODAM_SUBSCRIPTION_SEED.sql`, `database/seeds/README.md`

### MySQL 실행 검증

- [x] 2026-09-11 Docker `mysql:8.0`(8.0.46)에 V1~V17을 적용하고 525→491→522→523→524→492→493 순으로 실행했다. 계좌·카드번호 fixture는 SHA2 기반 더미(형식만 충족)를 썼다. 아래 "임시 테이블 재참조 오류"를 고친 뒤 원본 파일 그대로 1~7번이 43초에 오류 없이 끝났다.
- 결과: 거래 348,192건(INC 24,000 / FIX 72,000 / SUB 36,000 / SAV 24,000 / VAR 192,192), 계약 위반 0건, 주거래 계좌 `last_transaction_date` 4,000건·휴면 계좌 NULL. 실행 시간 약 14초(변동 슬롯 축소 불필요). 재실행 후 건수·금액 합이 같아 멱등을 확인했다.
- 성향별 소득 대비 소비 / 저축 비율: BALANCED 75.3 / 15.0, SPENDING_FOCUSED 100.4 / 3.0, SAVING_FOCUSED 65.3 / 30.0, VOLATILE 90.4 / 8.0.
- [ ] SPENDING_FOCUSED는 소비(고정 약 45% + 변동 55%)가 소득의 100%를 넘는다. 의도한 과소비 시연이 아니면 변동 비율을 낮춘다.

### 임시 테이블 재참조 오류 (491·492·493·522·523·524·구독 seed 공통)

- [x] MySQL은 한 문장에서 같은 TEMPORARY 테이블을 두 번 참조할 수 없다(`ERROR 1137 Can't reopen table`). 자릿수 테이블 4중 CROSS JOIN, preflight의 스칼라 서브쿼리 다중 참조, `ctx`와 서브쿼리 `c`의 동일 테이블 조인(492 4c·4e), `EXISTS(... preflight)` 게이트가 모두 해당해 491·492·493·522·구독 seed는 사용자 목록 생성에서 실패했다. 8.0.46에서 자기 조인·스칼라 서브쿼리 2개·조인+파생 테이블·EXISTS 네 패턴 모두 실패함을 확인했다. 523·524는 처음부터 파생 테이블·단일 참조 문장으로 작성돼 영향이 없다.
- 조치(2026-09-11): 다섯 파일의 `tmp_dodam_*` 작업 테이블과 491·522의 fixture 테이블을 일반 테이블로 바꾸고 파일 끝 cleanup 절에서 DROP한다. 머리말의 fixture 계약도 `CREATE TABLE`로 바꿨으므로 fixture 빌더는 TEMPORARY가 아닌 일반 테이블을 만들어야 한다(523·524 fixture는 TEMPORARY도 가능).
- [ ] 파일이 중간에 멈추면 작업 테이블이 스키마에 남는다(다음 실행의 `DROP TABLE IF EXISTS`가 정리). 운영 DB에 적재할 때 `tmp_dodam_%` 잔여 테이블을 확인한다.

### 525 금융상품 seed의 `product_sync_history`

- [x] `DODAM_FINANCIAL_PRODUCTS_*_COMPAT_FIXED.sql` 두 파일은 COMMIT 직전에 `product_sync_history`에 INSERT했는데, 이 테이블은 ERD에만 있고 V1~V17 마이그레이션과 엔티티에 없다. Flyway로 만든 DB에서는 여기서 멈추고 트랜잭션이 롤백돼 상품이 하나도 들어가지 않았다. 2026-09-11 INSERT와 그 전용 변수(`@dodam_existing_before`)를 제거했다. 결과: 상품 198건·옵션 456건, 위반 0건.
- [ ] 상품 동기화 이력이 필요해지면 `product_sync_history` 마이그레이션(BE-B 구간)과 엔티티를 만든 뒤 seed에 이력 INSERT를 되살린다.

### 구독 seed의 선행 마이그레이션

- [x] `DODAM_SUBSCRIPTION_SEED.sql`은 V30(`subscription`·`subscription_payment_history`, 2026-09-11 S15P21D105-512)이 생긴 뒤 적재할 수 있다. 같은 날 Docker `mysql:8.0`에 V1~V17+V30 적용 → seed 1~7 → 8번 순으로 실행해 구독 6,000건(ACTIVE 5,840 / PAUSED 160), 결제 이력 36,000건, 카드 불일치 0, 거래 SUB 행과 불일치 0을 확인했다(약 1초). 거래 seed의 SUB 행(12일·25일, 9,900/12,400/14,900/4,900원)과 금액·날짜가 맞는다.

### 시연 사용자 데이터 결

- [ ] 거래는 seed 사용자(`seed.kmeans.u0001~u4000`)에만 생긴다. 실제 회원가입 계정으로 시연하려면 금융망 동기화(ACCOUNT scope)로 거래를 받아야 하는데 API Key 문제(499 절)가 남아 있다. 시연 계정을 seed 사용자 중 하나로 정하고 비밀번호를 애플리케이션으로 세팅하는 절차(491의 `password_hash NULL` 항목)가 필요하다.
- [ ] `balance_after`는 NULL이다. 계좌 거래내역(5)·마이너스통장(10-1) 응답의 `balanceAfter`가 seed 데이터에서는 비어 보인다. 잔액 재생(replay)을 넣을지 정한다.

## S15P21D105-493 — Goal·GoalAccount·GoalHistory Seed

산출물: `database/seeds/DODAM_GOAL_SEED.sql`

### MySQL 실행 검증

- [x] 2026-09-11 MySQL 8.0.46에서 실행했다(492 절 "임시 테이블 재참조 오류" 조치 포함). 최초 규칙으로는 preflight가 `users_with_saving_account_count = 3000`으로 실패했다. 524가 예금 500·적금 500 계좌를 CLOSED로 만들어 사용자 1,000명(40명 주기의 25~32번째, 성향별 250명)은 열린 예·적금 계좌가 없고, Goal API는 CLOSED 계좌 연결을 400으로 막는다([goal.md](goal.md) 계좌 연결 표).
- 조치: 열린 예·적금이 없는 사용자는 주거래 입출금 계좌를 대신 연결한다(`COALESCE(saving_account_id, primary_account_id)`, BALANCED는 주거래 중복 연결 방지). 기대 연결 수는 5,000 → 4,750.
- 결과: 모으기 4,000(ACTIVE 3,900 / PAUSED 100), 줄이기 2,000, 연결 4,750(예금 1,500 / 적금 1,500 / 주거래 1,750, CLOSED 연결 0), 이력 30,000, `current_amount` 불일치 0, 연결 계약 위반 0, 진행률 범위 위반 0, 실행 약 2초. 재실행 후 건수·금액 합 동일.
- 참고: 재실행은 목표를 지우고 다시 만들므로 `goal_id`가 바뀐다. 시연 스크립트에 goal_id를 고정하지 않는다. `goal_id`를 (user_id, type, created_at 마커)로 되찾는 방식은 사용자당 유형별 목표가 하나라는 seed 전제에 의존하며, 앱에서 `2026-03-01 09:00:00`에 목표를 만들 수는 없으므로 실무에서는 충돌하지 않는다.

### 이력 스냅샷의 현실성

- [ ] 월말 이력은 현재 연결 잔액을 6등분해 선형으로 채운 것이다(계좌 잔액 이력이 없어 실제 추이를 만들 수 없음). `forecast_date`는 NULL이고 API가 실시간 계산한다. 시연에서 "예상 달성일 변화"를 보이려면 이력에 `forecast_date`를 채우는 규칙을 정한다.

### 줄이기 목표 예산 수준

- [ ] SPENDING_FOCUSED·VOLATILE 사용자의 월 예산을 소득의 45%로 두어 대부분 WARNING/EXCEEDED가 나오게 했다(시연용). 진단(14)의 "줄이기 목표 초과 위험" 표본이 과다하면 비율을 조정한다.

## S15P21D105-512 — Subscription 목록·상세·요약

구현 계약: [subscription.md](subscription.md)

### 금융망 동기화 부재

- [ ] 정기결제 데이터는 시연 seed(`DODAM_SUBSCRIPTION_SEED.sql`)만 적재한다. 명세의 "SSAFY 연동"에 해당하는 동기화 핸들러(Finance Data `FinanceSyncScopeHandler`, 499 절)가 없어 실제 회원가입 계정에는 정기결제가 하나도 없다. 금융망에 정기결제 조회 API가 있는지 확인한 뒤 CARD 거래에서 파생하는 방식(513 후보 탐지 결과를 등록)으로 갈지 정한다.
- 근거: SSAFY 금융망 문서에는 정기결제 도메인이 없다(카드 거래내역만 있음). 그래서 후보 탐지(513)를 "자체 탐지"로 뒀다.

### EXPIRED 자동 전이

- [ ] `end_date`가 지나도 상태는 ACTIVE로 남는다. EXPIRED는 seed·동기화가 넣는 값으로만 존재한다. 일 1회 배치(현금흐름 이벤트 수집과 함께)나 조회 시점 계산 중 하나를 정한다. 조회 시점 계산을 택하면 요약 금액에서 종료일 지난 건을 빼야 한다.

### 목록 처리 방식

- [ ] 목록 필터·정렬·페이지를 메모리에서 한다(사용자당 수십 건 전제, Goal과 같음). 동기화로 건수가 수백 건이 되면 `Specification` 조회로 바꾼다.
- [ ] `category` 필터는 저장 문자열과 대문자 비교다. 금융망이 우리 enum 밖의 카테고리를 넣으면 카테고리 목록(513)에는 `ETC`로 세지만 필터로는 원래 코드로만 잡힌다. 적재 시 정규화 규칙을 정한다.

### MySQL 검증

- [x] 2026-09-11 Docker `mysql:8.0`(8.0.46)에 V1~V17 + V30을 적용(빈 DB라 V6 문제 때문에 out-of-order 순서로 손 적용, 502 절)하고 `local` 프로필(`ddl-auto: validate`, Flyway off)로 기동해 엔티티 34개 통과. 시연 seed 8번 적재: 구독 6,000·이력 36,000·불일치 0. seed 사용자 `seed.kmeans.u0001`로 로그인해 목록(1건, 카드 `My WE:SH 카드` 조인)·요약·상세(이력 6건 집계)·이력(2026-03~08, 6건)이 기대값과 같았다.

## S15P21D105-513 — Subscription 반복결제 후보·서비스

구현 계약: [subscription.md](subscription.md) "513"

### 후보를 정기결제로 등록하는 경로

- [ ] 명세 7에는 등록(POST) API가 없어 후보 탐지 결과를 정기결제로 만들 방법이 없다. 후보 응답에 `merchantName`·`amount`·`estimatedBillingCycle`·`suggestedPaymentDay`·`cardId`·`suggestedCategory`를 넣어 등록 Body를 바로 만들 수 있게 해 두었다. `POST /api/v1/subscriptions`를 명세에 추가할지 팀에서 정한다.

### 탐지 임계값

- [ ] 기본 3회·6개월, 주기 판정 범위(주 5~9일, 월 25~36일, 연 350~380일)는 seed 거래(매월 12일·25일 결제)가 잡히도록 잡은 값이다. 기획안 "정기결제 점검" 화면의 요구(예: 2회면 경고)에 맞춰 조정한다.
- [ ] 가맹점명 정규화가 공백·대소문자 제거뿐이다. "넷플릭스"와 "NETFLIX.COM", "쿠팡와우"와 "쿠팡(와우멤버십)"은 다른 묶음이 된다. 카탈로그 키워드 매칭(`matchedServiceCode`)을 묶음 기준에도 쓸지 정한다.
- [ ] 금액이 바뀐 요금제(예: 9,900 → 13,500)는 두 묶음으로 갈라져 각각 횟수가 줄어든다. 가맹점 단위로 묶고 금액 변동을 허용(±20%)하는 방식을 검토한다.

### 서비스 카탈로그

- [ ] `SubscriptionServiceCatalog`는 코드에 고정한 30개 정적 목록이다(2026-09-11 요금 기준). 서비스·요금이 바뀌면 코드 수정이 필요하다. 테이블(`subscription_service`)과 관리자 API로 옮길지, FE가 자체 목록을 갖는지 정한다. `iconKey`는 FE `assets/icons/payments`의 파일명과 맞춘 것이며 FE가 아이콘을 추가하면 같이 갱신한다.

## S15P21D105-514 — Subscription 변경·정지·취소 + Goal 회귀

구현 계약: [subscription.md](subscription.md) "514"

### 금융망 반영 없음

- [ ] 수정·정지/재개·취소는 우리 DB만 바꾼다. 금융망에 정기결제 변경 API가 없어 전달 경로가 없다. 실제 해지는 각 서비스에서 해야 하므로 FE 문구("도담 목록에서만 정리됩니다")를 맞춘다.

### 다음 결제일 재계산

- [ ] WEEKLY·YEARLY는 기존 다음 결제일(없으면 생성일)을 기준점으로 쓴다. seed는 MONTHLY만 만들어 WEEKLY·YEARLY 경로는 단위 규칙 수준으로만 검증했다(통합 테스트는 YEARLY 목록·요약만). 실데이터가 들어오면 기준점 규칙을 재확인한다.
- [ ] 결제일이 바뀐 ACTIVE 정기결제만 다음 결제일을 다시 잡는다. PAUSED 상태에서 결제일을 바꾸면 재개 시점에 새 결제일로 계산된다(의도한 동작이지만 문서화만 했다).

### 취소의 의미

- [ ] DELETE는 soft(상태 CANCELED, `canceled_at`)라 목록·요약 건수에 계속 들어간다(금액 합계에는 빠짐). 사용자가 "목록에서 지우기"를 원하면 숨김 필드나 물리 삭제가 필요하다.

### Goal 회귀 테스트

- Goal 회귀는 별도 티켓 515 절에 기록한다(H2 전체 테스트 + MySQL seed 데이터 API 회귀).

### MySQL 검증

- [x] 2026-09-11 MySQL 8.0 seed 데이터(`SUB-U0001-01`, 다음 결제일 2026-09-12)로 확인: 정지(200, 다음 결제일 null) → 재개(200, 2026-09-12 재계산) → 종료일 2026-09-11 지정 400 `INVALID_REQUEST`(다음 결제일보다 앞, DB CHECK에 닿기 전에 막힘) → 종료일 2026-12-31·금액 13,900 수정 200 → 같은 금액 재요청 200(`updated_at` 유지) → 취소 200 → 재취소 409 `SUBSCRIPTION_NOT_EDITABLE` → 타인 건 404. 직접 UPDATE로 `chk_subscription_dates` 위반 시 MySQL이 3819로 막는 것도 확인했다. 후보 탐지는 seed 고정 지출 `도담 보험`(6회, 20일)·`도담 주거 관리사무소`(5회, 1일)를 MONTHLY로 잡고, 등록된 `도담 비디오`와 NON_SPENDING 저축 이체는 뺐다.

## S15P21D105-515 — Goal 회귀 테스트 (D7 "12 Goal 완료")

구현 계약: [goal.md](goal.md)

### H2 전체 테스트

- [x] 2026-09-11 Subscription(512~514) 추가 뒤 `./gradlew test` 전체 833개 통과(실패·오류 0). Goal 통합 테스트 4종(`GoalApiIntegrationTest`, `GoalAccountApiIntegrationTest`, `GoalCalculationApiIntegrationTest`, `GoalHistoryApiIntegrationTest`) 포함. Subscription fixture가 `card`·`transaction`을 DROP CASCADE로 다시 만들지만 Goal fixture(`goal-test-data.sql`)와 충돌하지 않았다.

### MySQL seed 데이터 API 회귀

- [x] 2026-09-11 Docker `mysql:8.0`(8.0.46)에 V1~V17+V30 적용, seed 1~8 적재(목표 6,000·연결 4,750·이력 30,000·거래 348,192) 후 `local` 프로필로 기동해 seed 사용자 u0001(BALANCED, 모으기 1개·연결 2건)·u0002(SPENDING_FOCUSED, 모으기+줄이기)로 Goal API 14개 중 13개를 호출했다. 앱 오류 로그 0건.
- 조회 결과가 seed 기대값과 모두 일치했다.
  - u0001 목표 1: `currentAmount` 29,160,000 = 연결 계좌(주거래 19,160,000 + KB 정기예금 10,000,000) 잔액 합, 목표 16,100,000 → 진행률 100%, `paceStatus = ACHIEVED`, 필요 월 저축액 0, 예상 달성일 = 기준일, 위험 NONE. 이력 7건(GOAL_CREATED + 월말 AMOUNT_UPDATED 6, 2026-03~08 선형 증가). 줄이기 전용 API 호출 시 400, 타인 목표 404.
  - u0002 목표 2: 1,800,000 / 32,000,000 → 5.6%, 기대 진행률 45.5%, `BEHIND`, 위험 HIGH(`PACE_INSUFFICIENT`, `BEHIND_SCHEDULE`), 필요 월 저축액 3,775,000. 목표 3(줄이기 1,800,000): 9월 소비 0(거래 seed가 8월까지라 정상), `previousMonthSpending` 4,247,800 = 8월 EXPENSE·비 NON_SPENDING 합과 일치, 위험 LOW. 요약: 모으기 목표 합 32,000,000 / 1,800,000, 월 예산 합 1,800,000.
- 쓰기 경로(MySQL CHECK·복합 외래키 살아 있는 상태): 모으기 생성 201 → 이름·목표 금액 수정 200 → PAUSED 200 → PAUSED→ACHIEVED 409 `GOAL_STATUS_TRANSITION_NOT_ALLOWED` → 모으기에 월 예산 400 → 이력에 GOAL_CREATED·GOAL_UPDATED·STATUS_CHANGED 기록 → 연결 계좌 0건·진행률·위험(`NO_LINKED_ACCOUNTS`) 200 → 삭제 200(soft, GOAL_DELETED 이력) → 조회 404. 줄이기 생성(예산 0은 400 `VALIDATION_ERROR`) → 소비 진행률 200 → 삭제 200. seed 목표 6,000건은 그대로다.
- [ ] `PUT /goals/{goalId}/accounts`는 호출하지 못했다. Body가 계좌번호 원문(`accountNos`)을 받아 서버가 해시로 찾는데, seed 계좌의 해시는 검증용 더미라 원문이 없다. 애플리케이션 코덱으로 만든 fixture(491 계약)로 seed를 적재한 환경에서 다시 확인한다. H2 통합 테스트(`GoalAccountApiIntegrationTest`)로는 통과했다.
- 참고: 계산 API(progress·required-saving·forecast·risk)는 금액이 바뀌지 않으면 `AMOUNT_UPDATED` 이력을 남기지 않아 seed 목표의 이력은 7건 그대로였다. 목표 1은 모인 금액이 목표를 넘었는데 상태는 ACTIVE로 남는다(508 절 "모인 금액 도달 시 상태" 미결 항목과 같음).
- 검증 중 확인한 도구 문제: Windows Git Bash의 `curl -d '한글'`은 본문을 UTF-8이 아닌 코드페이지로 보내 서버가 400 `INVALID_REQUEST`(본문 해석 실패)를 돌려준다. 앱 문제가 아니며 `--data-binary @utf8파일`로 보내면 정상이다. 시연·수동 테스트 시 주의.
## 544·545 진행 현황 (2026-09-13)

- 진단 실행·이력·latest·상세·summary·문제 목록/상세 API 추가. 현재 계약: [diagnosis-api.md](diagnosis-api.md).
- 목표/당일 저장 현금흐름 위험은 연결했으나 기획안 전체 규칙은 미완료. 미정 임계값·규칙은 coverage로 공개한다.
- 군집 공급자·적격 조건 검사·동일 카테고리 근거 병합은 후속 구현이며 저장 군집 근거 조회만 지원한다.
- MySQL에서 사용자 잠금·만료 경쟁 테스트 필요. 실행 실패는 미완료 접수만 삭제하며 실패 이력을 보존하지 않는다.
- 과거 543 항목의 input_snapshot·종합 상태·goalId 검증은 현재 API 문서의 부분 구현 범위와 함께 해석한다.

## 546 진행 현황 (2026-09-13)

- goal-impact·changes 추가. 계약: [diagnosis-api.md](diagnosis-api.md), 계획: [diagnosis-goal-impact-plan.md](diagnosis-goal-impact-plan.md).
- [ ] "금융비용으로 인한 목표 달성 지연"은 대출 이자·상환 규칙(`LOAN_INTEREST_BURDEN` 등) 정책 확정 후 계산한다. 현재 `unavailable`로 표시.
- [ ] 이전 진단은 같은 goalId 완전 일치 기준이다. 전체 진단과 목표 지정 진단 사이 비교가 필요하면 FE 요구를 확인한 뒤 확장한다.
- [ ] `DiagnosisRepository.findPreviousCompleted`(NULL goalId 비교 JPQL)는 H2만 확인했다. MySQL에서 실행 계획·결과를 확인한다.

## S15P21D105-516 — Pattern 분석 실행

구현 계약: [pattern.md](pattern.md)

### 금융 데이터 연동 조건

- [ ] 명세 "금융 데이터 연동 완료 필요"를 "분석 기간에 거래가 있음"으로 구현했다. seed 사용자는 `finance_connection` 행이 없어 연결 상태를 강제하면 시연 계정이 전부 막히기 때문이다. 거래가 없을 때만 연결 상태를 봐서 `FINANCE_NOT_CONNECTED`와 `PATTERN_INSUFFICIENT_DATA`를 나눈다. 실계정 흐름(회원가입 → 연결 → 동기화 → 분석)에서 연결 상태를 우선할지 팀에서 정한다.

### 분석 기간과 진행 중인 달

- [ ] 기간은 기준일이 속한 달을 뺀 완료된 달 6개다. 시연일(2026-09)에는 seed 창(2026-03~08)과 정확히 맞지만, 10월 이후 시연하면 9월이 빈 달(0)로 들어와 규칙성·안정성이 떨어진다. 시연 날짜가 정해지면 seed 창을 옮기거나 `lookback-months`를 조정한다.
- [ ] 저축성 지출은 API 6과 같이 NON_SPENDING EXPENSE 전체(저축·투자 + 이체)다. 일반 이체(TRANSFER 카테고리)가 많은 사용자는 저축률이 과대 계산된다. SAVING 카테고리만으로 좁힐지 API 6과 함께 정한다.

### 실행 정책

- [ ] 동기 실행이다. seed 4,000명 모집단 학습은 첫 요청에서만 일어나고(TTL 1시간) 그 뒤는 사용자 집계 1회라 응답 시간 문제가 없다고 봤지만 MySQL 실측은 아래 항목과 함께 한다.
- [ ] 결과 재사용 기준은 "오늘 완료·같은 기간·같은 요청 모드"다. 거래가 새로 동기화되면 같은 날이라도 `forceRefresh=true`로 다시 분석해야 한다. Finance Data 동기화(499) 완료 시 자동 무효화(또는 `analyzed_at` < `last_synced_at`이면 재계산)를 붙일지 정한다.
- [ ] FAILED 행은 조회에 나오지 않고 정리 배치도 없다. 누적되면 사용자별 최근 N건만 남기는 정리를 붙인다.

### MySQL 검증

- [ ] V31 `validate` 통과, `DECIMAL(10,4)`·`JSON` 컬럼 매핑, seed 사용자 분석 실행(모집단 4,000명 학습 시간 포함)을 MySQL 8.0에서 확인한다. H2 통합 테스트만 수행했다. 2026-09-14 작업 PC에 Docker 데몬이 꺼져 있고 로컬 MySQL80 서비스는 자격증명이 없어 미실행.

## S15P21D105-517 — Pattern 결과·프로필 조회

구현 계약: [pattern.md](pattern.md) "조회"

### 우선순위 프로필 계약

- [ ] 가중치·집중 영역 규칙(부채부담 0.30, 재량소비 0.40, 소비/소득 0.80, 저축률 0.20·0.30, 고정지출 0.30~0.70, α = 0.2 × 신뢰도)은 기획안에 수치가 없어 정한 서비스 기본안이다. 추천 엔진(API 16, BE-B)이 `weights`·`clusterDeviationAlpha`·`focusAreas` 중 무엇을 어떻게 쓸지 맞춘 뒤 수치를 조정한다. 현재 Recommendation 우선순위 계산은 이 API를 읽지 않는다.
- [ ] 성향검사(기획안 "선택")가 들어오면 `source`에 `PREFERENCE_SURVEY`를 추가하고 패턴분석과의 우선 규칙을 정한다.

### 소비·자금운용 패턴

- [ ] 소비 패턴의 `clusterProfile`은 `months`와 무관하게 최근 완료 분석의 것이다. `months=3` 요청에 6개월 기준 군집이 붙는 것이 FE 표시에 맞는지 확인한다.
- [ ] 카테고리 비중은 소비 정의(NON_SPENDING 제외) 기준이라 API 6 `/spending/categories`와 같은 값이다. FE가 둘을 함께 쓰면 하나로 줄일 수 있다.
- [ ] `months` 밖 형식(예: `abc`)은 공통 처리로 400 `INVALID_REQUEST`다. 명세에 오류 코드가 없어 다른 조회 API와 같은 방식을 따랐다.

## S15P21D105-518 — Pattern 군집 메타데이터·Fallback

구현 계약: [pattern.md](pattern.md) "군집 메타데이터·Fallback"

### 모델 운영

- [ ] 모델은 프로세스 메모리에 1시간 캐시된다. 서버가 여러 대면 서로 다른 시점의 모델이 공존할 수 있다(모델 버전은 같음). 배정 결과는 저장 시점 통계를 JSON으로 남기므로 조회 일관성은 유지되지만, 재현이 필요하면 학습 결과(중심·표준화 파라미터·범위)를 테이블이나 Redis에 저장하는 방식으로 옮긴다.
- [ ] 임계값(모집단 30, 군집 표본 10, 신뢰도 0.2, k = 4)은 seed 성향 4종을 전제로 한 기본안이다. 실사용자 분포에서 k와 임계값을 재검토한다. 기획안 "군집 이탈 점수 ≥ 임계값"은 진단(API 14)이 적용할 조건으로 보고 여기서는 점수(`deviationScore`)만 제공한다.
- [ ] 라벨은 중심값 순위로 정한다. 두 군집의 저축률 중심이 거의 같으면 학습마다 라벨이 뒤바뀔 수 있다. 실데이터에서 라벨 안정성을 확인하고 필요하면 운영자 고정 라벨 테이블로 바꾼다.

### 진단·추천 연동

- [ ] Diagnosis(545)의 "군집 범위 이탈" 규칙은 "Preference 공급 경로의 유효한 군집 집계"를 기다리고 있다. `PatternAnalysis.clusterStats`(features[].deviation·p5·p95·sampleSize·modelVersion)가 그 공급 경로다. BE-B와 `DiagnosisIssue.clusterContext` 형식(`feature`, `rangeMin`, `rangeMax`, `userValue`, `sampleSize`)으로의 변환 위치를 정한다.
- [ ] 모집단 특징은 INCOME 거래만 소득으로 쓴다(금융 기본정보 미조회). 급여 이체가 없는 사용자는 학습에서 빠지고 본인 분석은 PROFILE 소득으로 특징을 만들어 배정된다. 분포 차이가 크면 모집단도 기본정보 소득을 쓰도록 집계 SQL에 조인을 추가한다.
