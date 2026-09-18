# Day 12 실제 진행 기록

## 2026-09-18 — 시연 서버 시드 적재 (S15P21D105-633 · 632)

| 항목 | 내용 |
| --- | --- |
| 13번 `DODAM_DEMO_LOAN_SEED.sql` | 시연 계정 U0003에 고금리 대출 1건 + 상환 8건 (fixture 생성 필요) |
| 12번 `DODAM_USER_DEMOGRAPHICS_BACKFILL.sql` | 시드 4,000명의 `birth_date`·`gender` 채우기 (미적용 상태였다) |

둘 다 **운영 DB에 쓰는** 작업이라 day-11 2절과 같은 순서로 진행했다 — 파일이 무엇을 바꾸는지 먼저 읽고,
안전장치와 롤백 지점을 확인한 뒤 실행하고, 파일 자체 검증과 별개로 독립 재확인을 했다.

### 1. 실행 전 — 파일이 로컬에 없었다

요청받은 세 파일(`make_demo_loan_fixture.py`, 두 seed)이 **로컬 클론의 어느 ref에도 없었다.**
`git fetch`도 자격증명이 없어 실패했다(`could not read Username for 'https://lab.ssafy.com'`).

서버 체크아웃(`~/app`)은 `82bb3ee [S15P21D105-633] ... seed(13번)·fixture 생성 스크립트`에 있었고 세 파일이
모두 존재했다. 어차피 실행 위치가 서버라 **서버 파일을 기준으로 읽고 실행**했다. 로컬에서 내용을 못 본 채
돌리지 않기 위해 292줄 seed와 90줄 스크립트를 서버에서 전부 읽고 시작했다.

### 2. 확인한 안전장치

| 확인 | 내용 |
| --- | --- |
| preflight | 6개 지표(사용자 1·출금계좌 1·fixture 1·fixture 형식 1·상품 1·해시 충돌 0)가 모두 맞아야 `ready=1` 행이 생기고, **모든 INSERT가 그 행의 존재를 조건으로 건다.** 하나라도 어긋나면 아무것도 안 들어간다 |
| DELETE 범위 | `loan_repayment` 중 **이번 대출(loan_id) 것만**. 대상 대출은 U0003 + fixture 해시로 특정된다 |
| 12번 UPDATE 범위 | `email REGEXP '^seed[.]kmeans[.]u[0-9]{4}@dodam[.]invalid$'`로 잠겨 있다. 실제 회원은 구조적으로 못 건드린다. 두 컬럼만 쓴다 |
| 멱등성 | 대출은 `uk_loan_user_account_hash` upsert, 상환은 삭제 후 재생성. 12번은 같은 값을 다시 쓴다 |
| 되돌리기 | U0003의 `loan_repayment` → `loan` 행 삭제(`account_name = 'KB 국민ONE대출'`) |
| 롤백 지점 | 당일 04:00 자동 백업 존재 (`/home/ubuntu/backups/mysql/dodam-20260918-040002.sql.gz`, 8.4MB) |
| 사전 조건 | V39(`user.birth_date`·`gender`) 적용됨(flyway 39·40 success), U0003 존재, ACTIVE 입출금 계좌 1개, 기존 대출 0건 |
| 의존 패키지 | `cryptography 41.0.7` 이미 설치됨 → `pip install` 불필요 |

### 3. 요청 명령 그대로 실행했으면 실패했을 부분 두 가지

#### (1) `$DB_USERNAME` / `$DB_PASSWORD`는 `infra/.env`에 없다

README와 요청의 명령은 이렇게 되어 있다.

```bash
set -a; . infra/.env; set +a
mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" <db> < ...
```

그런데 `infra/.env`의 키 이름은 `MYSQL_USER` / `MYSQL_PASSWORD` / `MYSQL_DATABASE`다.
`DB_USERNAME`·`DB_PASSWORD`는 **compose가 backend 컨테이너에 넘길 때 쓰는 이름**이지 `.env`에 있는 이름이
아니다. 그대로 실행하면 빈 값으로 인증에 실패한다.

→ `MYSQL_USER`/`MYSQL_PASSWORD`/`MYSQL_DATABASE`로 바꿔 실행했다. 비밀번호는 day-11과 같이 `MYSQL_PWD`
환경변수로 넘겨 **명령줄·프로세스 목록에 남기지 않았다**(`-p"$..."`는 남는다).

#### (2) 사전 점검에서 "상품 미적재" 거짓 경보 — 원인은 내 쿼리의 문자셋

preflight가 요구하는 `KB 국민ONE대출` 상품이 있는지 미리 보려고 `mysql -e "... product_name='KB 국민ONE대출'"`를
돌렸더니 **0건**이 나왔다. 상품 배치가 안 들어간 것처럼 보였다.

실제로는 상품이 있었다(`product_id=58`). 결과 표에 회사명이 `KB ????`로 찍힌 것이 단서였다 —
`docker compose exec`로 넘긴 **한글 리터럴이 latin1로 해석돼 깨진 것**이지 데이터 문제가 아니었다.
`--default-character-set=utf8mb4`를 붙이니 정확히 1건이다.

> seed 파일 자체는 첫 줄이 `SET NAMES utf8mb4`라 파이프로 넣으면 영향이 없다. **문제는 내가 즉석으로 짠
> 점검 쿼리 쪽이었다.** day-11의 `LIKE '[1-4]'` 거짓 경보와 같은 교훈 — 운영 데이터를 이상하다고 보고하기
> 전에 **쿼리부터 의심한다.**

### 4. 실행

```bash
set -a; . ~/app/infra/.env; set +a
python3 backend/scripts/make_demo_loan_fixture.py > /tmp/demo_loan_fixture.sql   # 11줄, 키·원문 미포함
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/demo_loan_fixture.sql
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < ../backend/database/seeds/DODAM_DEMO_LOAN_SEED.sql
rm -f /tmp/demo_loan_fixture.sql
```

