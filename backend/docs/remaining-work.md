# 백엔드 남은 작업

마지막 갱신: 2026-09-10

백엔드 작업 중 확인한 미완료 항목을 이 파일에 계속 기록한다. 현재 등록 범위는 S15P21D105-499, 500, 530, 532, 533, 535이다.

## S15P21D105-535 — 예·적금 ERD 제약 동기화

구현 계약: [deposit-savings.md](deposit-savings.md)

- [ ] 루트 ERD SQL의 `chk_deposit_saving_withdrawal_account`에 V15와 같은 `withdrawal_account_type IS NOT NULL` 조건을 반영한다.
- 근거: MySQL CHECK는 UNKNOWN을 허용한다. 원본은 출금계좌 ID가 있어도 유형 NULL로 복합 FK를 우회할 수 있어 V15에서 보완했다.
- 선행 조건: 루트 docs 수정은 사용자 명시 요청 범위가 아니므로 이번에는 변경하지 않았다.
- 완료 조건: ERD와 V15가 같은 출금계좌 NULL 조합·유형·소유권 제약을 표현한다.

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
