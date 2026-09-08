# 카드 결제예정액·청구서·이용한도 조회

JIRA `S15P21D105-528`, API 명세 8 Card의 결제예정액·청구서·이용한도 조회 구현 기준이다.
보유 카드 목록·요약·상세·사용내역은 `S15P21D105-527`, 전체 결제예정액·결제 부담률·혜택·
저사용 카드·결제 설정 변경은 `S15P21D105-529` 범위다.

## API

모든 API는 인증이 필요하며 본인 카드만 조회할 수 있다. `{cardNo}`는 숫자 15~19자리의
평문 카드번호를 받고 `SensitiveNumberHasher`로 HMAC 처리한 뒤 `card_no_hash`로 조회한다.
응답에는 `card_no_masked`만 포함한다. 타인 카드와 존재하지 않는 카드는 모두
`RESOURCE_NOT_FOUND`로 처리한다.

| 기능 | Method | Endpoint | Parameter |
| --- | --- | --- | --- |
| 결제예정액 | GET | `/api/v1/cards/{cardNo}/payment-schedule` | Path: `cardNo` |
| 카드 청구서 | GET | `/api/v1/cards/{cardNo}/billing-statements` | Path: `cardNo` / Query: `startMonth?`, `endMonth?` |
| 카드 이용한도 | GET | `/api/v1/cards/{cardNo}/limit` | Path: `cardNo` |

## 결제예정액

로컬 `card_billing_statement`에서 `status = SCHEDULED`인 행을 `billing_date`,
`billing_month` 오름차순으로 정렬해 첫 번째 행을 반환한다. 고정 시연 데이터가 실행일에 따라
사라지지 않도록 현재 날짜를 추가 조건으로 사용하지 않는다.

```json
{
  "cardNoMasked": "1111-22**-****-4444",
  "schedule": {
    "billingMonth": "2026-09",
    "billingDate": "2026-09-25",
    "scheduledAmount": 350000,
    "status": "SCHEDULED"
  }
}
```

카드는 존재하지만 예정 건이 없으면 정상 응답에 `schedule: null`을 반환한다.

## 카드 청구서

청구서 응답은 `billingMonth`, `billingDate`, `totalBalance`, `status`, `paymentDate`를
포함한다. `status`는 ERD 제약과 같은 `SCHEDULED`, `PAID`, `OVERDUE`, `FAILED` 중 하나다.
목록은 `billingMonth` 내림차순으로 고정하며 결과가 없으면 빈 목록을 반환한다. 명세에 페이지
파라미터가 없으므로 페이지네이션하지 않는다.

월 조회 범위에는 `YearMonthPeriodPolicy`를 적용한다.

- 형식은 `YYYY-MM`이다.
- 둘 다 생략하면 Asia/Seoul 기준 현재 월을 종료월로 하고, 종료월을 포함한 최근 3개월을 조회한다.
- `endMonth`만 있으면 해당 종료월을 포함한 최근 3개월을 조회한다.
- `startMonth`만 있으면 시작월부터 현재 월까지 조회한다.
- 시작월과 종료월을 모두 포함해 최대 12개월까지 허용한다.
- 시작월은 종료월보다 늦을 수 없고 종료월은 현재 월 이후일 수 없다.
- 형식이나 범위가 올바르지 않으면 `INVALID_REQUEST`를 반환한다.

## 카드 이용한도

ERD `card.credit_limit`에 저장된 총 이용한도와 카드 마스킹 번호를 반환한다. 현재 사용액과 잔여
한도를 신뢰성 있게 계산할 원천 데이터가 없으므로 `usedAmount`, `availableLimit` 같은 파생값은
제공하지 않는다.

## 데이터 출처와 연동 경계

API 명세의 데이터 출처는 SSAFY 금융망이지만 현재 구현은 사전 생성된 시연 데이터를 로컬 DB에서
읽는다. 결제예정액과 청구서는 `CardBillingStatementReader` 경계 뒤에 두어 외부 연동 시 구현을
교체할 수 있게 했다. 이용한도는 동기화된 `card.credit_limit`를 사용한다.

현재 `S15P21D105-522` 시드 파일에는 Jira 설명과 달리 `card_billing_statement` 데이터가 없다.
따라서 시연 DB에서는 결제예정액이 `schedule: null`, 청구서가 빈 목록일 수 있다. 시연 데이터
보완은 522 후속 수정 또는 별도 Jira에서 처리해야 한다.

또한 현재 Flyway V1·V2에는 `card`, `card_billing_statement`가 포함되지 않는다. H2 통합 테스트는
전용 스키마로 검증하지만 local/prod의 MySQL 검증은 누락 테이블 마이그레이션이 병합된 후 수행해야
한다.