fixture는 계좌번호 **원문도 키도 담지 않는다** — 암호문·해시·마스킹값만 나온다(`110000-**-**3101`,
암호문 41바이트 = 12바이트 IV + 29). 적재 후 즉시 삭제했고, seed 파일의 cleanup이 fixture 테이블까지 지운다.

### 5. 결과

#### 13번 — U0003 고금리 대출 (기대와 정확히 일치)

| 지표 | 기대 | 실제 |
| --- | --- | --- |
| preflight | 1/1/1/1/1/1/0 | **1/1/1/1/1/1/0** |
| 대출 | 1건, 잔액 12,998,492 · 8.5000% | `loan_id=3001`, 12,998,492 · 8.5000% · ACTIVE |
| 상품 연결 | 필수 | `financial_product_id=58` (KB국민은행 KB 국민ONE대출) |
| 상환 이력 | 8건 (PAID 7 + SCHEDULED 1) | **8건 (7 + 1)**, 2026-03-10 ~ 2026-10-10 |
| 다음 상환일 | 2026-10-10 | 2026-10-10 |
| 다른 시연 계정 대출 | 0 | **0** |

상환 8건의 원금·이자도 파일의 원리금균등 계산과 한 원 단위까지 일치한다(410,331 = 302,916+107,415 …).

#### 12번 — 시드 4,000명 인구통계

| 지표 | 값 |
| --- | --- |
| 시드 사용자 | 4,000명, **누락 0** (적재 전 4,000명 전부 NULL이었다) |
| 성별 분포 | FEMALE 2,000 / MALE 2,000 |
| 연령대 × 성별 | 20S~50S 8개 구간, **모든 구간에 행동 코호트 4개** (군집 신호가 성별·나이에 섞이지 않는다) |
| 시연 계정 | U0001 1997-08-27 F / U0002 1990-08-16 M / U0003 1983-08-05 F / U0004 1976-07-25 M |

#### 독립 재확인

- `tmp_dodam%` 작업 테이블 **0개** — 두 파일의 cleanup이 모두 돌았다
- 전체 `loan` 3,001건 (523의 3,000 + 이번 1건)
- 시드 외 사용자 **0명** → 실제 회원 데이터가 영향을 받을 여지 자체가 없었다

### 6. U0003 재진단 — 발견 화면에 실제로 잡히는지 확인

seed는 `loan` 행을 넣을 뿐이고 기존 진단은 그대로라, README대로 U0003으로 재진단을 돌렸다.

```
POST /api/v1/diagnoses            {"forceRefresh": true}
POST /api/v1/recommendations/generate  {"diagnosisId": 9}
```

#### 진단 결과

| 항목 | 값 |
| --- | --- |
| `diagnosisId` | **9** (`cached=false` — 캐시 재사용이 아니라 실제로 다시 돌았다) |
| `ruleVersion` | `v6-last-month-vs-3m` |
| 종합 | `RISK`, 문제 5건(HIGH 4) |
| `coverage`의 `HIGH_INTEREST_RATE` | **`EVALUATED`** (적재 전에는 대출이 없어 평가 대상이 아니었다) |

#### 대출 문제 — 기획 수치와 한 자리까지 일치

> **[HIGH]** `'KB 국민ONE대출' 금리가 비교상품 공시금리보다 높아요` (`issueId=36`, `domain=LOAN`, `type=HIGH_INTEREST_RATE`)
>
> 연 8.5%로 같은 조건 상품의 공시 최저금리 중앙값 5.229%보다 3.271%p 높아요. 공시 최저 연 5.028% 상품으로
> 대환하면 만기까지 이자 약 741,260원을 아낄 수 있어요(중도상환수수료·부대비용 제외).

| 지표 | README 기대 | 실제 |
| --- | --- | --- |
| 비교군 / 후보 수 | `CREDIT` / 11개 | `CREDIT` / **11** |
| 기준금리(공시 최저 중앙값) | 5.229% | **5.229%** |
| 편차 | +3.271%p → HIGH | **+3.271%p → HIGH** |
| 대환 대상 | 카카오뱅크 신용대출 5.028%, 잔여 36회 | **동일** |
| 만기까지 이자 | 1,773,405 → 1,032,145 | **동일** |
| 절감 | 741,260원 (월 20,590 · 연 247,080) | **741,260 / 월 20,590** |

추천도 생성됐다: `created=5`, 그중 **`'KB 국민ONE대출' 대환 알아보기`** — "대환하면 만기까지 이자
741,260원을 아낄 수 있어요(수수료 제외)". `byDomain`에 `LOAN: 1`이 새로 생겼다.

### 7. 원래 계획과 다르게 실행된 부분 (기록)

요청받은 절차를 그대로 실행하지 **못했거나 하지 않은** 지점 전부다.

| # | 계획 | 실제 | 왜 |
| --- | --- | --- | --- |
| 1 | `mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD"` | `docker compose exec -T -e MYSQL_PWD=… mysql mysql -u"$MYSQL_USER" …` | `infra/.env`에 `DB_USERNAME`·`DB_PASSWORD`가 **없다**(`MYSQL_USER`/`MYSQL_PASSWORD`/`MYSQL_DATABASE`). 그대로 돌리면 빈 값으로 인증 실패. 비밀번호도 `-p`로 주면 프로세스 목록에 남아 `MYSQL_PWD`로 바꿨다 |
| 2 | `pip install cryptography` 필요 | 설치하지 않음 | 서버에 이미 `cryptography 41.0.7`이 있었다. 운영 호스트에 불필요한 패키지를 넣지 않았다 |
| 3 | 레포 파일로 실행 | **서버 체크아웃(`~/app`, `82bb3ee`) 파일**로 실행 | 세 파일이 로컬 클론의 어느 ref에도 없고 `git fetch`가 자격증명 부재로 실패했다. 실행 전 서버에서 292줄 seed와 스크립트 전문을 읽고 진행 |
| 4 | (계획에 없던 단계) 사전 점검 쿼리 | `kb_product=0` **거짓 경보** 후 정정 | 내 즉석 쿼리에 `--default-character-set=utf8mb4`가 빠져 한글 리터럴이 깨졌다. 상품은 정상 존재(`product_id=58`). seed 파일은 첫 줄이 `SET NAMES utf8mb4`라 무관 |
| 5 | `POST /diagnoses` → `diagnosisId` 파싱 | 첫 스크립트가 파싱 실패 | 응답이 `data.diagnosis.diagnosisId`로 **한 단계 더 중첩**돼 있다(내가 `data.diagnosisId`로 가정). 그 여파로 `{"diagnosisId":}` 빈 본문이 나가 `/recommendations/generate`가 **400 INVALID_REQUEST**. 쓰기는 일어나지 않았고 `diagnosisId=9`로 다시 호출해 정상 처리 |
| 6 | 발견 요약 `expectedAnnualCostSaving` = 247,080 기대 | 실제 **76,516,680** | 247,080은 **대출 항목 하나의 연 절감**(월 20,590×12)이고, 요약은 `ANNUAL_SAVING_BEST_ALTERNATIVE_PER_ISSUE`로 **문제 5건의 합**이다. 합계를 지배하는 것은 "월 6,181,000원 감축" 추천 — day-11 3절에서 이미 적은 **목표·예산 시드 금액이 소득 대비 비현실적인 문제(493 소관)**이며 633과 무관하다 |

