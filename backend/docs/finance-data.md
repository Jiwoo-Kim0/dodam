# Finance Data API

JIRA `S15P21D105-452` (D3 Finance Data API 구현)의 하위 티켓 `S15P21D105-498`(연결·회원 관리), `S15P21D105-499`(동기화), `S15P21D105-500`(통합 요약·신용등급) 구현 기준이다.
API 명세 4 Finance Data의 10개 엔드포인트를 모두 다룬다. 계좌·카드·대출 등 개별 도메인 데이터 조회는 각 도메인 명세(5, 8, 10 등)의 범위이며 이 문서에 포함하지 않는다.

## API

모든 API는 인증이 필요하고 본인 리소스만 다룬다. 응답에 금융망 `userKey`와 이메일 원문은 어떤 경우에도 싣지 않는다.

| 티켓 | 기능 | Method | Endpoint | 상태 코드 |
| --- | --- | --- | --- | --- |
| 498 | 금융망 연동 상태 조회 | GET | `/api/v1/finance-data/connection` | 200 |
| 498 | 금융망 사용자 등록(연결·재연결) | POST | `/api/v1/finance-data/connection` | 201 |
| 498 | 금융망 연동 해제 | DELETE | `/api/v1/finance-data/connection` | 200 |
| 498 | 금융망 사용자 조회 | GET | `/api/v1/finance-data/member` | 200 |
| 499 | 금융 데이터 갱신 | POST | `/api/v1/finance-data/sync` | 201 |
| 499 | 갱신 상태 조회 | GET | `/api/v1/finance-data/sync/status` | 200 |
| 499 | 마지막 갱신 시간 조회 | GET | `/api/v1/finance-data/sync/latest` | 200 |
| 500 | 금융 데이터 통합 요약 | GET | `/api/v1/finance-data/summary` | 200 |
| 500 | 신용 등급 조회 | GET | `/api/v1/finance-data/credit-rating` | 200 |
| 500 | 신용 등급 기준 조회 | GET | `/api/v1/finance-data/credit-rating/criteria` | 200 |

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `FINANCE_CONNECTION_NOT_FOUND` | 404 | 연결 행이 없는 사용자가 해제를 요청 |
| `FINANCE_ALREADY_CONNECTED` | 409 | 이미 CONNECTED인데 등록을 요청 (금융망을 다시 호출하지 않음) |
| `FINANCE_NOT_CONNECTED` | 409 | CONNECTED가 아니거나 userKey가 없는 상태에서 회원 조회·동기화·내 신용등급 조회, 또는 이미 DISCONNECTED인 연결 해제. 신용등급 기준표는 앱 단위 API라 해당 없음 |
| `FINANCE_EMAIL_ALREADY_REGISTERED` | 409 | 금융망에 다른 앱이 먼저 등록한 이메일 (E4002 후 조회 E4003) |
| `FINANCE_SYNC_IN_PROGRESS` | 409 | 진행 중인 동기화가 있는데 동기화 또는 연결 해제를 요청 |
| `FINANCE_API_ERROR` | 502 | 금융망이 오류 응답(responseCode)을 돌려줌. 코드·메시지는 서버 로그에만 남김 |
| `FINANCE_API_UNAVAILABLE` | 503 | 금융망 타임아웃·연결 실패·응답 해석 불가 |
| `INVALID_REQUEST` | 400 | 동기화 scope가 정의되지 않은 값 |

## 연결 상태 전이 (498)

`finance_connection`은 사용자당 1행이다. 회원가입이 첫 행을 만들고(CONNECTED 또는 FAILED), Finance Data API가 이후 전이를 담당한다.

```
(없음) ──POST──▶ CONNECTED ◀──POST── FAILED / DISCONNECTED
                   │
                 DELETE
                   ▼
              DISCONNECTED
```

