# 대출 금리비교·상환우선순위 진행계획 (S15P21D105-533)

## 1. 문서 성격과 근거

**계획 문서이며 구현 완료 문서가 아니다.** 작성일 2026-09-09.

- 사용자 지정 작업 브랜치는 `S15P21D105-533-api-10`이다. 작성 시점의 체크아웃은 `develop`, HEAD는 `1247b18`이며 브랜치 생성·커밋·push는 수행하지 않았다.
- 로컬 `develop`에 531 대출 조회와 532 대출 부담·이자 분석이 반영되어 있다.
- 연결된 Jira 사이트에서 533을 조회하지 못했다. 로컬 `SPRINT/Jira (4).csv`의 제목은 `[API 10] 금리비교·상환우선순위`, 설명은 `고금리·시장비교·우선순위 구현`이다. 최신 Jira 완료 조건을 대신하지 않는다.

기준 자료는 다음과 같다.

| 자료 | 사용 범위 |
| --- | --- |
| `docs/API명세서/10 Loan ...csv` | 3개 API의 경로·입력·제한사항 |
| `docs/API명세서`의 `12 Goal`, `14 Diagnosis`, `16 Recommendation`, `17 Simulation`, `19 Improvement` | 2절 소유 경계 |
| `docs/기획안/기획안 작성 ...md` | 고금리 부채 식별(602~646행), 고금리 대출 우선 상환(1276행~) |
| `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql` | loan, financial_company, financial_product, financial_product_option, goal |
| `backend/docs/loan-query.md`, `loan-analysis.md`, 기존 Loan·Product 코드 | 재사용할 계약과 응답 어휘 |
| `backend/database/seeds/` | 3절 데이터 확인 |

3절 이후의 기본값·응답·판정 정책 중 원본 명세에 없는 항목은 이 문서의 **제안**이다. 10절에 결정 현황을 정리했다. 루트 `docs/`의 명세와 ERD는 수정하지 않는다.

## 2. 작업 범위와 인접 API 경계

모두 인증이 필요한 GET API다.

| 경로 | 명세 입력 | 목적 |
| --- | --- | --- |
| `/api/v1/loans/high-interest` | `loanType?`, `industry?`, `thresholdMode?` | 동일 상품군·업권 기준 고금리 점검 대상 조회 |
| `/api/v1/loans/{accountNo}/rate-comparison` | `accountNo`, `term?`, `industry?`, `topN?` | 보유 대출과 상품 DB 공시금리 비교 |
| `/api/v1/loans/repayment-priority` | `goalId?` | 보유 대출의 상환 검토 순서와 근거 조회 |

534 마이너스통장, 실제 상환·대환 실행, 외부 상품 동기화, 신용평가 수집, 목표 CRUD는 범위 밖이다. 532의 이자 예측·부담률 계약은 유지한다.

533이 만들지 않는 값은 대부분 다른 영역이 이미 소유한다. 533은 그 API들의 입력이 되는 비교·순서 정보만 제공한다.

| 소유 API | 533에 주는 의미 |
| --- | --- |
| `POST /api/v1/simulations/loan/prepayment` | 추가 상환 절감액은 이 API가 계산한다. 533은 권장 상환금액·확정 절감액을 만들지 않는다 |
| `POST /api/v1/simulations/loan/refinance` (`targetProductId`) | 대환 계산은 이 API 소관이다. `rate-comparison`의 `candidates[].productId`가 그 입력이 되도록 상품 ID를 반환한다 |
| `GET /api/v1/recommendations/priorities` (`goalId?`) | 목표를 반영한 우선순위는 이 API가 소유한다. 533은 대출 도메인 내부의 금리 기준 순서만 제공한다 |
| `GET /api/v1/diagnoses/{id}/issues`와 `diagnosis_issue` 테이블 | 고금리 판정은 진단 이슈의 원천이다. 6절 응답 필드를 그 계약과 정합하게 둔다 |
| `GET /api/v1/improvements/savings`, `/debt-reduction` | 실제 절감·부채감소 실적은 이 영역이 집계한다 |

## 3. 데이터 확인 결과

seed SQL과 ERD, 기존 코드를 직접 확인한 결과다. **MySQL에 적재해 실행한 값이 아니라 정적 분석이며 구현 후 실제 조회로 재확인한다.**

### 상품과 대출

| 항목 | 확인한 상태 | 계획에 준 영향 |
| --- | --- | --- |
| 상품 규모 | LOAN 상품 76개, 옵션 319건 | 8절 조회 설계의 전량 로드 근거 |
| 상품 유형 | `loan_type` 문자열 39종. PERSONAL_CREDIT 19, JEONSE 6, POLICY_CREDIT 5, MORTGAGE 3, LEASE_* 7, AUTO 계열 3 등 | Loan enum 5종과 직접 비교 불가. 5절 대응표 필요 |
| 상환방식 | 옵션 값은 BULLET 113, EQUAL_PRINCIPAL_INTEREST 85, EQUAL_PRINCIPAL 79, AMORTIZING 24, CREDIT_LINE 11, OVERDRAFT 1, NULL 6. Loan enum은 EQUAL_PRINCIPAL/EQUAL_PAYMENT/BULLET | 문자열 일치로는 `EQUAL_PAYMENT` 후보가 0건. 5절 대응표 필요 |
| 금리 | 대출 상품 옵션 319건 중 `rate`가 NULL인 것이 308건이고 값이 있는 것은 11건이다. 그 11건은 `아낌e 보금자리론`(POLICY_MORTGAGE) 6건, `개인사업자 보증서대출`(SOHO_GUARANTEE) 3건, `KB 새희망홀씨II`(POLICY_CREDIT) 2건 등이다 | 비교에는 옵션 minRate/maxRate만 사용한다. 근거는 아래 참조 |
| 옵션 금리 분포 | 옵션 min/max는 대개 상품 min/max의 복사값이다(KB 직장인든든의 term=12 옵션 4건 모두 6.04~6.94) | 기간 조건은 표본만 줄이고 금리값을 바꾸지 않는다. 5절 기간 밴드의 근거 |
| 옵션 기간 | `term`이 NULL인 옵션 8건 | 밴드를 확정할 수 없어 비교에서 제외 |
| 업권 | 회사 9개뿐이며 `BANK` 5, `CARD_COMPANY` 4다. `SAVINGS_BANK`는 없다 | 초기 지원 업권은 `BANK` 단일 |
| 기관 코드 | 시연 대출의 `institution_code`(KB_KOOKMIN·SHINHAN_BANK·KAKAOBANK·WOORI_BANK)가 모두 `financial_company.company_code`와 동일 | 별도 대응표 없이 직접 조인 |
| 신용조건 | 신용점수 구간별 평균금리가 없다 | 기획안의 동일 신용구간 중앙금리는 구현하지 않는다 |
| 동기화 | 상품별 `syncedAt` 존재. 모집 종료·배치 식별 계약 없음. seed는 `source_type`을 지정하지 않아 ERD 기본값 `MANUAL`로 적재된다 | 스냅샷 일시와 `sourceType`을 함께 반환하고 실시간·판매 가능을 보장하지 않는다 |
| 목표 | `goal` 테이블 마이그레이션이 없다(현재 V1~V3, V10~V13). Goal 도메인 코드도 없다 | `goalId` 미지원 확정. 7절 참조 |