5번은 서버 계약이 바뀐 게 아니라 **내가 응답 모양을 확인하지 않고 스크립트를 쓴 것**이 원인이다.
400은 서버가 잘못된 요청을 제대로 막은 결과다.

### 8. 남은 것 (7절까지 시점)

- [x] ~~U0003 재진단~~ — 6절
- [x] ~~현금흐름에 대출 상환이 잡히는지 확인~~ — 9절
- [x] ~~발견 요약 연 절감액 76,516,680원의 원인 규명~~ — 10절 (**원인이 493 시드가 아니었다**)
- [x] ~~`seeds/README.md`의 서버 적용 명령 수정~~ — 11절에서 교정안 전달 → 633 담당자가 반영(13절)
- [ ] 재실행 시 fixture부터 다시 만들 것 (seed cleanup이 fixture 테이블을 지운다)

### 9. 현금흐름 — 대출 상환이 실제로 잡힌다 (확인 완료)

U0003은 **완료된 예측 자체가 없었다**(`GET /cashflow/forecasts/latest` → `RESOURCE_NOT_FOUND`).
그래서 진단 `coverage`의 `CASHFLOW`가 계속 `FORECAST_NOT_FOUND`였다. 예측을 한 번 돌려 확인했다.

```
POST /api/v1/cashflow/forecasts  {}   → forecastId=1, cached=false, 90일(2026-09-18 ~ 12-16)
```

| 항목 | 값 |
| --- | --- |
| 현재 잔액 | 17,460,000 |
| 오늘 가용액 | **17,049,669** (= 17,460,000 − **410,331**) |
| 이벤트 | **1건** — `2026-10-10 / LOAN_REPAYMENT / OUTFLOW / 410,331 / 대출 상환` |

**이번 대출이 화면 수치를 바꾼 것이 맞다.** 가용액 차이가 정확히 월 상환액 한 회분이다.

다만 예측 자체가 아직 성기다 — `incompleteReasons` 3건이 붙는다.

| 사유 | 뜻 |
| --- | --- |
| `LOAN_REPAYMENT_RECONCILIATION_REQUIRED` | **저장된 일정만 쓴다.** seed가 넣은 SCHEDULED 1건(10-10)만 잡히고 11-10·12-10은 투영하지 않는다. 금액·회차가 어긋나면 이후 원금 잔액이 달라지므로 근거 없이 추정하지 않는다는 정책(`CashflowEventCollector`) |
| `ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE` | 급여 등 계좌 기반 정기 일정 원천이 없다 |
| `CARD_SUBSCRIPTION_REQUIRES_BILLING_RECONCILIATION` | 카드·구독 청구 대사가 안 돼 있다 |

→ **시연에서 현금흐름 화면은 90일 동안 이벤트 1건만 보인다.** 이번 대출 적재와 무관한 기존 제약이지만,
화면을 띄울 계획이라면 알고 있어야 한다.

### 10. 발견 요약 76,516,680원 — 원인은 493 시드가 아니라 **어제 만들어진 목표 하나**였다

7절 6번에서 "493 소관"으로 적었는데, 숫자를 분해해 보니 아니었다. 정정한다.

| 문제 | 연 절감 기여 |
| --- | --- |
| **월 지출 줄이기 (월 6,181,000 감축)** | **74,172,000** |
| 택시 지출 (월 94,767) | 1,137,204 |
| 문화·여가 지출 (월 80,033) | 960,396 |
| 대출 대환 (월 20,590) | 247,080 |
| **합계** | **76,516,680** (요약값과 정확히 일치) |

즉 **97%가 "월 지출 줄이기" 한 건**에서 나온다. 그 목표를 열어 보면 원인이 분명하다.

```json
{ "goalId": 8211, "name": "월 지출 줄이기", "type": "SPENDING",
  "monthlyBudget": 200000, "createdAt": "2026-09-17T14:39:40" }
```

- **월 예산이 200,000원**이다. U0003의 실제 월 지출은 약 638만원이라 "월 618만원을 줄이라"가 나온다.
- `createdAt`이 **2026-09-17 14:39**다. 493 시드의 목표는 `created_at = 2026-03-01 09:00`(같은 사용자의
  `goalId=4`가 그렇다)이므로 **이 목표는 시드가 아니라 어제 누군가 화면·API로 만든 것**이다.

→ 고칠 지점은 493 시드가 아니라 **이 목표 한 건의 `monthlyBudget`**이다. 남의 계정 데이터라 임의로
바꾸지 않고 남긴다. (`goalId=4` "노트북 바꾸기"의 목표액 51,300,000원은 별개로 493 시드 쪽 이름·금액
불일치이며, 연 절감액에는 기여하지 않고 목표 기여액에만 들어간다.)

### 11. `seeds/README.md` 수정 — 지금은 손댈 수 없다

