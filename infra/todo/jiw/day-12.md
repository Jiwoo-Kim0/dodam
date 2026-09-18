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