**옵션 `rate`를 쓰지 않는 근거.** 값이 있는 11건을 확인한 결과 비교 결과가 달라지지 않는다.

- `아낌e 보금자리론` 6건은 `rate`(4.90~5.20)가 min/max(3.90~5.40)와 다른 유일한 사례지만 `repayment_type`이 NULL이라 5절 대응표에서 이미 제외된다. 3절 상환방식 분포의 NULL 6건이 바로 이 옵션들이다.
- 나머지 5건은 `rate`와 minRate·maxRate가 모두 같은 값이라 minRate만 써도 결과가 동일하다.
- 따라서 minRate 기준을 유지하면서 `rate`를 무시해도 손실이 없다. 반대로 `rate`를 우선하면 308건에 값이 없어 표본이 붕괴한다.

### 재사용 가능한 기존 자산

| 자산 | 위치 | 용도 |
| --- | --- | --- |
| `LoanSpecifications.ownedBy` | loan/repository | 본인 대출 조회 |
| `LoanStatus.isOutstanding()` | loan/entity | ACTIVE·OVERDUE 필터 |
| `LoanRepository.findByUserIdAndAccountNoHash` | loan/repository | accountNo 경로 조회 |
| `LoanAnalysisClock` | loan/service | Asia/Seoul 기준일 |
| `SensitiveNumberHasher`, `AuthenticatedUserIdResolver` | global | 계좌 해시, 인증 사용자 |
| `ProductQueryService`의 `*_LOAN_TYPES` 상수 | product/service | 5절에서 차이만 비교. 수정하지 않는다 |

상환 우선순위 API는 위 자산만으로 구현 가능하며 Repository 추가가 필요 없다.

### 관련 ERD 제약

- `chk_loan_interest_rate`: `interest_rate >= 0`이고 NOT NULL이다. 금리 무효는 MySQL에서 발생하지 않는다.
- `chk_loan_period`: `loan_period IS NULL OR > 0`. NULL은 가능하다.
- `chk_financial_product_option_rates`: `min_rate >= 0`과 `min_rate <= max_rate`를 보장한다. 음수·역전은 발생하지 않는다.
- `chk_financial_product_option_term`: `term IS NULL OR term > 0`.
- `chk_goal_account_type`: `DEMAND_DEPOSIT`, `DEPOSIT`, `SAVINGS`만 허용하고 `fk_goal_account_saving_goal`은 저축목표만 참조한다. **목표에 대출을 연결할 수 없다.**
- `spending_goal.monthly_budget`은 소비목표 전용이며 저축목표에는 예산 필드가 없다.

### 확정 정책 기준의 편차 분포

5절 정책을 모두 적용해 시연 대출을 계산한 결과다. 8절 5단계의 임계값 결정 근거로 사용한다.

| 시연 대출 | 후보 수 | 기준금리 | 편차 |
| --- | --- | --- | --- |
| 신한 사잇돌 7.90% / 36개월 (중금리군, OVERDUE) | 5 | 4.871% | +3.03%p |
| KB 직장인든든 5.40% / 36개월 | 11 | 5.229% | +0.17%p |
| 신한주택 3.90% / 240개월 | 2 | 4.729% | -0.83%p (`referenceConfidence=LOW`) |
| HF 전월세 3.20% / 36개월 | 5 | 4.549% | -1.35%p |
| 우리 드림카 4.70% / 60개월 | 1 | null | 표본 미달 |
| 중고차 4.20% / 36개월 | 0 | null | 후보 없음 |

6건 중 4건이 비교 성립한다. `rateSpreadThreshold`가 3.03%p 이하면 1건, 0.17%p 이하면 2건이 판정된다.

**폐기한 기준의 기록.** 초기안이던 공시 범위 중간값 `(minRate + maxRate) / 2`은 공시 상한이 15%까지 열려 있어 편차가 구조적으로 음수가 되고, 같은 데이터에서 최대 편차가 +0.21%p에 그쳐 판정이 0건이었다. 초기안의 기간 정확 일치와 최소 상품 3개 기준도 함께 폐기했다. PERSONAL_CREDIT 19개 중 `term=36` 옵션 보유 상품이 1개뿐이고, MORTGAGE·AUTO 비교군은 전체 상품이 3~5개라 두 조건을 함께 걸면 대표 시연 대출이 항상 비교 불가가 된다.

중금리 대출을 일반 신용대출군과 비교하면 편차가 +1.15~+1.89%p로 커져 판정 건수는 늘지만 이는 이질 상품군 혼합이다. **비교군을 좁히면 판정이 줄고 넓히면 근거가 약해지는 트레이드오프**를 인지하고, 좁은 비교군을 유지한 채 기준금리 정의를 바꾸는 쪽을 택했다.

## 4. 공통 계약

- `AuthenticatedUserIdResolver`, `ApiResponse`, 기존 `ErrorCode`와 전역 예외 처리 흐름을 사용한다.
- 모든 보유 대출 조회에 userId 조건을 적용한다. 타인·미존재 계좌는 동일하게 404 `RESOURCE_NOT_FOUND`다.
- accountNo는 기존 숫자 10~20자리 정책과 `SensitiveNumberHasher`를 사용하고 응답은 `accountNoMasked`만 제공한다. 원문·해시·암호문을 로그·오류·DTO에 넣지 않는다.
- 기준일은 `LoanAnalysisClock`의 Asia/Seoul 날짜를 요청당 한 번 사용한다.
- 집계·우선순위 대상은 본인의 ACTIVE·OVERDUE 중 잔액이 양수인 대출이며 `LoanStatus.isOutstanding()`을 그대로 쓴다. 잔액 조건은 `balance != null && balance > 0`으로 판정한다. 532의 예측 대상 필터는 `balance == null || balance != 0`으로 null을 포함하지만, ERD에서 `balance`가 NOT NULL이라 두 표현의 결과가 같고 533은 조건을 명시적으로 적는 쪽을 택한다.
- 금리는 BigDecimal, 차이는 %p, 금액은 Long이다. 판정·정렬은 반올림 전 값으로 하고 표시만 소수 넷째 자리 HALF_UP한다.
- 입력 생략과 빈 문자열·공백을 구분한다. enum 오류, 범위 위반, 형식 오류는 기존 400 흐름으로 처리한다.
- 비교 데이터 부족은 HTTP 200과 상태·사유로 표현한다. DB 장애나 프로그래밍 오류를 비교 불가로 삼키지 않는다.
- 최상위 응답에 `asOfDate`와 `assumptions`를, 집계 응답에 `complete`·`unavailableReasons`·`unavailableLoans`를 둔다. 이 이름은 기존 `LoanRemainingAmountsResponse` 등과 동일하다.
- 항목 응답의 필드 구성은 기존 `LoanCostItemResponse`를 따른다. `institutionName`과 `status`·`reasons`를 빠뜨리지 않는다.
- 상품 자료의 `sourceType`을 후보와 함께 반환해 공시 스냅샷의 성격을 숨기지 않는다.