고쳐야 할 블록은 **633 브랜치에만 있다**(로컬 클론의 어느 ref에도 없고 `git fetch`는 자격증명 부재로
실패한다). 여기서 로컬 README를 고치면 633이 병합될 때 충돌하거나 섹션이 중복된다. 서버 체크아웃
(`~/app`)을 직접 고치는 것은 배포 체크아웃을 더럽히는 일이라 하지 않았다.

브랜치를 받을 수 있게 되면 `backend/database/seeds/README.md` 111~112줄을 이렇게 바꾸면 된다.

```bash
# infra/.env 에는 DB_USERNAME/DB_PASSWORD 가 없다(그 이름은 compose가 backend 컨테이너에 넘길 때 쓴다).
set -a; . infra/.env; set +a
python3 backend/scripts/make_demo_loan_fixture.py > /tmp/demo_loan_fixture.sql
cd infra
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/demo_loan_fixture.sql
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  < ../backend/database/seeds/DODAM_DEMO_LOAN_SEED.sql
rm /tmp/demo_loan_fixture.sql
```

바뀐 점 세 가지: **(1)** `MYSQL_USER`/`MYSQL_PASSWORD`/`MYSQL_DATABASE` 사용, **(2)** 비밀번호를
`MYSQL_PWD`로 넘겨 프로세스 목록에 남기지 않음, **(3)** MySQL은 컨테이너 안에만 있으므로
`docker compose exec -T` 경유. 한글이 들어간 즉석 점검 쿼리를 쓸 때는 `--default-character-set=utf8mb4`도
빠뜨리지 말 것(7절 4번).

### 12. 이 절차에서 새로 만든 운영 상태 (되돌릴 때 참고)

| 대상 | 상태 | 되돌리기 |
| --- | --- | --- |
| `loan` / `loan_repayment` | U0003 대출 1건(`loan_id=3001`) + 상환 8건 | 해당 행 삭제 |
| `user.birth_date` / `gender` | 시드 4,000명 | 두 컬럼 NULL로 (실제 회원 없음) |
| `diagnosis` | `diagnosisId=9` 신규 + 추천 5건 | 이전 진단은 그대로 남아 있다 |
| `cashflow_forecast` | `forecastId=1` 신규 (U0003 최초) | 삭제 시 `CASHFLOW`가 다시 `FORECAST_NOT_FOUND` |

### 13. 633 담당자 회신 — README 반영 완료, 그리고 "12번 미적용" 오인 정정

11절의 교정안을 전달했고 그대로 반영됐다는 회신을 받았다.

| 항목 | 결과 |
| --- | --- |
| 지적 3건(`.env` 키 이름 / 컨테이너 경유 / `MYSQL_PWD`) + utf8mb4 주의 | README 106~114줄에 반영 |
| `cryptography` | "없으면 설치"로 표현 완화 |
| 다른 seed 절의 같은 패턴 | 없음(담당자 확인) |
| 브랜치 | `S15P21D105-633-readme-server-apply` (`d32ec86`), MR 예정 |

#### 회신에 사실과 다른 부분이 하나 있었다

> "12번 `DODAM_USER_DEMOGRAPHICS_BACKFILL.sql`은 아직 미적용이니 같은 방식으로 적재 부탁드립니다"

**이미 적재돼 있다.** 09-18에 13번과 같은 세션에서 함께 돌렸고(5절), 회신을 받고 서버 상태를 다시 조회해
확인했다.

```
seed_users = 4000,  missing_demographics = 0
gender: FEMALE 2000 / MALE 2000
U0001 1997-08-27 F / U0002 1990-08-16 M / U0003 1983-08-05 F / U0004 1976-07-25 M
```

**다시 돌리지 않았다.** 파일은 멱등이라 재실행해도 같은 값을 다시 쓸 뿐이지만, 운영 DB에 쓰는 작업을
"어차피 무해하니까"로 반복하지 않는 편이 맞다. 상태를 먼저 조회해 이미 충족된 것을 확인하고, 근거
수치와 함께 회신했다. 재적재를 원하면 말해 달라고 덧붙였다.

→ README `## 검증 상태` 절의 12번 항목을 "2026-09-18 적용 완료(시드 4,000명, 누락 0, 성별 2,000/2,000)"로
고쳐 달라고 같은 회신에서 요청했다. 이미 같은 브랜치를 손보고 있으므로 한 번에 반영하는 편이 낫다.

#### 남은 확인

- [ ] MR이 올라오면 **수정된 명령 블록 그대로** 한 번 더 검증한다(지금 서버 체크아웃은 `82bb3ee`라
      교정 전 README다). 로컬에 633 브랜치를 받을 수 있게 되면 그때 대조한다.
- [ ] 이 저장소에서 `git fetch`가 자격증명 부재로 실패하는 상태는 그대로다 — 590/591 push,
      633 브랜치 확인 모두 같은 이유로 막혀 있다.

## 2026-09-18 — 14번 U0003 소비 스토리 시드 (S15P21D105-636)

13번과 같은 방식, fixture 불필요. 요청받은 명령은 **11절에서 내가 교정안으로 낸 형태 그대로**였다
(`MYSQL_*` + `MYSQL_PWD` + `docker compose exec` + `--default-character-set=utf8mb4`) — 그대로 실행했고
이번엔 명령 자체를 손댈 곳이 없었다.

### 14. 실행 전 검토와 기준선

파일(438줄)을 먼저 읽었다.

| 확인 | 내용 |
| --- | --- |
| preflight | 5개 지표(컨텍스트 1·카드 1·카테고리 11·INCOME 월 7·plan 그룹 누락 0). 모든 쓰기가 `ready=1` 조건에 걸린다 |
| DELETE 범위 | U0003의 `source_type='SEED'` + `transaction_id LIKE 'SEED-U0003-%-VAR-%'` 또는 `'SEED-SEP-U0003-%-VAR-%'`. **변동 지출만** 지우고 INCOME/FIXED/SUBSCRIPTION/SAVING은 건드리지 않는다 |
| 다른 사용자 | 컨텍스트 테이블이 U0003 한 명이라 조인으로 격리된다 |
| 되돌릴 수 없는 변경 | `user.name`을 `김싸피`로 바꾼다(이메일·로그인은 그대로). 원래 이름 `도담 사용자 0003`은 복구되지 않는다 |
| 롤백 지점 | 당일 04:00 백업 (`dodam-20260918-040002.sql.gz`) |