- `GET /connection`: 행이 없으면 `status = PENDING`으로 응답한다. `UserResponse.financeConnectionStatus`와 같은 규칙이다.
- `POST /connection`: 회원가입과 같은 절차로 userKey를 확보한다(생성 → E4002면 조회 → E4003이면 409). 단, 회원가입은 금융망 장애를 삼키고 FAILED로 진행하지만 이 API는 사용자가 직접 요청한 것이므로 502/503으로 실패를 알린다. 실패해도 기존 행의 상태는 바꾸지 않는다.
  - 금융망 호출은 트랜잭션 밖에서 먼저 하고, 사용자 행을 `SELECT ... FOR UPDATE`로 잠근 뒤 상태를 다시 확인해 CONNECTED로 바꾼다. 행이 없으면 새로 만들고, 동시에 두 요청이 생성까지 왔다면 `uk_finance_connection_user_id`로 걸러 409를 돌려준다.
  - 재연결은 `connected_at`을 새로 찍고 `disconnected_at`은 마지막 해제 이력으로 남긴다. userKey 암호문·해시는 새 값으로 덮어쓴다.
- `DELETE /connection`: 우리 쪽 연결만 DISCONNECTED로 바꾼다. 금융망에는 사용자 계정 삭제 API가 없어 계정은 그대로 남고, 재연결 시 E4002 → 조회로 같은 userKey를 회수한다. 진행 중인 동기화가 있으면 409다.
- `GET /member`: 저장된 이메일 암호문을 복호화해 2.2.2 사용자 계정 조회를 호출한다. 응답은 `userId`(이메일), `userName`, `institutionCode`, `connectedAt`이다.

회원 탈퇴(`DELETE /users/me`)는 기존대로 연결을 DISCONNECTED로 바꾼다.

## 동기화 (499)

`finance_sync`(V4 마이그레이션, ERD 정의 그대로)에 실행 1건당 1행을 남긴다.

### 실행 흐름

요청 스레드에서 동기적으로 실행하고 결과를 바로 응답한다. 비동기 큐는 두지 않았다(ERD `PENDING` 상태는 비동기 전환 시 예약).

1. 트랜잭션 1: 연결 행을 잠근 채 CONNECTED·userKey 존재를 확인하고, 진행 중(PENDING/RUNNING) 행이 있으면 409. RUNNING 행을 삽입한다.
2. 트랜잭션 밖: scope별 `FinanceSyncScopeHandler`를 ACCOUNT → CARD → LOAN → SAVING 순으로 실행한다. 등록된 구현이 없는 scope는 SKIPPED, 예외는 그 scope만 FAILED로 기록하고 계속 진행한다.
3. 트랜잭션 2: FAILED가 하나라도 있으면 전체 FAILED(`error_message`에 실패 scope와 메시지를 모음), 아니면 COMPLETED. `progress`는 (전체 − 실패) / 전체 × 100. **실제로 COMPLETED된 scope가 하나 이상일 때만** `finance_connection.last_synced_at`을 갱신한다 — 전부 SKIPPED인 동기화는 데이터를 바꾸지 않았기 때문이다.

### 동시 실행 방지

MySQL은 부분 유니크 인덱스가 없어 "사용자당 RUNNING 1행"을 DB 제약으로 걸 수 없다. 대신 시작 시 `finance_connection` 행을 `FOR UPDATE`로 잠가 같은 사용자의 동기화 시작과 연결 해제를 직렬화한다.

프로세스가 2단계에서 죽으면 RUNNING 행이 남는다. 시작 후 10분(`FinanceSyncService.STALE_AFTER`)이 지난 RUNNING 행은 다음 동기화 요청이 `FAILED`("만료 처리")로 정리하고 새 동기화를 시작한다. `GET /sync/status`의 `inProgress`도 만료된 행은 진행 중으로 보지 않는다.

### 요청·응답

- 요청 Body `{ "scope": "ALL" | "ACCOUNT" | "CARD" | "LOAN" | "SAVING" }`. Body 생략 또는 `{}`면 ALL, 대소문자 무관.
- 실행 응답: `{ syncId, scope, status, progress, errorMessage, startedAt, completedAt, results: [{ scope, status: COMPLETED|SKIPPED|FAILED, syncedCount, message }] }`. scope별 `results`는 저장 컬럼이 없어 실행 응답에만 있고 이력 조회에서는 `null`이다.
- `GET /sync/status`: `{ inProgress, sync }` — 가장 최근 동기화(상태 무관). 이력이 없으면 `sync = null`, 404가 아니다.
- `GET /sync/latest`: `{ lastSyncedAt, lastCompletedSync }` — `lastSyncedAt`은 `finance_connection.last_synced_at`, `lastCompletedSync`는 마지막 COMPLETED 행.