### 응답 어휘

**새 코드를 만들기 전에 `LoanAnalysisService`·`LoanForecastCalculator`의 기존 문자열을 확인해 같은 뜻이면 그 이름을 재사용한다.** 아래에 없는 코드를 임의로 추가하지 않고, 필요하면 이 표를 먼저 갱신한다.

항목 `status`는 532와 같은 세 값만 쓴다. 네 번째 값을 만들지 않는다.

| `status` | 뜻 |
| --- | --- |
| `COMPLETE` | 기준금리·편차까지 모두 산출 |
| `PARTIAL` | 후보는 확보했으나 기준금리·편차가 null |
| `UNAVAILABLE` | 비교·판정을 시작할 수 없음 |

| 코드 | 위치 | 의미 |
| --- | --- | --- |
| `NOT_OUTSTANDING` | `reasons` | 종료·잔액 0 대출이라 비교 대상이 아님. 계좌를 지정하는 `rate-comparison`에서만 사용하고 목록 API는 제외 대상을 나열하지 않는다 |
| `COMPARISON_GROUP_UNKNOWN` | `reasons` | 연결 상품이 없거나 세부 유형을 확정할 수 없음 |
| `UNSUPPORTED_LOAN_TYPE` | `reasons` | 5절 대응표에 비교군이 없는 `loan_type` |
| `INSUFFICIENT_COMPARABLE_PRODUCTS` | `reasons` | 조건 적합 상품이 2개 미만. 후보가 있으면 `PARTIAL`과 함께 쓴다 |
| `INDUSTRY_UNKNOWN` | `reasons` | 기관 코드가 `financial_company`에 없어 업권을 확정 못 함 |
| `MISSING_LOAN_PERIOD` | `reasons` | loanPeriod가 없거나 범위 밖. 기존 `MISSING_MATURITY_DATE` 명명을 따른다 |
| `INVALID_TERMS` | `reasons` | **기존 코드 재사용.** 금리·상환방식 무효에 이미 쓰인다. ERD CHECK 때문에 MySQL에서는 도달하지 않는 방어적 분기 |
| `INCOMPLETE_LOAN_DATA` | 집계 `unavailableReasons` | **기존 코드 재사용.** 일부 대출을 판정하지 못했을 때 |
| `AGGREGATE_AMOUNT_OUT_OF_RANGE` | 집계 `unavailableReasons` | 기존 532 계약과 동일 |
| `TERM_BAND_MATCH` | `unmatchedConditions` | 요청·보유 기간과 다른 term의 옵션을 같은 밴드로 채택 |
| `REPAYMENT_TYPE_AMBIGUOUS` | `unmatchedConditions` | `AMORTIZING` 옵션이라 상환방식을 특정할 수 없음 |
| `RATE_TYPE_NOT_MATCHED` | `unmatchedConditions` | 고정·변동 조건을 판정에 사용하지 않음 |
| `OTHER_INDUSTRY_REFERENCE` | `unmatchedConditions` | 다른 업권의 참고 비교. 지원 업권이 하나인 동안 도달하지 않음 |
| `DISCLOSED_MIN_RATE_MEDIAN` | `referenceMethod` | 5절 기준금리 정의 |
| `MARKET_SPREAD` | `thresholdMode` | 6절 유일한 판정 모드 |
| `HIGHEST_RATE_FIRST` | `strategy` | 7절 기본 우선순위 전략 |
| `HIGHER_CURRENT_RATE` | 순위 `reasons` | 현재 금리가 높아 앞 순위 |
| `NORMAL` / `LOW` | `referenceConfidence` | 유효 상품이 2개면 LOW, 그 이상은 NORMAL |
| `DISCLOSED_SNAPSHOT_NOT_REALTIME` | `assumptions` | 상품 자료가 저장 스냅샷이며 실시간·판매 가능 정보가 아님 |
| `REFERENCE_IS_NOT_APPROVAL_RATE` | `assumptions` | 기준금리가 공시 최저금리 수준이며 예상 승인금리가 아님 |
| ~~`INTRA_USER_RATE_GAP`~~ | — | **미채택.** 6절 근거 참조. 사용자 내부 금리 비교는 `repayment-priority`가 담당한다 |
| `EXCLUDES_FEES` | `assumptions` | **기존 코드 재사용.** 중도상환수수료 등을 반영하지 않음 |
| `EXCLUDES_OVERDUE_INTEREST_AND_ARREARS` | `assumptions` | **기존 코드 재사용.** 대상에 OVERDUE가 있을 때만 추가 |
| `EXCLUDES_GOAL_BUDGET` | `assumptions` | 목표·상환 예산을 반영하지 않음. 2단계에서 추가 |
| `ORDER_BY_CURRENT_RATE_ONLY` | `assumptions` | 현재 금리만으로 정렬하며 시장 비교·절감액 계산이 아님. 2단계에서 추가 |

`assumptions`도 기존 어휘를 재사용한다. `EXCLUDES_FEES`와 `EXCLUDES_OVERDUE_INTEREST_AND_ARREARS`는 533의 비교·우선순위에도 그대로 해당한다. 533 고유 가정으로 기준금리가 승인금리가 아니라는 점과 상품 자료가 저장 스냅샷이라는 점을 추가한다. 사유와 가정은 기존 정책대로 코드명 오름차순으로 정렬한다.

## 5. 시장금리 비교 기준

고금리 판정이 이 계산을 재사용하므로 기준을 먼저 정의한다. 구현 순서는 8절을 따르며 상환 우선순위 API가 앞선다.

### 입력

- `topN`: 기본 5, 범위 1~20. 후보 표시 개수만 제한하고 기준금리 모집단을 줄이지 않는다.
- `term`: 월 단위 1~600. **생략하면 보유 대출 loanPeriod가, 명시하면 그 값이 속한 기간 밴드를 사용한다.** 밴드는 `≤24`, `25~60`, `61~120`, `>120`개월이다. loanPeriod가 없거나 범위 밖이면 `MISSING_LOAN_PERIOD`로 비교 불가이며 잔여기간으로 대체하지 않는다.
- 밴드를 쓰는 근거는 3절의 옵션 금리 분포다. 옵션 금리가 상품 금리의 복사값이라 정확 일치는 기준금리를 바꾸지 않고 표본만 줄인다. 채택한 옵션의 실제 term을 후보별로 반환하고 요청 term과 다르면 `TERM_BAND_MATCH`를 넣는다.
- `industry`: 초기에는 `BANK`만 지원한다. 생략하면 보유 대출의 업권을 사용한다. 보유 업권을 모르면 요청 업권으로 간주하지 않고 `INDUSTRY_UNKNOWN`으로 둔다.
- 연결된 보유 상품은 모집단에서 제외한다. 한 상품의 여러 옵션을 별도 상품으로 중복 집계하지 않는다.