적재 전 기준선을 떠 뒀다 — **이 시드가 고치려는 문제가 숫자로 보인다.**

| | 적재 전 | 적재 후 |
| --- | --- | --- |
| 이름 | `도담 사용자 0003` | **김싸피** |
| VAR 행 | 16행/월 × 7 = 112 | 58~71행/월 = **439** |
| 월 변동 소비 | 1,080,900 ~ 1,166,500 (**폭 8.6만원, 사실상 직선**) | 939,000 ~ 1,653,000 |

### 15. 결과 — 기대값과 전부 일치

preflight `1 / 1/1/11/7/0` → `ready=1`.

| 월 | 기대 | 실제 | `plan_gap` |
| --- | --- | --- | --- |
| 2026-03 | 974,000 | 974,000 | 0 |
| 2026-04 | 939,000 | 939,000 | 0 |
| 2026-05 | 1,653,000 | 1,653,000 (TRAVEL 480,000) | 0 |
| 2026-06 | 1,015,000 | 1,015,000 | 0 |
| 2026-07 | 1,031,000 | 1,031,000 | 0 |
| 2026-08 | 994,000 | 994,000 | 0 |
| 2026-09 | 978,000 | 978,000 (~09-27) | 0 |

- 계약 위반 **0건**, `INC 7 / FIX 21 / SUB 7 / SAV 7` **그대로 유지** (VAR만 112 → 439)
- 8월 증가율: **FOOD +51.5%**, **CAFE +45.0%**. 나머지는 전부 0% 이하(TAXI −30.8, GROCERY −33.8,
  SHOPPING −61.7 …) → 627 규칙이 **두 개만** 잡도록 설계된 대로다
- `tmp_dodam%` 작업 테이블 0개, `last_transaction_date` 2026-09-27, **다른 사용자 변동 행 변경 0건**

### 16. 재진단 — 화면에 실제로 바뀌어 나온다

```
POST /diagnoses {"forceRefresh":true}  → diagnosisId=10, cached=false, RISK, 문제 5건(HIGH 3)
```

| 진단 9 (적재 전) | 진단 10 (적재 후) |
| --- | --- |
| [HIGH] 문화·여가 소비가 2개월째 늘고 있어요 | **[HIGH] 식비 소비가 2개월째 늘고 있어요** |
| [HIGH] 택시 소비가 늘었어요 | **[MEDIUM] 카페 소비가 2개월째 늘고 있어요** |
| [HIGH] 'KB 국민ONE대출' 금리가 … | [HIGH] 'KB 국민ONE대출' 금리가 … (유지) |
| [HIGH]/[MEDIUM] 목표 달성 계획 점검 | 동일 |

추천도 다시 만들었다(`created=5`): 식비 월 176,667원, 카페 월 45,000원, 대환 741,260원(유지).

### 17. 원래 계획과 다르게 실행된 부분

| # | 계획 | 실제 | 왜 |
| --- | --- | --- | --- |
| 1 | `POST /diagnoses {"forceRefresh":true}`까지 | **`POST /recommendations/generate`도 실행** | 요청에는 재진단만 있었지만, 발견 화면은 추천까지 있어야 완성된다(633 README의 흐름도 `진단 → 추천 생성`이다). 진단 10에 딸린 추천이 없으면 화면이 비어 보인다 |

그 외에는 요청 절차와 다른 점이 없다. 11절 교정안이 이미 반영된 명령을 받았기 때문이다.

### 18. 넘길 것 두 가지

#### (1) 발견 요약 금액 문제는 **이 시드로 해결되지 않았다**

`expectedAnnualCostSaving` 76,516,680 → **77,231,088**. 소비를 현실적으로 바꿨는데도 그대로인 이유는
10절에서 찾은 원인이 소비 데이터가 아니기 때문이다 — `goalId=8211 "월 지출 줄이기"`의
**`monthlyBudget`이 200,000원**이라 "월 619만원을 줄이라"가 계속 나온다(이번에도 6,193,667원).

> 변동 소비가 약 100만원으로 현실적이 된 뒤에도 격차가 619만원이라는 것은, 이 수치가 변동 소비가 아니라
> **예산값 자체**에서 나온다는 것을 한 번 더 확인해 준다.

→ 636과 무관한 별건이며, 목표 한 건의 예산값을 고쳐야 한다. 남의 계정 데이터라 그대로 두었다.

#### (2) 14번 파일의 트랜잭션이 의도대로 원자적이지 않다 (636에 전달할 것)

`START TRANSACTION`(241줄) ~ `COMMIT`(344줄) 사이 262~269줄에 `DROP TABLE` / `CREATE TABLE`
(`tmp_dodam_u3story_rows`)이 들어 있다. **MySQL은 DDL에서 암묵적 커밋을 한다.** 따라서

- 이름 변경(2절)과 변동 행 삭제(3절)는 `DROP TABLE`을 만나는 순간 **이미 커밋된다**
- 그 뒤 INSERT가 실패하면 U0003은 **이름만 김싸피로 바뀌고 변동 거래가 0건인 상태**로 남는다

실제로는 preflight가 앞에서 막아주고 파일이 멱등이라 다시 돌리면 복구되지만, 트랜잭션으로 감싼
의도는 지금 형태로는 성립하지 않는다. 작업 테이블 생성을 `START TRANSACTION` **앞으로** 옮기면 해결된다.


---

## 2026-09-18 — API 19 Improvement · API 20 Report 완료 기준 재점검 (S15P21D105-590 · 635)

