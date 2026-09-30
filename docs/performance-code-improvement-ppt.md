# PPT용 코드 성능 개선: 가계부 숨긴 거래 지연 조회

## 발표에 바로 쓸 수 있는 사실

- **문제:** 가계부 진입 때 일반 거래와 숨긴 거래를 모두 `fetchAllPages(size=100)`로 끝까지 조회했다. 숨긴 거래 화면을 열지 않아도 숨긴 거래의 페이지 수만큼 API 요청과 `COUNT(*)` 쿼리가 발생했다.
- **코드 변경:** 일반 화면에서는 숨긴 거래 조회를 건너뛰고, 사용자가 `숨긴 거래 내역 보기`를 누를 때 해당 월의 숨긴 거래를 조회한다. 같은 월에서 다시 열면 메모리에 둔 결과를 사용한다. 새로고침과 거래 변경 후에는 필요한 경우 다시 조회한다.
- **딥링크 보존:** 숨긴 거래 상세 링크는 전체 숨긴 목록 대신 `GET /v1/transactions/{id}`로 해당 거래 한 건만 조회한다.
- **서버 조회 개선:** 거래 페이지가 100건 미만이면 반환된 행 수로 총건수를 계산해 `COUNT(*)`를 생략한다. 이런 페이지의 거래 목록 SQL은 **2회 → 1회**다. 꽉 찬 페이지와 범위를 벗어난 빈 페이지는 정확한 총건수를 위해 기존처럼 count를 실행한다.
- **확정 가능한 감소량:** 일반 가계부 진입 시 숨긴 거래 조회가 **최소 1회 → 0회**다. 숨긴 거래 페이지가 N개라면 **N회 → 0회**다. 조회 결과 매핑과 해당 거래 목록 렌더링도 진입 시 수행하지 않는다.
- **합친 효과의 예:** 일반 거래가 한 페이지(100건 미만)에 들어가는 월에는 첫 진입의 거래 목록 관련 SQL이 기존 일반 목록 `SELECT + COUNT`와 숨긴 목록 `SELECT + COUNT`의 **4회에서 일반 목록 `SELECT` 1회로 줄어든다**. 다른 집계 API의 SQL은 이 계산에 포함하지 않는다.
- **한계:** 이 변경은 가계부 화면의 불필요한 작업을 줄인다. 과거 전체 백엔드 부하 테스트에서 확인한 4 vCPU 처리량 한계의 개선율로 환산해서는 안 된다.

## 전후 측정 절차

### 2026-10-01 로컬 MySQL SQL 실측

`exec/dodam_dump.sql.gz`의 합성 거래 1,394,125건을 MySQL 8.0 임시 컨테이너에 복원했다. `infra/scripts/benchmark-transaction-page.py`로 하나의 연결에서 20쌍 예열 후, 기존 `COUNT + SELECT`와 개선 `SELECT`를 순서를 섞어 각각 200회 실행했다. 시간에는 SQL 실행, 결과 전달, 양쪽에 공통인 종료 마커 쿼리가 포함된다. [원본 수치](performance-measurements/ledger-sql-2026-10-01.json).

재현 명령(저장소 루트, 로컬 임시 컨테이너, 외부 포트 없음):

```powershell
docker run -d --name dodam-perf-mysql --network none -e MYSQL_ALLOW_EMPTY_PASSWORD=yes mysql:8.0 --skip-log-bin --innodb-buffer-pool-size=1G
docker cp exec/dodam_dump.sql.gz dodam-perf-mysql:/tmp/dodam_dump.sql.gz
docker exec dodam-perf-mysql mysql -uroot -e 'CREATE DATABASE dodam CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci'
docker exec dodam-perf-mysql sh -c 'gzip -dc /tmp/dodam_dump.sql.gz | mysql --default-character-set=utf8mb4 -uroot dodam'
python infra/scripts/benchmark-transaction-page.py --user-id 3 --month 2026-08 --repeats 200
python infra/scripts/benchmark-transaction-page.py --user-id 2 --month 2026-08 --repeats 200
docker rm -f dodam-perf-mysql
```

| 2026-08 사용자 | 월 일반 거래 | 기존 중앙값 | 개선 중앙값 | 중앙값 감소 | 기존 p95 | 개선 p95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ID 3 | 92건 | 2.026ms | 1.645ms | 18.8% | 2.243ms | 1.900ms |
| ID 2 | 27건 | 1.622ms | 1.328ms | 18.1% | 1.824ms | 1.467ms |

