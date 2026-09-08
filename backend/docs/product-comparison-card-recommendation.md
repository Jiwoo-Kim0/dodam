# 상품 비교 및 소비 기반 카드 추천

JIRA `S15P21D105-526`, API 명세 18의 상품 비교 3종과 카드 추천 API 구현 기준이다.

## API

모든 API는 인증이 필요하다.

| 기능 | Method | Endpoint |
| --- | --- | --- |
| 예·적금 비교 | POST | `/api/v1/products/deposit-savings/compare` |
| 대출 비교 | POST | `/api/v1/products/loans/compare` |
| 카드 비교 | POST | `/api/v1/products/cards/compare` |
| 소비 기반 카드 추천 | GET | `/api/v1/products/cards/recommended` |

비교 요청은 다음 형식이며 상품 ID는 중복 없이 2개 이상 5개 이하로 전달한다.

```json
{
  "productIds": [101, 201]
}
```

예·적금 비교는 예금과 적금을 함께 비교할 수 있고, 대출 비교는 대출 하위 유형을 함께 비교할 수 있다. 요청 순서는 응답의 `products` 순서에 유지된다. 존재하지 않는 상품은 `RESOURCE_NOT_FOUND`, 비교 API와 유형이 다른 상품은 `INVALID_REQUEST`를 반환한다.

추천 API의 `yearMonth`는 `YYYY-MM` 형식이며 생략하면 Asia/Seoul 기준 현재 월을 사용한다. `topN` 기본값은 3이고 1부터 10까지 허용한다.

## 추천 집계 기준

추천은 요청 사용자의 해당 월 거래 중 다음 조건을 만족하는 금액만 사용한다.

- `transaction_type = EXPENSE`
- `hidden = false`
- 거래 또는 카테고리의 소비 유형이 `NON_SPENDING`이 아님

거래 카테고리와 결제수단을 카드 혜택 카테고리로 정규화한다. 동일 지출에 `ALL`과 세부 혜택이 함께 적용되면 할인율이 가장 높은 혜택 하나만 계산한다.

- `limitAmount > 0`: 혜택별 한도 적용
- `maxBenefitLimit > 0`: 카드 전체 한도 적용
- `discountRate <= 0`: 금액을 추정하지 않고 정성적 일치 혜택으로 반환
- 월 소비가 `baselinePerformance`보다 적음: `estimatedMonthlyBenefit`은 0이고, 실적 충족 시 잠재 혜택은 `potentialMonthlyBenefit`으로 반환

카드 상품의 혜택 JSON만으로 계산할 수 없는 건별 조건, 제외 가맹점, 연회비, 마일리지 환산 가치는 추천 금액에 포함하지 않는다.

## 인증·거래 도메인 연결

추천 컨트롤러는 Spring Security의 인증 주체에서 사용자 ID를 읽는다. Auth 작업의 `AuthPrincipal.userId()`가 병합되면 같은 계약으로 동작한다.

소비 집계는 `CardRecommendationSpendingReader` 경계 뒤에 두었다. 현재 구현은 ERD의 `transaction`, `transaction_category`를 JDBC 읽기 전용 쿼리로 집계하며, 이후 Transaction·Spending 도메인의 공용 조회 구현으로 교체할 수 있다.