> Day 11 갈래 A에서 구현한 두 API가 **완료 기준을 실제로 만족하는지** 다시 확인한 기록이다.
> 확인 → 발견 → 고친 것 → 안 고치고 넘긴 것 순으로 적는다.

### 1. 무엇을 기준으로 봤나

요청받은 완료 기준 두 줄을 그대로 체크리스트로 썼다.

| # | 완료 기준 | 결론 |
| --- | --- | --- |
| A | 추천 수락과 개선 행동을 **중복 없이** 연결 | 충족 |
| B | **예상 효과**와 **실제 관측값**을 구분 | 충족 |
| C | **소유권** 검증 | 충족 |
| D | **기간** 검증 | 충족 |
| E | **페이지** 검증 | 충족 |
| F | **재진단 중복 방지** | 충족 |
| G | **리포트 기반** 응답 | 충족 |

확인 방법은 세 가지를 겹쳤다 — ① 명세서 CSV와 컨트롤러 1:1 대조, ② 로컬 테스트 실행,
③ **시연 서버(`j15d105.p.ssafy.io`) 실호출**. 코드만 읽고 "되어 있다"고 적지 않았다.

### 2. 엔드포인트 대조 — 19종 전부 있다

`docs/API명세서/19 Improvement …_all.csv`(11건) · `20 Report …_all.csv`(8건)와
`ImprovementController` · `ReportController`를 한 줄씩 맞췄다. **빠진 엔드포인트도, 명세에 없는데 열려 있는
엔드포인트도 없다.**

| API 19 | 메서드 · 경로 | 시연 서버 |
| --- | --- | --- |
| 개선 결과 요약 | GET `/improvements/summary` | 200 |
| 개선 행동 이력 | GET `/improvements/actions` | 200 |
| 개선 행동 상세 | GET `/improvements/actions/{actionId}` | 404(없는 id, 정상) |
| 실행 전후 비교 | GET `/improvements/actions/{actionId}/comparison` | 404(없는 id, 정상) |
| 절감금액 | GET `/improvements/savings` | 200 |
| 부채감소액 | GET `/improvements/debt-reduction` | 200 |
| 소비감소액 | GET `/improvements/spending-reduction` | 200 |
| 저축증가액 | GET `/improvements/saving-increase` | 200 |
| 목표달성일 개선 | GET `/improvements/goal-impact` | 200 |
| 월별 개선추이 | GET `/improvements/monthly-trend` | 200 |
| 재진단 요청 | POST `/improvements/rediagnose` | **호출하지 않음**(6절 참조) |

| API 20 | 메서드 · 경로 | 시연 서버 |
| --- | --- | --- |
| 데일리 브리핑 | GET `/reports/daily` | 200 |
| 월간 리포트 | GET `/reports/monthly` | 200 |
| 리포트 목록 | GET `/reports` | 200 |
| 월간 소비패턴 | GET `/reports/monthly/spending-pattern` | 200 |
| 리포트 상세 | GET `/reports/{reportId}` | 200 |
| 월간 목표변화 | GET `/reports/monthly/goal-impact` | 200 |
| 다음달 예정비용 | GET `/reports/monthly/upcoming-expenses` | 200 |
| 월간 개선행동 요약 | GET `/reports/monthly/improvements` | 200 |

### 3. 완료 기준별 근거

**A. 중복 없는 연결.** 개선 행동을 만드는 입구가 하나뿐이다 — `RecommendationStatusService`가
같은 트랜잭션에서 부르는 `ImprovementActionRecorder`. API 19에는 행동을 만들거나 상태를 바꾸는
엔드포인트가 아예 없다. 중복은 **3중으로** 막혀 있다.

- DB: `uk_improvement_action_recommendation_id` (V37) — 추천 1건당 행동 1행
- 엔티티: 같은 `@UniqueConstraint`를 `ImprovementAction`에도 선언 (H2 테스트는 Flyway를 끄고 엔티티로
  스키마를 만들어서, 여기 없으면 테스트에서 검증이 안 된다)
- 코드: DEFERRED → 재수락은 `INSERT`가 아니라 `replan()` — 기존 행의 기준선만 다시 잡는다

테스트 `deferAndReacceptDoesNotCreateASecondAction`이 수락 → 보류 → 재수락 후 `hasSize(1)`을 단언한다.

**B. 예상 ≠ 실측.** `improvement_action`의 금액 컬럼은 **전부 실측값**이고, 예상치는 `recommendation`에만
있다. 응답에서 `expectedMonthlySaving`(추천)과 `actualMonthlySaving`(행동)이 다른 소스에서 온다.
측정 전에는 `actual*`·`measuredAt`이 **null이고 0이 아니다** — "효과 0원"과 "아직 모름"을 구분한다.
`ImprovementSummaryResponse`도 실측만 더하고, `measuredActions`로 "합계 0"의 의미를 구분해 준다.
테스트 `acceptCreatesOneActionAndKeepsExpectedApartFromActual`이 `expected=12000`,
`actual` 부재를 함께 단언한다.

**C~E. 소유권 · 기간 · 페이지.** 시연 서버 실호출 결과.

```
/reports/999999                              [404] RESOURCE_NOT_FOUND  리포트를 찾을 수 없습니다.
/improvements/actions/999999                 [404] RESOURCE_NOT_FOUND  개선 행동을 찾을 수 없습니다.
/improvements/actions/999999/comparison      [404] RESOURCE_NOT_FOUND
/improvements/actions/abc                    [400] INVALID_REQUEST
/improvements/actions?page=-1                [400] VALIDATION_ERROR
/improvements/actions?size=101               [400] VALIDATION_ERROR
/improvements/actions?startDate=09-10&endDate=09-01   [400] 시작일은 종료일보다 늦을 수 없습니다.
/improvements/monthly-trend?startMonth=2026-9         [400] YYYY-MM 형식의 유효한 연월이어야 합니다.
/improvements/monthly-trend?2025-01~2026-09           [400] 조회 기간은 최대 12개월까지 가능합니다.
/reports/monthly?yearMonth=2026-09           [400] 아직 끝나지 않은 월의 리포트는 만들 수 없습니다.
/reports/monthly?yearMonth=2026-13           [400] YYYY-MM 형식…
/reports/daily?date=2026-09-18               [400] 아직 끝나지 않은 날의 브리핑은 만들 수 없습니다.
/reports?size=0                              [400] VALIDATION_ERROR
/reports?type=WEEKLY                         [400] INVALID_REQUEST
/reports/monthly/goal-impact?goalId=999999   [404] 목표를 찾을 수 없습니다.
/reports/monthly/upcoming-expenses?2028-10   [400] 예정 비용은 12개월 뒤까지 조회할 수 있습니다.
```

