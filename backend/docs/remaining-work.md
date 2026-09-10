# 백엔드 남은 작업

마지막 갱신: 2026-09-10

백엔드 작업 중 확인한 미완료 항목을 이 파일에 계속 기록한다. 현재 등록 범위는 S15P21D105-499, 500, 530, 532, 533, 535, 536, 537이다.

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

- [ ] `FinanceSyncScopeHandler` 구현을 도메인별로 등록한다. 현재 등록된 구현이 없어 `POST /finance-data/sync`는 모든 scope를 SKIPPED로 보고하고 `last_synced_at`을 갱신하지 않는다.
- 담당 근거: 테이블 소유 도메인이 구현한다. ACCOUNT는 BE-A(S15P21D105-501, `Account` 엔티티와 함께), CARD·LOAN·SAVING은 BE-B 영역이다. 새 담당 배정을 의미하지 않는다.
- 선행 조건: 시연 데이터를 로컬 DB에서 읽는 현재 방식과 금융망 실시간 동기화의 역할 분담을 팀이 정한다. 금융망에서 받은 행은 `source_type = 'SSAFY'`, 시연 데이터는 `SEED`로 구분해 공존시키는 것을 전제로 한다.
- 완료 조건: scope별 handler가 금융망 조회 → 로컬 upsert를 수행하고, 실행 응답의 `syncedCount`와 `last_synced_at` 갱신을 통합 테스트로 확인한다.

### MySQL 검증

- [x] V13까지 적용된 MySQL에 V4를 추가하면 `FlywayValidateException`이 나는 문제(MR 리뷰, 2026-09-09)를 `spring.flyway.out-of-order: true`(local/prod)로 해결했다. 번호 구간 분할 자체가 이 옵션을 전제로 한다. `FlywayOutOfOrderTest`로 재현·검증.
- [ ] `V4__create_finance_sync.sql`을 MySQL 8.0에 적용해 `ddl-auto: validate` 통과와 `FinanceSync` 엔티티 매핑(VARCHAR(30) enum, DATETIME)을 확인한다. H2 통합 테스트만 수행했다. out-of-order 적용은 리뷰어가 기존 DB에서 재확인 예정.
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