**이 수치는 로컬 DB의 목록 SQL 측정값이다.** HTTP 응답 시간, 가계부 화면 표시 시간, 전체 서버 처리량 개선율로 쓰면 안 된다. 도커 컨테이너의 MySQL 버퍼가 데워진 상태의 반복 조회다.

### HTTP 경로 후속 측정

기존 `ledger-journey.js`에 `HIDDEN_FETCH_MODE`를 추가했다. 같은 서버, 같은 데이터, 같은 VU와 테스트 프로필에서 아래 두 경우를 각각 여러 번 실행한다. 이 비교는 **프런트엔드 요청 패턴 변경의 효과**를 분리해 본다. SQL 코드 변경까지 합친 전후 비교는 변경 전 백엔드와 변경 후 백엔드를 각각 배포해 같은 조건에서 측정해야 한다.

```bash
# 기존 화면 진입 패턴
HIDDEN_FETCH_MODE=eager PROFILE=load LOAD_VUS=50 THINK_TIME=0

# 개선 후 일반 화면 진입 패턴
HIDDEN_FETCH_MODE=lazy PROFILE=load LOAD_VUS=50 THINK_TIME=0
```

위 환경변수를 기존 `infra/docs/load-test-records.md`의 `docker run ... ledger-journey.js` 명령에 `-e HIDDEN_FETCH_MODE=eager` 또는 `-e HIDDEN_FETCH_MODE=lazy`로 전달한다. 실행 순서를 eager → lazy → lazy → eager로 교차해 서버 상태 변화를 줄인다. 아래 값을 원본 k6 출력과 함께 기록한다.

| 지표 | 기존 eager | 개선 lazy |
| --- | ---: | ---: |
| `transactions_hidden` 요청 수 | 실측 | 0 |
| 전체 `http_reqs` | 실측 | 실측 |
| `journey_duration` p95 | 실측 | 실측 |
| `http_req_duration{name:transactions}` p95 | 실측 | 실측 |
| DB 거래 목록 `COUNT(*)` 실행 수 | 변경 전 빌드에서 실측 | 변경 후 빌드에서 실측 |
| 오류율 | 실측 | 실측 |

**주의:** `journey_duration`은 사용자 화면 표시 시간과 같지 않다. 현재 k6 스크립트는 화면의 동시 요청을 완전히 재현하지 않는다. PPT에서 초기 표시 시간 개선을 숫자로 주장하려면 앱의 실제 기기에서 화면 진입부터 거래 목록 표시까지 별도 측정해야 한다.

## 슬라이드 문안

> **숨긴 거래를 필요할 때만 조회하도록 변경**
>
> 기존에는 가계부에 들어올 때 일반 거래와 숨긴 거래를 매번 끝까지 조회했습니다. 숨긴 거래는 해당 메뉴를 열 때만 조회하고, 100건 미만인 거래 페이지에서는 불필요한 `COUNT(*)`를 없앴습니다. 합성 거래 139만 건의 로컬 MySQL에서 월 거래 92건인 사용자의 목록 SQL 중앙값은 **2.026ms에서 1.645ms로 18.8% 감소**했습니다. 일반 화면 첫 진입의 숨긴 거래 요청은 최소 1회에서 0회로 줄었습니다.

`설정 2줄` 개선과 이 코드 개선은 대상 시나리오가 다르므로 하나의 성능 향상률로 합치지 않는다. 위 18.8%는 **SQL 경로 중앙값**이며 HTTP 응답이나 전체 처리량 수치가 아니다.

## 현재 검증 상태

`git diff --check`와 k6 스크립트의 `node --check`는 통과했다. 변경한 백엔드의 페이지네이션 단위 테스트 2건과 거래 API 통합 테스트 33건이 통과했다. Docker MySQL에서 위 SQL 실측을 수행했다. 현재 실행 중인 백엔드·k6 컨테이너와 `infra/.env`, 부하 테스트 계정 파일은 없어 HTTP 부하 테스트는 아직 실행하지 않았다. 프런트엔드 TypeScript 패키지가 로컬에 없어 타입 검사는 실행하지 못했다.