소유권은 **없는 리소스와 타인 리소스를 구분하지 않는다**(둘 다 404). 리포지터리 메서드에 `userId`
조건이 박혀 있어 서비스 코드가 검사를 빠뜨릴 수 없는 구조다.

**F. 재진단 중복 방지.** `ImprovementRediagnoseService`가 진단을 다시 구현하지 않고 진단 실행 API와
**같은 `DiagnosisRunner`**를 부른다. 그래서 중복 방지가 `DiagnosisExecutionStore.admit()` 하나로 모인다 —
임대 5분 안에 진행 중 접수가 있으면 409, 같은 날 같은 규칙 버전의 완료 진단이 있으면 그것을 그대로
돌려주고(`created:false`, 200), 아니면 새로 실행(201). `forceRefresh`를 열지 않은 것이 이 API의
"중복 요청 방지"다. `goalId`는 `admit()`이 소유권까지 확인한다.
테스트 `rediagnoseCreatesOnceAndReusesTheSameDayResult`가 201 → 200 전이를 단언한다.

**G. 리포트 기반 응답.** 소비패턴·목표변화·개선행동 요약은 조회 시점에 다시 집계하지 않고
**저장된 월간 리포트의 JSON을 읽는다.** 같은 달을 두 경로로 집계하면 합계가 어긋나기 때문이다.
시연 서버에서 확인: `/reports/monthly`의 `totalExpense 6,455,800`과
`/reports/monthly/spending-pattern`의 `totalExpense 6,455,800`이 같은 값이다(둘 다 reportId=1에서 나왔다).
`READY`로 굳은 리포트는 재생성하지 않고, `NO_TRANSACTIONS`·`INSUFFICIENT_DATA`만 다음 조회 때 다시 만든다.

### 4. 고친 것 — 3곳

`./gradlew test` 전체 **1,231건 통과 / 실패 0**(수정 전후 모두).

#### (1) 월별 개선추이가 `ImprovementClock`이 아니라 벽시계를 쓰고 있었다 — 값이 조용히 사라지는 경로

`ImprovementQueryService`의 나머지 10종은 전부 `clock.today()`를 쓰는데
`monthlyTrend`만 `monthPeriods.resolve(startMonth, endMonth)` 2-인자 오버로드를 불렀다. 이 오버로드는
안에서 `YearMonth.now(SERVICE_ZONE)`을 직접 읽는다.

**왜 고쳤나.** 단순한 취향 문제가 아니다. 추이의 **버킷 키**는 `measuredAt`·`completedAt`에서 나오고
이 값들은 `ImprovementClock`이 찍는다. 반면 **버킷 경계**는 벽시계가 정했다. 두 기준이 어긋나면
`buckets.get(month)`가 `null`을 돌려주고 코드가 `continue`로 넘어간다 — **에러 없이 그 달의 절감액이
응답에서 빠진다.** 운영에서는 두 시계가 같아 지금 당장 틀린 값이 나오지는 않지만, 시계를 고정하는
모든 상황(테스트·시연용 시각 이동)에서 바로 깨진다. 실제로 기존 테스트가 이 사실을 알고
"공용 정책이 실제 현재 월을 본다"는 주석과 함께 **우회하고 있었다.**

**어떻게 고쳤나.** `YearMonthPeriodPolicy`에 이미 있던 3-인자 `resolve(start, end, currentMonth)`를
package-private → `public`으로 열고(다른 호출부 영향 없음, 추가만 한 셈이다) 기준월을 넘겨준다.

```java
// before
YearMonthPeriod period = monthPeriods.resolve(startMonth, endMonth);
// after
YearMonthPeriod period = monthPeriods.resolve(startMonth, endMonth, YearMonth.from(clock.today()));
```

테스트도 우회 주석을 지우고, **파라미터를 모두 생략했을 때 고정 시계의 현재 월로 끝나는 6개월**이
나오는지(`2026-04`~`2026-09`, 6개) 단언을 추가했다. 이제 실제 날짜가 흘러도 이 테스트는 흔들리지 않는다.

- `backend/src/main/java/com/ssafy/dodam/global/policy/YearMonthPeriodPolicy.java`
- `backend/src/main/java/com/ssafy/dodam/domain/improvement/service/ImprovementQueryService.java`
- `backend/src/test/java/com/ssafy/dodam/domain/improvement/ImprovementApiTest.java`

#### (2) 스냅샷의 사유 코드가 대출 건수만큼 중복 적재됐다

`ImprovementSnapshotReader`가 금리 없는 대출을 만날 때마다 `unavailable.add(LOAN_RATE_UNAVAILABLE)`을
했다. 금리 없는 대출이 3건이면 같은 코드가 3번 들어가고, 그대로 `before_snapshot` JSON에 **굳는다**
(스냅샷은 수락 시점 기준선이라 나중에 다시 만들지 않는다).

**왜 고쳤나.** `unavailable`은 주석에도 "값을 만들지 못한 항목의 **사유 코드**"라고 적혀 있는 집합인데
실제로는 개수가 대출 건수를 드러내는 리스트였다. 소비자가 코드 존재 여부로 분기하면 지금도 동작하지만,
저장된 JSON이 대출 건수에 따라 달라지는 것은 스냅샷의 의도가 아니다.

**어떻게 고쳤나.** `ArrayList` → `LinkedHashSet`(입력 순서 유지)으로 바꾸고 record에 넣을 때
`List.copyOf`로 변환했다. `monthlyInterest`를 null로 접는 `contains` 판정과 `forecastDate()`가
집합에 코드를 더하는 순서(Java 인자 왼→오 평가)는 그대로다.

