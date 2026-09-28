# 보유 카드 기본 조회

JIRA `S15P21D105-527`, API 명세 8 Card의 기본 조회 4종 구현 기준이다.
결제예정액·청구서·한도·혜택·월 사용액·저사용 카드·결제 설정 변경은 `S15P21D105-528`, `S15P21D105-529` 범위이며 이 문서에 포함하지 않는다.

## API

모든 API는 인증이 필요하고 본인 리소스만 반환한다.

| 기능 | Method | Endpoint | Parameter |
| --- | --- | --- | --- |
| 보유 카드 조회 | GET | `/api/v1/cards` | `issuerCode?`, `status?` |
| 카드 요약 | GET | `/api/v1/cards/summary` | 없음 |
| 카드 상세 | GET | `/api/v1/cards/{cardNo}` | Path: `cardNo` |
| 카드 사용내역 | GET | `/api/v1/cards/{cardNo}/transactions` | Path: `cardNo` / Query: `startDate?`, `endDate?`, `page?`, `size?` |

명세서의 Parameter 정의를 그대로 따른다. 보유 카드 조회는 페이지네이션 없이 목록을 반환하고, 사용내역만 `PageResponse`로 반환한다. 계좌 API(`GET /api/v1/accounts`)와 할부 API(`GET /api/v1/installments`)의 구분과 같은 규칙이다.

`status`는 ERD `card.status` CHECK와 동일하게 `ACTIVE`, `SUSPENDED`, `EXPIRED`, `CANCELED`만 허용하며 대소문자를 구분하지 않는다. 허용되지 않는 값은 `INVALID_REQUEST`를 반환한다. `issuerCode`도 대소문자를 구분하지 않는다.

목록 정렬은 `status`, `cardName`, `cardId` 오름차순으로 고정한다.

## 카드번호 식별자

ERD 「물리 스키마 공통 정책 > 민감 식별자 저장」을 그대로 따른다.

- `{cardNo}`는 평문 카드번호를 받고 서버가 해시한 뒤 `card.card_no_hash`로 조회한다.
- 해시는 암호화 키와 분리된 키를 쓰는 HMAC-SHA-256 결과를 64자리 소문자 16진수로 만든다. 구현은 `SensitiveNumberHasher`이며 계좌번호(`{accountNo}`)도 같은 컴포넌트를 사용한다.
- 해시 키는 `app.security.number-hash-key`(환경변수 `NUMBER_HASH_KEY`)로 주입하며, 값이 없으면 애플리케이션이 기동하지 않는다.
- 응답에는 `card_no_masked`만 내보낸다. 조회 API는 원문 복호화가 필요 없으므로 `card_no_encrypted`는 엔티티에 매핑하지 않는다.
- `{cardNo}` 형식은 숫자 15~19자리만 허용한다.

`card_no_masked`에는 유니크 제약이 없으므로 마스킹 번호는 식별자로 쓰지 않는다.

### `cardId` (2026-09-16 추가)

목록·상세 항목에 내부 식별자 `cardId`(`card.card_id`)를 넣는다. 리볼빙 시뮬레이션(명세 17 `cardId`)과
`GET /api/v1/installments?cardId=`가 이 값을 받는데, 카드 목록이 마스킹 번호·이름·상태만 주면 FE가 값을 얻을 경로가 없다
(616 실측에서 `GET /installments` 항목의 `cardId`로 역추적해야 했다). 계좌 목록이 `accountId`를 주는 것과 같은 규칙이며,
민감번호가 아니라 노출해도 카드번호 정책과 충돌하지 않는다. 루트 명세 8 응답 정의에는 없는 필드라 명세 반영은 별도 요청이 필요하다.

## 조회 기간 정책

명세서 여러 API에 반복되는 "조회기간 정책 제한"을 `QueryPeriodPolicy` 한 곳에 정의하고 기간 조회를 지원하는 도메인이 공유한다.

- 기본 기간: 종료일 기준 최근 3개월. `endDate` 생략 시 Asia/Seoul 기준 오늘.
- 최대 기간: 12개월.
- `startDate ≤ endDate`, 종료일은 오늘 이후 불가.
- 위반 시 `INVALID_REQUEST`.

기본 3개월은 기획안의 소비·패턴 분석 창(3~6개월)에 맞춘 값이고, 상한 12개월은 사전 생성된 시연 데이터(2026-01 ~ 2026-09)를 한 번에 조회할 수 있도록 잡은 값이다.

## 사용내역 집계 기준

사용내역은 요청 사용자의 거래 중 다음 조건을 만족하는 행만 반환한다.

- `transaction.card_id`가 조회 대상 카드
- `transaction.user_id`가 인증 사용자
- `hidden = false`
- `transaction_date`가 조회 기간 안

정렬은 `transaction_date`, `transaction_time`, `transaction_id` 내림차순이다.

## 외부 연동 경계

명세상 보유 카드 조회·상세·사용내역의 데이터 출처는 SSAFY 금융망이지만, 현재는 사전 생성된 시연 데이터를 로컬 DB에서 읽는다. 사용내역은 `CardTransactionReader` 경계 뒤에 두어 외부 연동이 붙으면 구현만 교체할 수 있게 했다. 카드 기본 정보는 `card` 테이블을 JPA로 조회한다.

출금계좌 표시 정보는 Account 도메인 구현 전까지 `CardWithdrawalAccount` 읽기 전용 매핑으로 `account`의 표시용 컬럼만 노출한다. 계좌번호 원문·암호문은 매핑하지 않는다.

## 인증

컨트롤러는 Spring Security 인증 주체에서 사용자 ID를 읽는다. 카드·상품 도메인이 함께 쓰게 되면서 `AuthenticatedUserIdResolver`를 `global.security`로 옮겼고, Auth 작업의 `AuthPrincipal.userId()`가 병합되면 같은 계약으로 동작한다.

타인 카드는 존재 여부까지 감추기 위해 `FORBIDDEN`이 아니라 `RESOURCE_NOT_FOUND`로 응답한다.

## 남은 의존성

- 카드 사용내역이 읽는 `transaction` 시드는 `S15P21D105-492` 범위다. 해당 시드 적재 전에는 사용내역이 비어 있을 수 있으며, 이 경우에도 빈 페이지를 정상 반환한다.
- `S15P21D105-522` 카드 시드는 애플리케이션이 만든 암호문·해시·마스킹 픽스처를 요구한다. 해시는 이 문서의 `SensitiveNumberHasher`와 같은 키로 생성해야 하고, AES-256-GCM 암호화 코덱과 마스커는 쓰기 API를 다루는 후속 작업에서 추가한다.