### 비교군

**비교군은 Loan 유형이 아니라 연결 상품의 `loan_type`으로 결정한다.** 표에 없는 값은 `UNSUPPORTED_LOAN_TYPE`이며 가까운 군에 임의 편입하지 않는다. 연결 상품이 없어 유형을 확정할 수 없으면 `COMPARISON_GROUP_UNKNOWN`이다.

| 비교군 | 상품 `loan_type` | 상품 수 |
| --- | --- | --- |
| CREDIT | `PERSONAL_CREDIT`, `CREDIT_REFINANCE` | 22 |
| CREDIT (중금리) | `POLICY_CREDIT`, `MID_CREDIT`, `MID_CREDIT_EMERGENCY`, `MID_CREDIT_REFINANCE`, `MID_CREDIT_POLICY`, `MID_EMERGENCY_REFINANCE` | 11 |
| MORTGAGE | `MORTGAGE`, `MORTGAGE_REFINANCE`, `POLICY_MORTGAGE` | 5 |
| JEONSE | `JEONSE`, `LEASE_HF`, `LEASE_YOUTH`, `LEASE_SGI`, `LEASE_REFI_HF`, `LEASE_REFI_YOUTH`, `LEASE_REFI_SGI` | 13 |
| AUTO | `AUTO_NEW`, `AUTO_LOAN`, `USED_CAR` | 3 |

- 중금리는 CREDIT과 별도 군이다. 판정 건수를 늘리려고 일반 신용대출군과 섞지 않는다.
- 자격 한정·단독 상품 15개는 어느 군에도 넣지 않는다. 상품 수 기준으로 `SOHO_*` 5(유형 4종), `MILITARY_*` 4(`MILITARY_CREDIT` 3 + `MILITARY_PENSION` 1), `PUBLIC_OFFICIAL`·`MEDICAL_PROFESSIONAL`·`VETERAN_POLICY`·`PENSION_CREDIT`·`VIP_CREDIT`·`LIVING_EXPENSE` 각 1이다.
- 미배정 6종 7개도 배정하지 않는다. `CREDIT_LINE`, `LINE_REFINANCE`, `EMERGENCY_CREDIT`(2)는 한도·마이너스 방식이라 분할상환 비교 대상이 아니고 534 범위와 겹친다. `SALARY_CREDIT`, `JOINT_CREDIT`, `POLICY_HOUSING`은 자격 조건이 달라 동질 군을 만들 표본이 없다.
- 합계 확인: 22 + 11 + 5 + 13 + 3 + 제외 15 + 미배정 7 = LOAN 상품 76개. 상품이 늘면 이 합계를 다시 맞춘다.
- **AUTO는 대응군을 정의해도 현재 데이터로는 비교가 성립하지 않는다.** 상품 3개에 밴드·상환방식을 걸면 후보가 1개 이하다(3절 분포). 대응군은 상품 보강에 대비해 유지하되 AUTO의 표본 미달을 결함으로 다루지 않는다.

기존 `ProductQueryService`는 이미 `PERSONAL_LOAN_TYPES`(23종), `MORTGAGE_LOAN_TYPES`(5종), `JEONSE_LOAN_TYPES`(7종)를 상수로 둔다. 533의 비교군은 그 분류를 대체하지 않고 조회 API도 바꾸지 않는다. 상수 이름이 겹치지 않도록 533 집합은 `COMPARISON_GROUP_*` 계열로 새로 정의한다.

| 항목 | 기존 조회 분류 | 533 비교군 | 차이의 이유 |
| --- | --- | --- | --- |
| JEONSE | 7종 | 동일 | 차이 없음 |
| MORTGAGE | `POLICY_HOUSING`·`SOHO_MORTGAGE` 포함 5종 | 3종 | 사업자 담보 제외. `POLICY_HOUSING`은 기존이 주택담보로 보지만 정책 자격이 달라 배정하지 않는다. **같은 상품을 두 API가 다르게 취급하므로 구현 문서에 명시한다** |
| CREDIT | 정책·중금리·한도·사업자까지 23종 | 일반 22 + 중금리 11로 분리 | 조회용 넓은 집합을 동질 금리 비교군으로 재사용하지 않는다 |

### 상환방식

문자열 동일성만 비교하면 `EQUAL_PAYMENT` 후보가 0건이 되므로 대응표를 쓴다.

| Loan `repayment_type` | 허용 옵션 값 |
| --- | --- |
| `EQUAL_PAYMENT` | `EQUAL_PRINCIPAL_INTEREST`, `AMORTIZING` |
| `EQUAL_PRINCIPAL` | `EQUAL_PRINCIPAL`, `AMORTIZING` |
| `BULLET` | `BULLET` |

- `AMORTIZING`은 원금균등·원리금균등을 구분할 수 없어 양쪽에 포함하되 `REPAYMENT_TYPE_AMBIGUOUS`를 표시한다. MORTGAGE 옵션 다수가 이 값이라 제외하면 비교군 확보가 무의미해진다.
- `CREDIT_LINE`, `OVERDRAFT`, NULL 옵션은 제외한다. 분할상환 대출의 비교 조건이 아니다.
- `rate_type`은 판정에 쓰지 않고 후보별로 반환하며 `RATE_TYPE_NOT_MATCHED`로 표시한다.
- 기간과 상환방식은 **같은 옵션 행에서** 함께 판정한다.

### 기준금리

현재 DB로는 실제 시장 평균금리를 만들 수 없으므로 다음을 별도 기준으로 둔다.

1. 조건에 맞고 `minRate`가 있는 옵션의 **공시 최저금리 `minRate`** 를 사용한다. `term`이 NULL인 옵션은 밴드를 확정할 수 없어 제외한다. 음수·역전은 ERD CHECK가 막지만 방어적으로 검증한다.
2. 한 상품의 적합 옵션이 여럿이면 그 minRate들의 중앙값을 상품 대표값으로 삼는다.
3. 상품 대표값들의 중앙값을 `referenceRate`로 쓴다. 짝수 중앙값은 가운데 두 값의 산술평균이다.
4. 기준명은 `DISCLOSED_MIN_RATE_MEDIAN`이며 **동일 조건에서 공시된 최저금리 수준**이라는 의미만 부여한다. 실제 평균 대출금리나 예상 승인금리라고 부르지 않고, 사용자가 같은 금리를 받는다는 뜻이 아님을 `assumptions`에 적는다.
5. 유효한 서로 다른 상품이 **2개 미만**이면 기준금리·편차는 null이고 사유는 `INSUFFICIENT_COMPARABLE_PRODUCTS`다. 확보한 후보는 유지할 수 있으며 이때 status는 `PARTIAL`이다.
6. 상품이 2개면 중앙값의 통계적 의미가 약하므로 `referenceConfidence=LOW`를 함께 반환한다.
7. 밴드에 맞는 옵션이 없을 때 상품 전체 min/max로 되돌리지 않는다. 옵션 `rate`는 3절 근거에 따라 사용하지 않는다.