- `backend/src/main/java/com/ssafy/dodam/domain/improvement/service/ImprovementSnapshotReader.java`

### 5. 안 고치고 넘기는 것 — 코드가 아니라 **시연 데이터** 문제 3건

시연 서버 실호출에서 나온 것들이다. 코드는 정상이고 데이터가 비어 있다.

#### (1) U0003에 수락된 추천이 0건이라 API 19가 통째로 비어 있다

```
GET /recommendations/summary
  byStatus: {PENDING: 22, ACCEPTED: 0, REJECTED: 0, DEFERRED: 0, COMPLETED: 0}
GET /improvements/summary
  totalActions: 0, byStatus 전부 0, totalMonthlySaving: 0
```

개선 행동은 **추천 수락으로만** 생기므로 수락이 0건이면 API 19 화면이 전부 빈 상태로 보인다.
시연 전에 추천 1건 이상 수락이 필요하다. **다른 사람 계정 데이터라 내가 수락하지 않았다**
(day-12 §18에서 목표 예산값을 그대로 둔 것과 같은 이유다).

#### (2) 지금 수락해도 "전후 비교"와 "절감액"은 시연에서 못 본다 ← 이게 더 중요하다

측정 정책상 **수락한 달은 전·후 어느 쪽에도 넣지 않고**, 실행 후는 **완료된 달**이 1개월 이상 쌓여야 열린다
(`ImprovementMeasurementPolicy`). 즉 **2026-09-18에 수락하면 after 창은 2026-11-01에야 열린다.**
그 전까지는 계속 이렇게 나온다.

| 화면 | 수락 직후 값 |
| --- | --- |
| 실행 전후 비교 | `unavailableReason: AFTER_PERIOD_NOT_READY` |
| 절감/부채/소비/저축 4종 | `totalAmount: 0`, `contributingActions: 0` |
| 개선 행동 상세 | `actualMonthlySaving: null` |

"20일치 소비를 한 달치와 비교하면 반드시 개선으로 보인다"를 막는 의도된 정책이라 **코드로 완화하면 안 된다.**
시연에서 전후 비교를 보여주려면 `improvement_action.planned_at`을 **2026-07 이전으로 둔 시드**가 필요하다
(예: 7월 수락 → 8월이 완료 월이라 after 창이 열린다). 636 시드와 같은 방식으로 만들 수 있다.

#### (3) `goal_history.forecast_date`가 비어 있어 월간 목표변화의 달성일 칸이 전부 null

```
GET /reports/monthly/goal-impact
  forecastDateAtStart: null, forecastDateAtEnd: null, advancedDays: null
  (startAmount·endAmount·contributed·progressRatio는 정상)
GET /improvements/goal-impact
  currentForecastDate: "2027-07-18"   ← 지금 계산하면 값이 나온다
```

계산 자체는 된다(위 두 줄이 그 증거다). 앱이 쓰는 `GoalHistoryRecorder.record()`는 모으기 목표면
`forecast_date`를 같이 넣는데, 시연용 `goal_history` 행은 **SQL로 직접 적재돼서** 그 칸이 비어 있다.
리포트는 "오늘의 잔액이 아니라 기록된 시점 값"만 읽는 설계라(지난달을 재현하려면 그래야 한다)
과거 행을 채우지 않는 한 null이 유지된다. → 시드에 `forecast_date`를 채우면 해결된다. **API 20 결함 아님.**

### 6. 내가 시연 서버에 한 일 / 하지 않은 일

한 일 (읽기 + 부수효과 1건):
- API 19/20 조회 19종 + 음성 케이스 22종 호출
- `GET /reports/daily` 호출로 **`report` 행 1건이 새로 생성됐다**(reportId=2, period 2026-09-17).
  "끝난 기간을 조회하면 없을 때 만들어 저장한다"는 설계된 동작이고, 어차피 시연에 필요한 행이다.

하지 않은 일 (의도적으로):
- `POST /improvements/rediagnose` — 같은 날 캐시에 걸리면 무해하지만 빗나가면 진단 11번이 생기고,
  추천은 진단 10번에 묶여 있어 **발견 화면이 비어 보이는 day-12 §17과 같은 상황**이 된다. 중복 방지는
  로컬 테스트로 확인했다.
- `POST /recommendations/{id}/accept` — 5-(1). 남의 계정 상태를 바꾸지 않았다.

### 7. 참고로 남기는 관찰 2개 (조치 없음)

- **`generatedAt` 정밀도.** 리포트를 **막 만든 응답**은 엔티티의 나노초를 그대로 돌려주고
  (`2026-09-18T14:35:12.561317925`), 이후 조회는 MySQL `DATETIME`이 초로 반올림한 값을 돌려준다
  (`14:35:13`). 같은 리포트인데 첫 응답과 두 번째 응답의 `generatedAt`이 다르다. FE가 이 값을 캐시 키로
  쓰면 한 번 헛돈다. 화면 표시용으로만 쓰면 무해해서 그대로 뒀다.
- **V37 이전에 ACCEPTED된 추천에는 행동 행이 없다.** `ImprovementActionRecorder`는 COMPLETED·REJECTED를
  `ifPresent`로만 처리하므로 없는 행을 나중에 만들지 않는다(기준선을 수락 시점에 잡아야 해서 맞는 선택이다).
  현재 시연 계정은 ACCEPTED가 0건이라 해당 사항이 없다. 백필이 필요해지면 그때 마이그레이션으로 다룬다.

### 8. 결론

**완료 기준 7개 모두 충족. 빠진 엔드포인트 없음. 고친 것 3곳(추이 기준월 1건 + 스냅샷 사유 코드 1건 + 그 테스트).**
남은 것은 전부 시연 데이터이고, 그중 **5-(2) "전후 비교를 보려면 planned_at 과거 시드가 필요하다"**가
시연 준비에 제일 먼저 필요하다.
