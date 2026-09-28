# User 프로필 확장 — 생년월일·성별·신용정보 (S15P21D105-629·680, V39·V42)

API 명세 2. User(`/api/v1/users/me`)와 1. Auth 회원가입에 생년월일·성별을 더하고, 금융망 A~E 등급과 도담 예상
신용점수를 구분해 제공한다. 나이는 생년월일로 저장해 응답에서 계산하며 성별은 사용자 입력이다. 금융망 등급과 신용점수는
사용자가 수정할 수 없고, 산정·조회 전에는 NULL이다.

## 스키마 (V39)

| 컬럼 | 타입 | 출처 | 비고 |
| --- | --- | --- | --- |
| `user.birth_date` | `DATE NULL` | 사용자 입력 | 나이는 저장하지 않고 조회 시 계산 |
| `user.gender` | `VARCHAR(10) NULL`, `CHECK IN ('MALE','FEMALE')` | 사용자 입력 | enum `Gender`. 미입력은 NULL |
| `user.credit_rating` | `VARCHAR(5) NULL` | 금융망 2.7.4 내 신용등급 조회 `ratingName` | `A`~`E` 원문. 금융망은 숫자 점수를 주지 않는다 |
| `user.credit_rating_updated_at` | `DATETIME NULL` | 서버 | 마지막 기록 시각 |
| `user.credit_score` | `SMALLINT NULL`, 1~1,000 | 합성 모델 또는 CB 조회 | 현재 개인신용점수 |
| `user.credit_score_source` | `VARCHAR(30) NULL` | 서버 | `SYNTHETIC`, `KCB`, `NICE` |
| `user.credit_score_model_version` | `VARCHAR(30) NULL` | 서버 | 합성 점수일 때 필수. 현재 `DODAM_CREDIT_V1` |
| `user.credit_score_updated_at` | `DATETIME NULL` | 서버 | 마지막 산정·조회 시각 |

## API 변경

### `POST /api/v1/auth/signup` Body

```json
{ "email": "dodam@example.com", "password": "password123", "name": "도담",
  "birthDate": "1996-03-15", "gender": "FEMALE" }
```

- `birthDate?`: `yyyy-MM-dd`, 오늘 이전(`@Past`). 위반 시 400 `VALIDATION_ERROR`(`errors[].field = birthDate`).
- `gender?`: `MALE` | `FEMALE`. 그 밖의 문자열은 본문 해석 실패라 400 `INVALID_REQUEST`.
- 응답 `data.user`(AuthUserResponse)는 최소 정보 그대로다. 새 필드는 `GET /users/me`에서 읽는다.

### `PATCH /api/v1/users/me` Body

```json
{ "name": "새이름", "birthDate": "1996-03-15", "gender": "MALE" }
```

- 세 필드 모두 선택이며 보낸 것만 바꾼다. 셋 다 없으면 400 `NO_FIELDS_TO_UPDATE`.
- `creditRating`은 받지 않는다(알 수 없는 필드로 무시). 금융망 값이라 사용자가 고칠 수 없다.

### `GET /api/v1/users/me` 응답

```json
{ "success": true, "data": {
    "userId": 1, "email": "dodam@example.com", "name": "도담",
    "birthDate": "1996-03-15", "age": 30, "gender": "FEMALE",
    "creditRating": "B", "creditRatingUpdatedAt": "2026-09-17T14:31:02",
    "creditScore": 768, "creditScoreType": "ESTIMATED",
    "creditScoreSource": "SYNTHETIC", "creditScoreModelVersion": "DODAM_CREDIT_V1",
    "creditScoreUpdatedAt": "2026-09-27T23:59:59",
    "role": "USER", "financeConnectionStatus": "CONNECTED",
    "createdAt": "2026-09-17T14:20:00", "updatedAt": "2026-09-17T14:31:02" } }
```

- `age`: 서비스 시간대(Asia/Seoul) 오늘 기준 만 나이. `birthDate`가 없으면 `null`.
- `creditRating`·`creditRatingUpdatedAt`: 신용등급 조회 또는 금융 데이터 동기화가 한 번도 성공하지 않았으면 `null`.
- `creditScore*`: 점수 산정·조회 전에는 모두 `null`. `SYNTHETIC`은 `ESTIMATED`, KCB/NICE는 `VERIFIED`다.
- SSAFY 금융망 등급은 점수로 환산하지 않는다. 4,000명 합성 점수 산식은 [credit-score-model.md](credit-score-model.md)를 따른다.

## 신용등급 갱신 규칙

| 경로 | 동작 |
| --- | --- |
| `GET /finance-data/credit-rating` 성공 | `ratingName`과 조회 시각을 기록. 응답 자체는 종전과 같은 실시간 값 |
| `POST /finance-data/sync` | scope 핸들러가 끝난 뒤 `CreditRatingService.refresh`로 한 번 조회해 기록. scope 결과·progress·status에 포함되지 않고, 조회 실패는 로그만 남긴다 |
| 회원가입 | 조회하지 않는다. 새 회원은 자산이 없어 항상 최저 등급이라 첫 동기화·조회에서 채운다 |

빈 등급명(`ratingName` null/blank)은 이전 값을 덮어쓰지 않는다. 금융망 호출은 트랜잭션 밖, 기록은 짧은 별도 트랜잭션이다.

## 검증 (2026-09-17)

| 테스트 | 건수 | 확인한 것 |
| --- | --- | --- |
| `UserApiIntegrationTest` (H2) | +2 (12) | 미입력 회원의 새 필드 null, PATCH로 생년월일·성별 설정과 `age` 계산, 성별만 재수정 시 생년월일 유지, 미래 생년월일 `VALIDATION_ERROR`, 미정의 성별 `INVALID_REQUEST`, `creditRating`만 보내면 `NO_FIELDS_TO_UPDATE` |
| `AuthApiIntegrationTest` (H2) | +2 (21) | 회원가입 Body의 생년월일·성별 저장, 가입 시 신용등급 미조회, 미래 생년월일·`OTHER` 거부 시 회원 미생성 |
| `FinanceDataApiIntegrationTest` (H2) | +2 (38) | 신용등급 조회 성공 시 `user.credit_rating` 기록과 `GET /users/me` 노출, 동기화 후 등급 갱신(scope 결과 1건 유지), 등급 조회 실패에도 동기화 COMPLETED·등급 null |
| `SchemaTypeConsistencyTest` | 1 | V39 DDL 타입(DATE·VARCHAR·DATETIME)과 엔티티 매핑 계열 일치 |
| `UserDemographicsMySqlIntegrationTest` (MySQL 8.0 게이트) | 2 | 로컬 Docker `mysql:8.0` `dodam_516_test`에 V1~V39 적용 후 `ddl-auto: validate` 기동, DATE·enum 문자열 왕복, `chk_user_gender` CHECK 거부(3819)와 NULL 통과, `refresh` 기록·빈 등급명 무시 |
| 전체 H2 스위트 | 1,192 (skip 22 = MySQL 게이트) | 실패 0 |

MySQL 게이트는 `PATTERN_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_516_test`, `PATTERN_MYSQL_TEST_PASSWORD`로 켠다. 새 DB에 손으로 적용할 때는
V1~V5 → V10~V11 → V6~V9 → V12~V19 → V30~V39 순서다(V6이 V11 `card`를 참조).

## 남은 것

[remaining-work.md](remaining-work.md) §S15P21D105-629 — FE 입력·타입 반영, Notion 명세 갱신, 등급 갱신 시점(FE가 sync·credit-rating을 아직 호출하지 않음),
"신용점수" 표기 확정, 시연 계정 4명의 생년월일·성별 채우기.
