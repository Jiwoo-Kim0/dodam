# 카드 집계·혜택·결제 설정

JIRA `S15P21D105-529`, API 명세 8 Card의 집계·혜택·결제 설정 구현 기준이다.
보유 카드 기본 조회는 `S15P21D105-527`, 카드별 결제예정액·청구서·이용한도는
`S15P21D105-528` 범위다. 이미 구현된 카드 요약은 이 작업에서 중복 구현하지 않는다.

## API

모든 API는 인증이 필요하다. 카드번호와 계좌번호는 숫자로 된 평문 식별자를 입력받아
`SensitiveNumberHasher`로 HMAC 처리한 뒤 `*_hash`로 조회한다. 응답에는 마스킹 값만 포함한다.
타인 카드·계좌와 존재하지 않는 리소스는 `RESOURCE_NOT_FOUND`로 동일하게 처리한다.

| 기능 | Method | Endpoint |
| --- | --- | --- |
| 월 사용액 | GET | `/api/v1/cards/{cardNo}/monthly-summary?yearMonth=` |
| 전체 결제예정액 | GET | `/api/v1/cards/payment-schedules?startDate=&endDate=` |
| 카드 결제 부담률 | GET | `/api/v1/cards/payment-burden?yearMonth=` |
| 카드 혜택 | GET | `/api/v1/cards/{cardNo}/benefits?category=` |
| 혜택 활용도 | GET | `/api/v1/cards/{cardNo}/benefit-usage?yearMonth=` |
| 소비 카테고리 | GET | `/api/v1/cards/{cardNo}/categories?yearMonth=` |
| 저사용 카드 | GET | `/api/v1/cards/low-usage?lookbackMonths=&thresholdAmount=` |
| 결제 설정 변경 | PATCH | `/api/v1/cards/{cardNo}/payment-settings` |

## 소비 집계

월 사용액·소비 카테고리·혜택 활용도·저사용 카드에는 다음 거래만 포함한다.

- 인증 사용자와 대상 카드가 일치한다.
- `transaction_type = EXPENSE`, `hidden = false`다.
- 거래 또는 카테고리의 소비 유형이 `NON_SPENDING`이 아니다.
- 카테고리가 없으면 코드 `UNCATEGORIZED`, 이름 `미분류`로 집계한다.

`yearMonth`는 생략하면 Asia/Seoul 현재 월이며, 소비 집계에서는 미래 월을 허용하지 않는다.
카테고리는 금액 내림차순, 코드 오름차순이고 비율은 소수점 둘째 자리까지 반올림한다.
거래가 없으면 합계와 건수는 0, 목록은 빈 배열이다.

저사용 카드는 `ACTIVE` 카드만 대상으로 한다. `lookbackMonths` 기본값은 3이고 1~12,
`thresholdAmount` 기본값은 도담 정책인 100,000원이고 0 이상을 허용한다. 조회 종료일은
오늘이며 시작일은 `오늘 - N개월 + 1일`이다. 사용액이 기준 이하인 카드를 사용액 오름차순,
연회비 내림차순, 카드 ID 오름차순으로 반환한다. 거래가 0건인 카드도 포함한다.

## 결제예정액과 부담률

전체 결제예정액은 인증 사용자의 `SCHEDULED` 청구서 중 `billing_date`가 요청 범위에 있는
건을 반환한다. 정렬은 결제일, 카드명, 카드 ID 오름차순이다. 과거 거래용
`QueryPeriodPolicy`는 미래를 금지하므로 이 API에는 `PaymentSchedulePeriodPolicy`를 사용한다.

- 둘 다 생략: 오늘부터 3개월
- 시작일만 입력: 시작일부터 3개월
- 종료일만 입력: 종료일까지 이전 3개월
- 최대 포함 기간: 12개월
- 명시적인 과거 및 미래 날짜를 모두 허용한다.

결제 부담률은 선택한 `billingMonth`에 속하는 본인 카드 청구서의 상태와 관계없이
`total_balance`를 합산하고 다음 식으로 계산한다.

```text
burdenRate = totalBillingAmount / monthlyIncome * 100
```

비율은 소수점 둘째 자리까지 반올림한다. `financial_profile.monthly_income`이 없거나 0이면
금액은 반환하되 `burdenRate`는 null이다. 부담률의 `yearMonth`는 미래 청구월도 허용한다.

## 혜택과 활용도

ERD에는 `card_benefit`이 있지만 현재 적재 데이터가 없다. 보유 카드의
`financial_product_id`로 연결된 `financial_product.benefits` JSON을 혜택 정의로 사용한다.
연결 상품이나 혜택 JSON이 없으면 `benefitDataAvailable = false`와 빈 목록을 반환한다.

혜택 활용도는 카드사가 확정한 지급액이 아니라 소비 데이터에 기반한 추정값이다.
요청한 월의 소비로 혜택 매칭과 예상 혜택을 계산하고, 바로 전월의 소비 합계로 실적 충족
여부와 부족 금액을 계산한다. 전월 거래가 없으면 실적 금액은 0원이다. 신규 발급 카드의
실적 유예와 상품 JSON에 없는 실적 제외 조건은 반영하지 않는다.

`S15P21D105-526` 카드 추천과 같은 카테고리 매핑, 혜택별 한도, 카드 전체 한도 정책을
공유한다. 실적 기준과 카드 전체 한도는 상품의 현재 값이 아니라 보유 카드에 저장된
`baseline_performance`, `max_benefit_limit` 스냅샷을 사용한다. 혜택 데이터가 없으면
추정 금액과 비율은 0으로 단정하지 않고 null로 반환한다.

## 결제 설정 변경

요청 Body의 `withdrawalAccountNo`, `withdrawalDay`는 모두 선택값이며 null인 필드는 유지한다.
둘 다 없으면 `NO_FIELDS_TO_UPDATE`, 결제일이 1~31이 아니면 입력 검증 오류를 반환한다.
활성 카드만 변경할 수 있고 출금계좌는 본인의 `ACTIVE` 상태 `DEMAND_DEPOSIT`이어야 한다.
동일한 설정을 다시 요청하면 갱신 시각을 바꾸지 않고 현재 설정을 정상 반환한다.

API 명세는 데이터 출처를 SSAFY로 정의하지만 현재 `SsafyFinanceClient`에는 카드 결제 설정
변경 계약이 없다. 따라서 현재 구현은 사전 동기화된 로컬 DB를 갱신한다. 외부 변경 API가
확정되면 쓰기 경계에서 금융망 호출과 실패 보상 정책을 추가해야 한다.

## 현재 제한사항

- 활성 Flyway V1·V2에는 `account`, `card`, `transaction`, `card_billing_statement`,
  `financial_product`가 없다. H2 통합 테스트는 전용 스키마를 사용하며 MySQL 검증은 관련
  마이그레이션 병합 후 수행해야 한다.
- `S15P21D105-522` 카드 시드에는 청구서와 카드 혜택 데이터가 없고, 현재 저장소에는 거래
  시드가 없다. 해당 데이터가 적재되지 않은 시연 DB에서는 집계가 0 또는 빈 목록일 수 있다.
- 카드 혜택의 제외 가맹점, 건별 조건, 포인트 환산가치처럼 JSON에 없는 조건은 추정 금액에
  반영하지 않는다.