후보 정렬은 상품 대표값 오름차순, productId 오름차순이다. `rateDifference = currentRate - referenceRate`이며 기준금리는 topN 적용 전 전체 적합 상품에서 계산한다.

### 응답

최상위에 보유 대출 식별·마스킹 정보와 `comparisonGroup`, `termBand`, `industry`, `referenceMethod`, `referenceRate`, `referenceConfidence`, `rateDifference`, `comparableProductCount`, `unmatchedConditions`, `candidates`를 둔다. 후보에는 상품 ID·이름·기관·대표값·공시 범위·채택 옵션의 실제 term과 상환방식·`syncedAt`·`sourceType`을 둔다. 전체에는 `oldestSyncedAt`과 `latestSyncedAt`을 두고 최신 일시 하나로 전체가 최신인 것처럼 표현하지 않는다.

**금리 자료의 허용 경과기간 임계값은 두지 않는다.** 스냅샷을 일시와 함께 제공하고 실시간·판매 가능 판정은 하지 않는 것으로 문서 요구를 충족한다. 근거는 다음과 같다.

- 명세 18의 상품 조회 API 제한사항은 모두 "DB 최신 동기화 시점 기준; 실시간 상품정보 아님"이고 명세 10의 rate-comparison도 같다. 어느 문서에도 경과기간으로 자료를 무효화하는 개념이 없다. 요구되는 것은 임계값이 아니라 고지다.
- 동기화는 명세 18-1의 `POST /api/v1/admin/products/sync`로 관리자가 트리거하며 주기가 문서에 정의되지 않았다. "오래됐다"를 판정할 기준선 자체가 없다.
- 신선도 관리는 명세 18-1의 `sync/status`·`sync/history`와 ERD `product_sync_history`가 소유한다. 533이 임계값을 만들면 그 책임이 넘어온다.
- 시연 상품의 `syncedAt`은 모두 `2026-09-03`이고 기준일이 `2026-09-09`이라 이미 6일 경과다. 짧은 임계값을 두면 시연에서 판정이 통째로 사라지고 사용자에게는 원인 불명의 빈 결과가 된다.

자료 신선도를 사용자에게 알리는 수단은 `syncedAt`, `oldestSyncedAt`, `latestSyncedAt`이다. 판정을 신선도로 차단하지 않는다.

## 6. 고금리 대출 판정

- `loanType`은 기존 LoanType이며 생략 시 전체 유형을 평가한다. `loanType=CREDIT` 요청은 일반과 중금리 대출을 **모두 포함**하고 각 대출은 자기 군의 기준금리로 판정된다. 두 군의 기준금리를 합치지 않는다.
- `industry`는 보유 대출 업권 필터이며 비교 API의 참고 비교와 구분한다. 업권 미확인 대출은 정상 제외 건과 구별해 `unavailableLoans`에 넣는다.
- `thresholdMode`는 `MARKET_SPREAD` 하나이며 생략 시 동일 값이다. 다른 모드는 구현하지 않고 잘못된 값은 400이다.
- 판정식은 `currentRate - referenceRate >= rateSpreadThreshold`다. 전체 대출 평균이나 사용자 보유 대출 평균으로 대체하지 않는다.
- 판정에는 동일 업권과 5절 비교군을 쓰고 기간 밴드·상환방식 대응도 5절과 같다.
- **임계값은 계획 단계에서 확정하지 않는다.** 기획안의 α는 `0.0%p -> 추후 결정`이며 0.0을 확정값으로 읽지 않는다. 8절 5단계에서 실제 분포를 근거로 정하고, 확정값이 통계적으로 검증된 기준이 아니라 서비스 초기 임계값임을 `assumptions`와 구현 문서에 적는다.
- 코드·응답에서는 이 값을 `rateSpreadThreshold`로 부른다. 기획안에는 고금리 기준편차(622행)와 진단 엔진의 군집 이탈 가중치(1365행)라는 서로 다른 두 α가 있어 이름을 구분한다.
- 서로 다른 군의 편차를 한 목록에서 정렬하므로 항목마다 `comparisonGroup`, `referenceRate`, `referenceMethod`, `comparableProductCount`를 함께 반환한다. 편차 숫자만으로 다른 군과 직접 비교되지 않게 한다.
- 응답 필드는 진단 계약과 정합하게 둔다. `diagnosis_issue`는 `current_value`·`benchmark_value`·`evidence`를 JSON으로 저장하고 `detection_source` 기본값이 `RULE`이다. 533의 현재 금리와 `referenceRate`, `referenceMethod`·`comparableProductCount`가 각각의 원천이 되도록 이름과 단위를 유지한다. 533이 진단 테이블에 직접 쓰지는 않는다.
- **`INTRA_USER_RATE_GAP` 보조 신호는 채택하지 않는다.** 기획안 614~618행의 탐지 수단 네 가지 중 "보유 대출을 금리순으로 비교"와 "동일 사용자의 대출 간 금리 차이 비교"는 이미 구현된 `repayment-priority`가 담당한다. 금리 내림차순 순위와 각 대출의 `interestRate`를 함께 반환하므로 사용자 내부 금리 차이는 그 응답만으로 얻는다. 이 API는 명세가 못박은 "상품군/업권 기준 적용"에 따라 시장 기준 비교만 한다. 같은 신호를 두 API에 중복으로 두면 2절의 소유 경계가 흐려진다.
- 시장 비교 불가가 많은 상황에서 화면이 비는 문제는 `repayment-priority`를 함께 호출해 해결한다. 두 API의 역할 분담이 곧 기획안 탐지 수단의 분담이다.
- `loans`에는 점검 대상만, `unavailableLoans`에는 판정 불가 대출과 사유를 반환한다. `evaluatedCount`, `highInterestCount`, `unavailableCount`, `complete`로 빈 결과의 의미를 설명한다.
- 정렬은 편차 내림차순, 현재 금리 내림차순, loanId 오름차순이다.
- `rateSpreadThreshold`·기준 방식·최소 표본 수를 응답과 구현 문서에 기록한다. 수수료·심사조건을 반영한 대환 권유나 대환 가능 여부는 반환하지 않는다.

## 7. 상환 우선순위와 goalId

### 기본 순서