### 도메인별 동기화 구현 (확장점)

`FinanceSyncScopeHandler`를 구현한 빈을 등록하면 해당 scope가 실행된다. 각 도메인 테이블은 그 도메인이 소유하므로 구현도 도메인 패키지(예: `domain/account`)에 둔다. 규칙:

- 트랜잭션 밖에서 호출되므로 금융망 응답을 기다리며 DB 커넥션을 잡지 않도록 저장 구간만 스스로 트랜잭션을 연다.
- `userKey` 원문을 로그·응답·DB에 남기지 않는다.
- 같은 scope 구현이 둘이면 기동에 실패한다. ALL을 대상으로 하는 구현은 등록할 수 없다.

**2026-09-09 기준 등록된 구현은 없다.** 현재 팀 규칙상 계좌·카드·대출 데이터는 사전 생성된 시연 데이터를 로컬 DB에서 읽으므로(카드·대출 구현 문서의 「외부 연동 경계」), 동기화는 모든 scope를 SKIPPED로 보고하고 `last_synced_at`을 갱신하지 않는다. ACCOUNT 구현은 Account 도메인(`S15P21D105-501`)에서 `Account` 엔티티와 함께 추가할 예정이다.

## 통합 요약 (500)

금융망을 호출하지 않고 로컬 DB만 집계한다. 미연결 사용자도 응답하며 `connection.status`로 상태를 알 수 있다.

계좌·카드·대출 테이블은 다른 도메인 소유이므로 엔티티에 의존하지 않고 `JdbcFinanceSummaryReader`가 ERD 컬럼명 기준 SQL로 읽는다(읽기 전용).

| 항목 | 집계 기준 |
| --- | --- |
| `accounts.count` | `account.status <> 'CLOSED'` 전체 (마이너스통장 포함) |
| `accounts.demandDepositBalance / depositBalance / savingsBalance` | 위 계좌 중 `account_type`별 `balance` 합 |
| `accounts.minusAccountCount / minusUsedAmount` | `account_type = 'MINUS'` 건수와 `|balance|` 합. 마이너스통장 `balance`는 −사용액으로 저장된다(ERD 무결성 규칙) |
| `cards.activeCount / totalCount / totalCreditLimit` | `card` 전체 건수, ACTIVE 건수, ACTIVE 카드 `credit_limit` 합 |
| `loans.outstandingCount / overdueCount / totalBalance` | `loan.status IN ('ACTIVE','OVERDUE')` 건수·OVERDUE 건수·`balance` 합 |
| `totals.cashAssets` | 입출금 + 예금 + 적금 잔액 |
| `totals.liabilities` | 대출 잔액 + 마이너스통장 사용액 |
| `totals.netAssets` | cashAssets − liabilities |

예·적금은 `account`의 DEPOSIT·SAVINGS 행으로 집계한다(`deposit_saving` 상세 테이블은 아직 없음). 할부·구독은 요약에 포함하지 않았다.

## 신용등급 (500)

SSAFY 금융망 API 문서 2.7절 대출 API를 실시간 호출하며 결과를 저장하지 않는다. 필드명은 문서(2026-09-09 확인본)와 대조해 확정했다.

| 도담 API | 금융망 API | Header | 응답 |
| --- | --- | --- | --- |
| `GET /credit-rating` | 2.7.4 내 신용등급 조회 `POST /ssafy/api/v1/edu/loan/inquireMyCreditRating` | userKey 필요 (CONNECTED 아니면 409) | `{ ratingName, demandDepositAssetValue, depositSavingsAssetValue, totalAssetValue, inquiredAt }` |
| `GET /credit-rating/criteria` | 2.7.1 신용등급 기준 조회 `POST /ssafy/api/v1/edu/loan/inquireAssetBasedCreditRatingList` | **userKey 제외** (앱 단위, 연결 상태 무관) | `{ criteria: [{ ratingUniqueNo, ratingName, minAssetValue, maxAssetValue }], inquiredAt }` |