- 현재 금리 내림차순 → 잔액 오름차순 → loanId 오름차순이다. loanId가 유일하므로 순서가 항상 하나로 정해진다.
- `rank`는 1부터 순차 부여하며 금리·잔액이 같아도 공유하지 않는다.
- 시장 비교가 불가해도 금리 기반 순위는 계산 가능하다. `interest_rate`는 NOT NULL이고 `>= 0`이 보장되므로 금리 무효는 MySQL에서 발생하지 않는다. 기존 `INVALID_TERMS`를 방어적 분기로 재사용하고 무이자(0%)는 정상 값으로 순위에 포함한다.
- 순위 근거는 `HIGHER_CURRENT_RATE`이며 동률 정렬 기준은 별도 정책으로 설명한다. 잔액 동률 해소를 이자 절감 최적화라고 설명하지 않는다.
- OVERDUE는 `requiresAttention`으로 표시한다. 미납액·연체 가산금리 자료가 없어 연체 해소의 최적 순서를 계산한 것으로 표현하지 않는다.
- 추가 상환예산 입력과 수수료 데이터가 없으므로 권장 상환금액·확정 절감액·완납일을 만들지 않는다. 532의 남은 전체 이자를 조기상환 절감액으로 재사용하지 않는다.
- 응답은 4절 공통 필드(`asOfDate`, `assumptions`, `complete`, `unavailableReasons`, `unavailableLoans`)에 `strategy=HIGHEST_RATE_FIRST`, `evaluatedCount`, `loans[{rank, loanId, accountName, accountNoMasked, institutionName, balance, interestRate, requiresAttention, status, reasons}]`를 더한다. `evaluatedCount`는 6절과 같은 뜻으로 순위를 매긴 대출 수다.
- `goalApplied`처럼 goalId 거부 때문에 값이 고정되는 필드는 두지 않는다. 목표 미반영은 `assumptions`의 `EXCLUDES_GOAL_BUDGET`으로 설명한다.
- 항목 `status`는 현재 구현에서 항상 `COMPLETE`다. 판정 불가 대출을 `unavailableLoans`로 분리하기 때문이다. 값이 고정이어도 유지한다. `status`·`reasons` 쌍은 기존 `LoanCostItemResponse`를 포함한 Loan 응답의 공통 구조이고, 형제 API인 `rate-comparison`에서 세 값이 모두 쓰이므로 항목 구조를 API마다 다르게 만들지 않는다. 이는 위 `goalApplied` 규칙의 예외이며 근거를 여기에 남긴다.
- 대상에서 제외한 종료·잔액 0 대출은 `loans`와 `unavailableLoans` 어디에도 넣지 않는다. `NOT_OUTSTANDING`은 계좌를 직접 지정하는 `rate-comparison`에서만 사용한다. 목록 API가 제외 대상을 나열하면 종료 대출을 판정 실패로 오해하게 된다.
- 대상 대출이 없으면 빈 배열과 `complete=true`다.

### goalId 미지원

`goalId?`는 명세에 있으므로 조용히 무시하지 않는다. 다만 지원 자체가 불가능하다.

1. `goal` 테이블 마이그레이션과 Goal 도메인 코드가 없어 목표 존재 여부를 조회할 방법이 없다. 533에 임시 테이블이나 대체 저장소를 만들지 않는다. Goal은 명세 12의 별도 담당 범위다.
2. 목표가 준비되어도 ERD가 목표-대출 연결을 제약조건으로 금지한다(3절).
3. 예산 항목은 소비목표 전용 `monthly_budget`뿐이라 소비 한도를 상환 여력으로 해석할 근거가 없다.
4. 목표를 반영한 우선순위는 명세 16의 `recommendations/priorities`가 소유한다. 같은 계산을 중복 구현하지 않는다.
5. 형식이 유효한 goalId도 `INVALID_REQUEST` 400으로 기능 미지원임을 명시한다. 형식 오류·0·음수도 같은 400이며 존재 여부를 조회한 것처럼 404를 반환하지 않는다.
6. 이 상태는 **goalId 지원 미완료**이며 533 전체 완료로 보고하지 않는다. 후속 이슈 분리 여부는 최신 Jira 범위와 대조한다.
7. 후속으로 Goal이 준비되면 `goalId + userId`로 읽고 타인·미존재는 동일 404로 처리한다. `goal.deleted_at`이 있어 소프트 삭제 취급도 그때 정한다.

## 8. 구현 순서

상환 우선순위 API를 가장 먼저 구현한다. 금리 기준 정렬만으로 동작해 상품 데이터 제약이 없으므로 정책 결정이 늦어져도 시연 가능한 결과를 먼저 확보한다.

| 순서 | 작업 | 산출물·통과 조건 |
| --- | --- | --- |
| 1 | 기준 확인 | 작업 브랜치 기준 확정, 531·532 병합 확인. **532는 부담률 항목이 미완이므로 533이 그 부분에 의존하지 않음을 확인해 근거를 남긴다**(현재 공유하는 것은 `LoanAnalysisClock`뿐) |
| 2 | 상환 우선순위 API | **완료.** 3절 기존 자산만으로 구현했고 Repository 추가는 없다. 통합 테스트 12건 통과 |
| 3 | 남은 정책 2건 결정 | **완료.** 자료 경과기간 임계값 미도입, `INTRA_USER_RATE_GAP` 미채택으로 확정했다(5·6절 근거) |
| 4 | 공통 비교 계산과 상품 fixture | **완료.** `LoanComparisonPolicy`·`LoanTermBand`·`LoanRateComparisonCalculator`·`LoanMarketProductReader`를 추가했고 전용 fixture 도입 후 기존 product·card 테스트가 통과함을 확인했다 |
| 5 | 편차 분포 산출과 임계값 결정 | **완료.** `rateSpreadThreshold = 1.0%p`로 확정했고 게이트(판정 1건 이상)를 충족한다 |
| 6 | 개별 시장금리 비교 API | **완료.** 소유권·입력·후보·기준금리·일시와 비교 불가 5종 사유를 검증했다. AUTO의 표본 미달은 정상 결과로 확인했다 |
| 7 | 고금리 API | **완료.** 비교 계산을 재사용하고 업권 필터·편차 경계·불가 항목·중금리 분리를 검증했다 |
| 8 | 통합·회귀·기록 | 전체 284건 통과·`clean build` 성공·구현 문서 작성까지 완료. **MySQL 조건부 테스트 추가는 남아 있다** |

`remaining-work.md`에 533을 등록하는 시점은 8단계가 아니다. goalId 미지원은 2단계에서 이미 확정된 잔여 항목이고 그 파일은 미완료 항목을 발견 시점에 기록하는 용도이므로, 3단계에 들어가기 전에 등록한다.

### 5단계 게이트

임계값 결정은 6·7단계를 막는 게이트다. 입력과 통과 조건을 고정해 조건부 환경에 의존하지 않게 한다.

- **입력에 대출 seed는 필요 없다.** `DODAM_LOAN_MINUS_ACCOUNT_SEED.sql`은 앱이 생성한 AES-256-GCM 암호문과 HMAC fixture 3종을 같은 세션에 요구해 단독 실행이 불가능하다. 상품 seed는 선행 조건이 없어 단독 적재가 가능하고 시연 대출 6종은 금리·기간·상환방식·연결 상품명이 대출 seed 안에 리터럴로 있다. **상품 데이터와 이 6종 프로필로 분포를 산출한다.**
- MySQL이 없어도 산출 가능하다. 있으면 같은 계산을 실제 조회로 재확인하고 차이를 기록한다.
- 게이트는 **판정 대상 1건 이상**과 그 근거 설명 가능이다. 0건이면 임계값을 낮춰 넘기지 않고 3단계로 돌아가 기준 방식을 재검토한다. 3절 분포에서는 이 조건이 충족된다.
- 이 분포는 정책 채택의 사후 확인이지 목표 판정 건수를 맞추기 위한 근거가 아니다. 건수를 늘리려고 비교군을 넓히지 않는다.

### 조회 설계

**요청당 LOAN 상품 전량을 회사·옵션과 함께 한 번 읽고 메모리에서 비교군별로 그룹핑한다.** 대출마다, 비교군마다 조회하지 않는다.

- 규모 근거: LOAN 상품 76개, 옵션 319건이다. 전량 로드가 단순하고 N+1을 원천 차단한다.
- 인덱스 근거: `financial_product_option`에는 `product_id` 인덱스만 있고 `term`·`repayment_type` 인덱스가 없다. `financial_product`의 금리 인덱스도 `(product_type, min_rate, max_rate)`라 옵션 조건을 DB에서 거르는 이점이 없다.
- `high-interest`가 대출 여러 건을 평가해도 상품 조회는 요청당 1회로 끝난다.
- 회사·옵션은 fetch join 또는 projection으로 함께 읽고 지연 로딩으로 건별 조회하지 않는다.
- 상품 수가 크게 늘면 비교군 조건을 DB로 내리는 것을 재검토한다. 그때의 판단 근거로 위 규모를 남긴다.

변경은 `backend/`에 한정한다. 기존 Loan 패키지에 Controller/Service/DTO와 비교 정책·계산기를 추가하고 전량 로드를 담당하는 `LoanMarketProductReader` 조회 경계를 둔다. 조회 Service는 readOnly 트랜잭션을 쓴다. 스키마는 변경하지 않으며 Goal이나 추가 신용정보가 필요하면 별도 선행 작업으로 다룬다. 상품 조회 API의 기존 분류·응답은 바꾸지 않는다.

### 커밋과 중단 지점

- 커밋은 API별로 나눈다. 2·4·6·7·8단계를 각각 하나 이상의 커밋으로 두고 전체를 한 커밋으로 묶지 않는다.
- 구현 문서 `backend/docs/loan-rate-priority.md`를 8단계에서 처음 만들지 않는다. 2단계에서 API 하나가 완성되므로 그 계약을 먼저 기록하고 이후 증분 갱신한다.
- 어느 중단 지점에서도 533 전체 완료로 보고하지 않는다.

| 중단 지점 | 완료된 것 | 남은 범위 |
| --- | --- | --- |
| 2단계 후 | 상환 우선순위 API 1종 | 비교·고금리 2종, goalId 미지원 |
| 5단계 후 | 계산기와 임계값 확정, API 1종 | 비교·고금리 2종 |
| 7단계 후 | API 3종 | 문서·회귀·`remaining-work.md` 등록 |

## 9. 검증 계획

| 범주 | 필수 사례 |
| --- | --- |
| 수치 검산 | 상품 대표 minRate 4/6/8이면 기준 6, 현재 8이면 편차 2%p; 짝수 중앙값; `rateSpreadThreshold` 직전·동일·직후 |
| 모집단 | 옵션 수가 달라도 상품별 동일 가중, topN 1/5에 기준금리 동일, 자기 상품 제외, 유효 상품 0/1/2/3과 2개일 때 `referenceConfidence=LOW` |
| 조건 격리 | 신용/담보/전세·일반/중금리/정책/사업자/한도 혼합 방지, 밴드 경계(24/25, 60/61, 120/121), 같은 옵션 행에서 기간·상환방식 판정 |
| 상환방식 대응 | `EQUAL_PAYMENT`가 `EQUAL_PRINCIPAL_INTEREST`·`AMORTIZING`에 매칭되고 문자열 동일성만으로 0건이던 회귀 방지, `REPAYMENT_TYPE_AMBIGUOUS` 표시, `CREDIT_LINE`·`OVERDRAFT`·NULL 제외 |
| 데이터 누락 | 연결 상품 없음, 기관 대응 없음, sector null, 대응표에 없는 `loan_type`, 옵션 term NULL, loanPeriod NULL·600 초과 |
| 방어적 분기 | **대출 금리 무효(`INVALID_TERMS`)는 테스트로도 도달할 수 없다.** `sql/loan-query-test-data.sql`이 ERD의 `interest_rate >= 0` 등 CHECK를 그대로 복제해 두어 H2에서도 음수를 넣을 수 없다(2단계에서 실제로 확인). 서비스의 분기는 방어 코드로만 남기고 통과 조건으로 삼지 않는다. `OTHER_INDUSTRY_REFERENCE`도 같다. 반면 상품 옵션의 금리 음수·역전은 `sql/product-query-test-data.sql`에 CHECK가 없어 검증 가능하지만, 이를 MySQL 제약 검증으로 간주하지 않는다 |
| 정상 미달 | AUTO의 표본 미달과 후보 0건이 결함이 아니라 `INSUFFICIENT_COMPARABLE_PRODUCTS`·`UNAVAILABLE`로 표현되는지 |
| 어휘 정합 | `status`가 세 값만 쓰이는지, 표본 미달인데 후보가 있으면 `PARTIAL`인지, 집계 사유가 `INCOMPLETE_LOAN_DATA`를 재사용하는지, `assumptions`에 `EXCLUDES_FEES`·`EXCLUDES_OVERDUE_INTEREST_AND_ARREARS`가 있는지 |
| 일시 | 서로 다른 `syncedAt` 유지, 응답의 가장 오래된·최신 일시, 경과기간 정책 경계 |
| 판정 | 데이터 부족을 정상 저금리로 분류하지 않음, 전체 평균 대체 금지, 업권 미확인 사유, `loanType=CREDIT`에서 일반·중금리가 각자 군 기준으로 판정되고 기준금리가 합쳐지지 않음 |
| 순위 | 금리·잔액 동률에도 rank가 순차이고 중복되지 않음, 입력 순서와 무관한 ID 정렬, 연체·종료·잔액 0·무이자 대출 |
| API | 3개 API 정상 응답·401, 타인·미존재 계좌 동일 404, 마스킹만 반환, `high-interest`·`repayment-priority`가 `{accountNo}`로 해석되지 않음(패턴이 숫자 전용이라 충돌하지 않으며 회귀 방지용) |
| 입력 | enum·빈 문자열·공백·숫자 변환 실패, 계좌 9/10/20/21자리, term·topN 최소·최대·초과, goalId 0·음수·범위 초과 |
| 목표 | 형식이 유효한 goalId도 400이고 404를 반환하지 않음 |
| 빈 결과 | 대출 없음, 고금리 대상 없음, 전부 비교 불가를 서로 구분 |
| 회귀 | 531·532 Loan API, 기존 상품 조회·비교 계약 유지 |