- 등급은 A~E이며 금융망 내 수시입출금 + 예·적금 자산 합산으로 산정된다. 최고 등급의 `maxAssetValue`를 넘는 자산도 최고 등급이다. 금융망은 신용점수(creditScore)를 제공하지 않는다.
- 자산 값은 금융망이 계산한 금융망 내 자산이며 로컬 통합 요약과 다를 수 있다. 금융망은 Long 항목을 문자열(`"1089427976"`)로 내려주므로 Jackson 강제 변환으로 받는다.
- `ratingUniqueNo`는 대출 상품 생성 API(2.7.2)의 입력이라 그대로 전달한다.
- 기준표 조회는 로그인만 필요하고 금융망 연결이 FAILED여도 응답한다.

## SSAFY 금융망 거래 API 공통 Header

`infrastructure/external/ssafy/SsafyHeaderFactory`가 API 문서의 공통 Header를 만든다.

| 필드 | 값 |
| --- | --- |
| `apiName`, `apiServiceCode` | API명 (URL 마지막 경로와 동일) |
| `transmissionDate`, `transmissionTime` | Asia/Seoul 기준 `yyyyMMdd`, `HHmmss` |
| `institutionCode` | `00100` |
| `fintechAppNo` | `001` |
| `institutionTransactionUniqueNo` | 전송일자(8) + 전송시각(6) + 난수 일련번호(6) = 20자리. **호출마다 새로 생성** |
| `apiKey` | `SSAFY_FINANCE_API_KEY` |
| `userKey` | 사용자 단위 API만 |

일련번호는 카운터가 아닌 난수다. 서버 인스턴스가 여러 대여도 같은 초에 중복될 확률이 무시할 수준이고, 금융망이 중복을 거절하면 502로 드러난다.

앱 단위 API(문서에 "Header 공통 — userKey 제외")는 `userKey`를 null로 넘기며, `SsafyRequestHeader`가 `@JsonInclude(NON_NULL)`이라 JSON에서 키 자체가 빠진다.

응답은 `{"Header": {...}, "REC": ...}`이며 `RestSsafyFinanceClient`는 REC만 꺼내 타입으로 변환한다. 정상 코드는 `H0000` 하나다. Header 검증 오류 `H1000`~`H1010`(예: `H1007` 기관거래고유번호 중복, `H1008` API_KEY 무효, `H1009` USER_KEY 무효)도 `H`로 시작하므로 "H로 시작하면 성공"으로 판정하면 안 된다. HTTP 4xx Body의 `responseCode`와 2xx Body의 `Header.responseCode ≠ H0000` 모두 `SsafyApiException`으로 처리한다. 응답 record의 숫자 필드는 Jackson 문자열→숫자 강제 변환에 의존하며, 알 수 없는 필드는 무시한다.

`MockRestServiceServer` 기반 단위 테스트(`RestSsafyFinanceClientTest`)가 요청 Header 형식과 REC 해석을 문서의 요청·응답 예시로 검증한다. **실제 금융망 호출은 2026-09-09 시도했으나 전달받은 API Key가 `E4004`/`H1008`(API KEY 무효)로 거절되어 확인하지 못했다**(`docs/remaining-work.md`).

## 테스트

`FinanceDataApiIntegrationTest`(H2, MockMvc, `SsafyFinanceClient` Mock) 34개와 `SsafyHeaderFactoryTest` 3개, `RestSsafyFinanceClientTest` 8개. 신용등급 관련 요청·응답 형식은 SSAFY API 문서 2.3.1, 2.7.1, 2.7.4의 예시를 그대로 사용한다.

- 동기화 확장점은 테스트 설정(`SyncHandlerStubs`)이 ACCOUNT 성공·LOAN 실패 스텁을 등록해 COMPLETED/SKIPPED/FAILED 집계와 `last_synced_at` 규칙을 검증한다.
- 통합 요약은 `sql/finance-summary-test-schema.sql`로 `account`, `card`, `loan`을 ERD 컬럼명 기준으로 다시 만들고(다른 도메인 엔티티가 일부 컬럼만 매핑하거나 없기 때문) 테스트가 사용자 ID를 안 뒤 행을 넣는다. 다른 사용자 데이터가 섞이지 않는지, CLOSED 계좌·종료 대출이 제외되는지, 마이너스통장이 부채로 잡히는지를 본다.
- MySQL 8.0 검증(`ddl-auto: validate`, V4 적용, 집계 SQL)은 아직 하지 않았다.