### 테스트 fixture

533은 **loan과 product 도메인을 한 H2 세션에서 함께 조회하는 첫 API**다. 기존 fixture는 도메인별로 자기 테이블을 `DROP` 후 `CREATE`하므로 그대로 재사용하면 실행 순서에 따라 스키마가 사라진다.

- `sql/loan-query-test-data.sql`은 `loan`·`loan_repayment`만 만들며, 주석에 테스트 실행 순서가 서로의 스키마를 깨뜨리지 않도록 도메인 간 외래키를 두지 않았다고 적혀 있다.
- `sql/product-query-test-data.sql`은 `financial_company`·`financial_product`·`financial_product_option`을 `DROP` 후 다시 만든다.
- 따라서 533은 네 테이블을 함께 만드는 **전용 fixture 하나**를 두고 다른 도메인 fixture에 의존하지 않는다. 기존 두 파일은 수정하지 않는다.
- 같은 방침으로 도메인 간 외래키를 두지 않고 `financial_product_id`는 값만 맞춘다.
- 상품 fixture에는 비교군·밴드·상환방식 조합이 결정적으로 드러나는 최소 집합을 만든다. 시연 seed를 실행하거나 참조하지 않는다.
- `product-query-test-data.sql`에는 CHECK가 없어 H2에서는 음수·역전 금리를 넣을 수 있다. 방어 코드는 이 경로로 검증하되 MySQL 제약 검증으로 간주하지 않는다.

### MySQL 검증

기존 `LoanAnalysisMySqlIntegrationTest`가 쓰는 방식을 그대로 따른다. `@EnabledIfEnvironmentVariable(named = "LOAN_MYSQL_TEST_URL", ...)`로 환경변수가 있을 때만 실행되고 Flyway 이력까지 확인하는 구조이며, 환경변수가 없으면 스킵된다(현재 전체 테스트의 스킵 1건이 이것이다).

- 533도 같은 방식의 조건부 테스트 클래스를 추가한다. 비교군 조회 SQL과 옵션 매핑, 상품·회사 조인을 실제 MySQL에서 확인하는 것이 목적이다.
- 환경변수가 없어 스킵되면 미검증으로 기록한다. "환경이 있으면"이라는 조건부 문장으로 남기지 않고 스킵 여부를 결과에 명시한다.

구현 후 관련 테스트부터 실행하고 `backend/`에서 `./gradlew.bat test`와 `./gradlew.bat clean build`를 수행한다. 이 클론에서는 `JAVA_HOME`이 설정되어 있지 않아 새 셸에서는 JDK 21 경로를 먼저 지정해야 두 명령이 동작한다. 운영·시연 seed를 실행하거나 수정하지 않는다.

## 10. 결정 현황과 완료 기준

| 항목 | 상태 | 값 |
| --- | --- | --- |
| 기준금리 정의 | 결정 | 조건 적합 옵션 minRate의 상품별 중앙값을 다시 중앙값 처리 (`DISCLOSED_MIN_RATE_MEDIAN`) |
| 기간 조건 | 결정 | `≤24 / 25~60 / 61~120 / >120`개월 밴드 |
| 최소 상품 수 | 결정 | 2개, 2개면 `referenceConfidence=LOW` |
| 비교군·상환방식 대응표 | 결정 | 5절 두 표 |
| industry | 결정 | 초기 `BANK` 단일 |
| 기관 코드 대응 | 결정 | `institution_code = company_code` 직접 조인 |
| goalId | 결정 | 미지원, 유효 형식도 400. 목표 반영 우선순위는 명세 16 소관 |
| thresholdMode | 결정 | `MARKET_SPREAD` 단일 |
| 상품 조회 방식 | 결정 | 요청당 LOAN 상품 전량(76개·옵션 319건) 1회 로드 후 메모리 그룹핑 |
| 응답 어휘 | 결정 | 4절 표. `status` 3값, `INVALID_TERMS`·`INCOMPLETE_LOAN_DATA`·`EXCLUDES_*`는 기존 코드 재사용 |
| 테스트 fixture | 결정 | loan·company·product·option을 함께 만드는 533 전용 fixture |
| 임계값 명칭 | 결정 | `rateSpreadThreshold` |
| 임계값 | **미결** | 8절 5단계에서 편차 분포를 근거로 확정 |
| 자료 허용 경과기간 | 결정 | 임계값 미도입. `syncedAt`·`oldestSyncedAt`·`latestSyncedAt` 노출로 충족하며 판정을 신선도로 차단하지 않는다(5절 근거) |
| `INTRA_USER_RATE_GAP` | 결정 | 미채택. 기획안의 사용자 내부 금리 비교는 `repayment-priority`가 담당한다(6절 근거) |

착수 전과 완료 전에 다음을 확인한다.

- [ ] 최신 Jira 완료 조건과 이 계획의 차이를 확인한다.
- [ ] 판정 기준이 공시 자료 기반 참고 지표이며 예상 승인금리가 아님을 DTO와 문서에 표시한다.
- [ ] 인증·소유권·입력 경계·민감정보·빈 결과·비교 불가 테스트가 통과한다.
- [ ] topN·옵션 개수·조회 순서가 기준금리를 왜곡하지 않는다.
- [ ] 시연 데이터로 3개 API가 실제 값을 반환한다. 전 건 비교 불가는 완료로 보지 않는다.
- [ ] 임계값을 편차 분포 근거와 함께 확정하고 초기 임계값임을 기록한다.
- [ ] 4절 응답 어휘와 실제 DTO·테스트의 코드 이름이 일치한다.
- [ ] 533 전용 fixture가 다른 도메인 fixture의 실행 순서에 영향받지 않는다.
- [ ] 2절 소유 관계에 따라 절감액·대환·목표 우선순위를 533에서 계산하지 않았다.
- [ ] 3절 정적 분석 값을 실제 MySQL 조회로 재확인하고 차이를 기록한다.
- [ ] 전체 테스트·빌드와 MySQL 검증 여부를 기록한다.
- [ ] 채택한 기본값·정렬·자료 출처·제한을 구현 문서에 기록한다.
- [ ] `remaining-work.md`에 goalId 미지원과 남은 제한을 530·532와 같은 형식으로 등록한다.
- [ ] 8절 중단 지점 표에 따라 완료·미완 범위를 구분해 보고한다.
- [ ] 최종 `git status`, 관련 diff, `git diff --check`를 확인한다.
