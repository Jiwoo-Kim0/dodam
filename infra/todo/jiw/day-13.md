# Day 13 실제 진행 기록

## 2026-09-21 — 15번 구독 seed(655·656) 적재 준비와 선행 조건 검증 (S15P21D105-475)

백엔드에서 온 요청: 시연 계정 U0001~U0004의 구독을 실제 서비스명·요금으로 다시 만드는
`DODAM_DEMO_SUBSCRIPTION_SEED.sql`(15번)을 적재하고, 그 뒤 9번 알림 seed를 재실행한다.

**현재 상태: 실행 직전까지 준비 완료, DB 쓰기는 아직 하지 않았다.** 사유는 7절.

### 1. 선행 조건 3개 — 전부 충족, 재배포 불필요

요청서의 선행 조건 1번이 "서버 backend를 develop `7420d6c`(656 머지) 이상으로 재배포해 달라"였는데,
**확인해 보니 이미 그 커밋으로 돌고 있었다.**

| 근거 | 값 |
| --- | --- |
| 서버 `~/app` HEAD | `7420d6c` (develop), 2026-09-21 00:16:18 KST |
| `infra-backend` 이미지 빌드 | 2026-09-21 00:27:24 KST — **커밋 11분 뒤** |
| `backend` 컨테이너 기동 | 2026-09-21 00:28:01 KST, `Up 9 hours (healthy)` |

시각만으로는 "빌드가 그 커밋에서 나왔다"를 단정할 수 없어서 **동작으로 한 번 더 확인**했다.

```
GET /subscriptions/candidates            → 200 (656에서 생긴 엔드포인트)
GET /subscriptions?status=ACTIVE         → 응답에 serviceCode·iconKey 키가 있다
```

`serviceCode`·`iconKey`가 지금 `null`인 것은 배포 누락이 아니라 **정상**이다. 현재 U0003의 구독은
8번이 넣은 `도담 뮤직`이고, seed 헤더가 적어 둔 대로 `"도담 ○○"`은 `SubscriptionServiceCatalog`에
매칭되는 이름이 없다. 15번을 넣어 `넷플릭스` 등으로 바뀌면 채워진다.

- 선행 조건 2 (6·8·11번 적재) — 기준선 조회에서 8번 행(`도담 ○○` 6건)과 SUB 거래 42건 확인
- 선행 조건 3 (새 마이그레이션 없음) — `flyway_schema_history`: **applied 31 / failed 0 / max version 41.00**

### 2. 파일 검증 — 서버와 로컬이 같고, day-12에서 지적한 문제가 고쳐져 있다

```
4fb586d4d19dcb0fdbd9405ce0a225a8  DODAM_DEMO_SUBSCRIPTION_SEED.sql   (로컬 = 서버 동일)
59ebc4cc255b8fb7bee680ecc5019517  DODAM_NOTIFICATION_SEED.sql        (로컬 = 서버 동일)
```

433줄 전체를 읽었다. day-11 2절·day-12 방식대로 "무엇을 바꾸는지 먼저 읽고" 시작한다.

**day-12 §18 (2)에서 넘긴 지적이 반영돼 있다.** 14번은 `START TRANSACTION` 구간 안에
`DROP TABLE`/`CREATE TABLE`이 있어 암묵적 커밋이 일어났는데, 15번은

- 스크래치 테이블 DDL: 74~205줄 → **`START TRANSACTION`(240줄) 앞**
- 트랜잭션 240~334줄: DELETE 3 + INSERT 3 뿐, DDL 없음
- 정리용 `DROP TABLE` 5개: 429~433줄 → **`COMMIT`(334줄) 뒤**

이제 트랜잭션으로 감싼 의도가 실제로 성립한다.

**삭제 범위도 좁다.** 세 DELETE 모두 `tmp_dodam_demosub_context`(시연 4명)와 조인돼 있고
`SUB-U%` / `SEED-U%-SUB-%` / `SEED-SEP-U%-SUB-%` 패턴에만 걸린다. 4명 밖으로 나가지 않는다.

**preflight가 두 겹이다.** `tmp_dodam_demosub_preflight`에 `CHECK (ready = 1)` 등 7개 제약이 걸려 있어
조건이 어긋나면 INSERT 자체가 실패하고, 그러면 테이블이 비어 모든 쓰기 문장의
`EXISTS (... ready = 1)`이 거짓이 되어 **아무것도 쓰지 않는다.**

### 3. 요청서의 확인 쿼리가 실제 스키마와 맞지 않는다 (백엔드 회신 필요)

요청서 말미에 "컬럼명 `login_id`·`status`가 다르면 알려 달라"고 되어 있어 적는다. **세 군데 다르다.**

| 요청서 | 실제 | 근거 |
| --- | --- | --- |
| `users` | **`` `user` ``** (단수, 예약어라 백틱 필요) | `V1__create_user_and_auth_session.sql:9` |
| `u.id` | **`u.user_id`** | 같은 파일 10줄 |
| `u.login_id` | **없는 컬럼** | `user`에는 `email`만 있다 |

시연 계정은 `login_id`가 아니라 **이메일**로 식별한다 —
`email = 'seed.kmeans.u000N@dodam.invalid'` (15번 seed 자신이 95~96줄에서 이렇게 조인한다).
`status`는 `subscription.status`라 요청서가 맞다.

> **seed 파일 자체는 멀쩡하다.** 틀린 것은 요청서에 딸려온 *선택* 확인 쿼리뿐이다.

내가 쓴 교정본(`demo` 컬럼은 이메일에서 잘라낸다):

```sql
SET @DEMO := NULL; -- 아래 쿼리에서 직접 IN 절로 쓴다
SELECT UPPER(SUBSTRING(u.email,13,5)) demo, COUNT(*) cnt,
       SUM(s.status='ACTIVE') active,
       SUM(CASE WHEN s.status='ACTIVE' THEN s.payment_amount ELSE 0 END) active_total
FROM subscription s JOIN `user` u ON u.user_id = s.user_id
WHERE u.email IN ('seed.kmeans.u0001@dodam.invalid','seed.kmeans.u0002@dodam.invalid',
                  'seed.kmeans.u0003@dodam.invalid','seed.kmeans.u0004@dodam.invalid')
GROUP BY demo ORDER BY demo;
```

`notification` 테이블도 본문 컬럼이 `body`가 아니라 **`message`**다(`V32__create_notification.sql:14`).
9번 재실행 결과를 확인할 때 쓸 것.

### 4. 적재 전 기준선 (2026-09-21 09:5x KST)

| 항목 | 값 |
| --- | --- |
| U0001 구독 | 1건 `도담 비디오` 12,400 (ACTIVE 1) |
| U0002 구독 | 2건 `도담 클라우드` 14,900 · `도담 리딩` 4,900 (ACTIVE 2, 19,800) |
| U0003 구독 | 1건 `도담 뮤직` 9,900 (ACTIVE 1) |
| U0004 구독 | 2건 `도담 비디오` 12,400 · `도담 리딩` 4,900 (ACTIVE 2, 17,300) |
| 다른 사용자 구독 | **5,994** (적재 후에도 이 값이 유지돼야 한다) |
| 시연 4명 SUB 거래 | 42 |
| 결제 이력 | 42 |
| 알림 전체 | 21,001 |
| 미등록 반복결제 후보 | **0건** (디즈니+ 아직 없음) |
| `tmp_dodam%` 작업 테이블 | 0개 |

8번이 "짝수 사용자 2건, 나머지 1건"을 넣는다는 seed 헤더 설명과 정확히 일치한다(6건).

### 5. 실행할 명령 (요청서 그대로, 교정 불필요)

```bash
cd ~/app
set -a; . infra/.env; set +a
cd infra
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  < ../backend/database/seeds/DODAM_DEMO_SUBSCRIPTION_SEED.sql
docker compose exec -T -e MYSQL_PWD="$MYSQL_PASSWORD" mysql \
  mysql --default-character-set=utf8mb4 -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  < ../backend/database/seeds/DODAM_NOTIFICATION_SEED.sql
```

> 스크립트로 감쌀 때 주의: `docker compose exec -T`는 **stdin을 읽는다.** 검증 스크립트를
> `ssh ... 'bash -s' < script.sh`로 흘려 넣었더니 첫 `exec`가 스크립트 나머지를 삼켜서 두 번째
> 쿼리부터 실행되지 않았다. 스크립트는 서버에 파일로 올려 `bash /tmp/x.sh`로 돌리거나,
> 쿼리용 `exec`에는 `< /dev/null`을 붙인다. seed 적재는 `< 파일`로 stdin이 이미 막혀 있어 무관하다.

### 6. 적재 후 확인할 것 (기대값)

- preflight **1 / 4 / 4 / 1 / 24 / 4 / 4** ← 실행 로그 마지막에 찍힌다. 백엔드에 보낼 값
- 구독 23건: U0001 5(54,590) · U0002 6(83,680) · U0003 7(94,490) · U0004 5(ACTIVE 4건 37,690 + 유튜브 프리미엄 PAUSED)
- SUB 거래 165 · 결제 이력 158 · 이력-거래 불일치 0 · 계약 위반 0 · 카드 불일치 0
- 다른 사용자 구독 **5,994 유지**
- U0003 미등록 `디즈니+ 9,900` 7건 (3/27~9/27)
- `tmp_dodam_demosub%` 0개
- 9번 재실행 뒤 `PAYMENT_DUE` 알림 `message`가 `도담 ○○` → 실제 서비스명으로 바뀌었는지
- API 스모크: U0003 로그인 → `/subscriptions?status=ACTIVE` 7건에 `serviceCode`·`iconKey` 채워짐,
  `/subscriptions/candidates` 디즈니+ 1건

### 7. 왜 아직 실행하지 않았나

시연 서버 DB에 쓰는 명령이 **Claude Code auto mode 분류기에 차단**됐다(`Blocked by classifier`).
day-11·day-12의 seed 적재는 통과했는데 이번에는 막혔다. 우회하지 않고 사용자 승인을 받기로 했다.

되돌리기는 요청서대로 **8번 → 11번 → 15번 → 9번** 순서다. 15번 적용 뒤 11번만 단독 재실행하면
11번 preflight가 막혀(`ready = 0`) 아무것도 쓰지 않으므로, 9월을 다시 만들 때는 반드시 이 순서를 지킨다.

## 2026-09-22 — QA 환경·기준 지표 준비 (S15P21D105-693)

부하 테스트는 **수(09-23)·토(09-26)**, 준비 창은 월·화다. 오늘이 화요일이라 남은 시간이 하루였다.

### 8. 무엇을 했나 — 지표가 아예 수집되지 않고 있었다

준비를 시작하자마자 전제가 깨져 있었다.

```
up{job="backend"} = 0        (node=1, prometheus=1)
/actuator/prometheus → 401
```

`prometheus.yml`에는 `job: backend`가 Day 6부터 있었다. **설정 파일만 보면 정상으로 보였고**,
실제로 긁히는지는 아무도 확인한 적이 없었다. 세 곳이 동시에 막혀 있었다.

| # | 막힌 곳 | 증상 |
| --- | --- | --- |
| 1 | `micrometer-registry-prometheus` 의존성 없음 | 엔드포인트 자체가 생기지 않음 |
| 2 | `management.endpoints.web.exposure.include: health` | 노출 안 됨 |
| 3 | `SecurityConfig`가 `/actuator/prometheus`를 인증 대상으로 잡음 | 401 |

즉 **CPU·메모리·DB·캐시·오류 중 호스트 CPU/메모리(node-exporter) 말고는 측정 수단이 없었다.**
이 상태로 내일 부하를 걸었으면 "느린 것 같다"는 인상 외에 남는 것이 없었을 것이다.

### 9. 결정 1 — 액추에이터 노출 방식

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **`SecurityConfig`에 인증 예외 + nginx가 외부 차단** | 변경 3줄. 외부 차단은 이미 있고 실제 응답으로 검증 가능 | **채택** |
| 별도 management 포트(`management.server.port`) 분리 | 앱 포트와 분리돼 더 깔끔하지만 compose·prometheus 타깃·헬스체크를 모두 고쳐야 한다. 하루 남은 시점에 건드릴 범위가 아니다 | 기각 |
| Basic 인증을 걸고 Prometheus에 자격증명 설정 | 더 안전하지만 `.env`에 비밀 하나가 늘고 check-env·배포 절차가 함께 는다. 컨테이너 네트워크 안에서만 닿는 엔드포인트에 비용이 과하다 | 기각 |
| nginx 차단만 믿고 Security는 그대로 | 그대로면 401이라 애초에 동작하지 않는다 | 불가 |

**채택 근거**: 외부 차단이 이미 존재하고(`location ^~ /actuator/ { return 404; }`)
**설정과 실제 응답 둘 다 확인**했다(외부에서 `/actuator/health`·`/actuator/prometheus` 모두 404).
컨테이너 네트워크 안에는 우리 컨테이너만 있다. 남은 위험 대비 변경 비용이 가장 작다.

### 10. 결정 2 — 지연을 어떻게 볼 것인가

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **히스토그램 + SLO 버킷(100ms~5s)** | `histogram_quantile`로 p95·p99를 **엔드포인트별**로 뽑을 수 있다 | **채택** |
| 기본값(평균만) | 비용 0. 그러나 **느린 소수 요청이 평균에 묻힌다.** 개선 전후 차이가 가장 크게 나는 곳이 꼬리인데 그게 안 보인다 | 기각 |
| 클라이언트 측 퍼센타일(`percentiles`) | 인스턴스별 값이라 **합산이 불가능**하다. 지금은 1대지만 비교·집계에 제약이 생긴다 | 기각 |

**채택 근거**: 부하 테스트의 목적이 "병목 원인 규명"이라 평균으로는 답이 안 나온다.
카디널리티가 늘지만 엔드포인트 수가 적어 감당 가능하다.

### 11. 결정 3 — DB·캐시 서버 지표를 볼 것인가

micrometer만으로도 **앱이 보는** DB·캐시(HikariCP 풀, Lettuce 명령 지연)는 잡힌다.
문제는 그것으로 "DB가 느리다"까지만 알고 **왜 느린지**는 모른다는 점이다.

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **exporter 2종 추가**(mysqld·redis) | 슬로우 쿼리·InnoDB 락 대기·커넥션, Redis eviction·hit rate가 보인다. 컨테이너 2개 + 전용 계정 | **채택** |
| 앱 지표만으로 진행 | 오늘 작업 0. 병목이 DB면 slow log를 사람이 직접 뒤져야 하고, 부하 중 실시간 판단이 불가능하다 | 기각 |
| 부하 중 `SHOW ENGINE INNODB STATUS`를 수동 조회 | 시점 스냅샷이라 시계열 비교가 안 되고, 전후 비교 기록으로 남기기 어렵다 | 기각 |

**채택 근거**: 완료 기준이 "병목 원인·잔여 한계 전달"이다. 원인을 못 짚으면 "최소 개선·재측정"
자체가 성립하지 않는다. 컨테이너 2개는 각 128MB 상한이라 부담이 작다.

### 12. 결정 4 — exporter가 쓸 DB 계정

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **전용 계정 + 최소 권한** | `PROCESS`, `REPLICATION CLIENT`, `performance_schema` SELECT만. **앱 데이터 SELECT 없음** | **채택** |
| 앱 계정(`MYSQL_USER`) 재사용 | 새 비밀번호가 필요 없지만, 수집기가 앱 데이터 전체를 읽을 수 있게 된다. 모니터링 컨테이너가 뚫리면 회원 데이터가 통째로 노출된다 | 기각 |
| `root` 사용 | 가장 쉬우나 최악 | 기각 |

`MAX_USER_CONNECTIONS 3`을 걸었다. **부하 중에 수집기가 커넥션을 잠식하면 측정이 측정 대상을
망가뜨린다.** 비밀번호는 서버에서 생성해 `.env`에만 두고 출력에 남기지 않았다.

### 13. 결정 5 — `MYSQL_EXPORTER_PASSWORD`를 필수로 둘 것인가

compose에 `${MYSQL_EXPORTER_PASSWORD}`(기본값 없음)로 적었다. `check-env.sh` 3번 검사가
"비어 있으면 배포 중단"으로 잡아 준다.

- `:-` 기본값을 주면 배포는 안 막히지만 **비어 있어도 조용히 지나가** exporter만 죽는다.
  모니터링이 죽은 것은 티가 안 나므로(오늘 `up=0`이 그랬다) 실패는 시끄러워야 한다.
- 값은 이미 서버 `.env`에 넣어 두었으므로 배포가 막힐 일은 없다.

OPENAI 키(`:-` 허용)와 반대로 판단했다. 그쪽은 없어도 코치가 템플릿으로 동작하지만,
여기는 **없으면 관측 자체가 사라진다.**

### 14. 곁다리로 알게 된 것 — 테스트는 메트릭을 끈다

회귀 방지 테스트를 넣었더니 운영에서는 200인 엔드포인트가 테스트에서만 404였다.
**Spring Boot는 테스트에서 메트릭 익스포터를 기본으로 끈다.** `@AutoConfigureObservability`가 필요하다.

모르고 넘어갔으면 "테스트가 실패하니 노출 설정이 잘못됐나" 쪽으로 시간을 썼을 것이다.

### 15. 산출물

**코드** (`f326203`)
- `build.gradle` micrometer-registry-prometheus / `application.yml` 노출·히스토그램 /
  `SecurityConfig` 인증 예외 / `HealthApiIntegrationTest` 회귀 테스트 1건
- 전체 1,342건 통과

**인프라** (`ee3bf10`)
- `docker-compose.yml` mysqld-exporter·redis-exporter(외부 포트 없음, 128MB, no-new-privileges)
- `prometheus.yml` job `mysql`·`redis`
- `infra/.env.example` `MYSQL_EXPORTER_PASSWORD`
- **`infra/docs/monitoring.md` 신설** — 수집 경로, 전용 계정 SQL, 기준 지표 PromQL·임계 13종, 측정 절차
- 서버: exporter 계정 생성(최소 권한 확인), `.env` 비밀번호 추가

### 16. 남은 것

- [ ] **배포 후 `up{job=...}` 3종 확인** — backend·mysql·redis가 모두 1인지
- [ ] **부하 전 기준선 스냅샷** — 지표 13종 + 배포 커밋 해시를 함께 기록(전후 비교의 '전')
- [ ] **Grafana 대시보드에 패널이 없다.** 백엔드 지표가 없던 시절 만들어진 것이라 JVM·Hikari·MySQL
      패널이 아예 없다. 부하 테스트 전에 추가하거나 Explore로 직접 쿼리한다
- [ ] **오류율과 로그를 잇는 상관 ID가 없다.** 5xx가 늘면 시간대로 Loki를 뒤져야 한다
- [ ] DB 적재와 부하 실행 시간대 분리 — seed 적재 중 측정은 결과가 오염된다(monitoring.md §4)
- [ ] 부하 생성 도구·시나리오는 215·317 담당. 이 문서는 관측 쪽만 다룬다

### 17. 배포 후 `up` 확인 — 두 가지에 걸렸다

배포 뒤 `backend = 1`로 8절의 수정은 확인됐다. **그런데 exporter 2종이 아예 뜨지 않았다.**

#### 17-1. 내 실수 — `DEPLOY_SERVICES`에 새 서비스를 넣지 않았다

`deploy.sh:24`의 서비스 목록은 **하드코딩**이다. compose에만 추가하고 이 목록을 빠뜨려
배포해도 컨테이너가 만들어지지 않았다.

**스크립트에는 이걸 잡는 경고가 이미 있었다**(176~184행).

```
WARN: '$svc' 가 compose에 있으나 배포 대상이 아닙니다 — DEPLOY_SERVICES 확인 필요
```

누군가 같은 실수를 예상하고 만들어 둔 장치인데, **내가 배포 로그를 읽지 않아 놓쳤다.**
compose에 서비스를 추가할 때 `DEPLOY_SERVICES`를 함께 보는 습관이 필요하다.

→ `deploy.sh`에 `mysqld-exporter redis-exporter` 추가. 다음 배포부터는 자동으로 뜬다.
   지금은 `docker compose up -d mysqld-exporter redis-exporter`로 두 개만 올렸다
   (다른 서비스를 건드리지 않는다).

#### 17-2. Prometheus가 옛 설정 파일을 보고 있었다 — 단일 파일 bind mount

exporter는 떴는데 `up`에 `mysql`·`redis` 잡이 나타나지 않았다. 순서대로 좁혔다.

| 확인 | 결과 |
| --- | --- |
| exporter가 지표를 내보내나 | ✅ mysql 1,136개 / redis 447개 |
| 호스트의 `prometheus.yml`에 잡이 있나 | ✅ 있음 |
| **컨테이너 안 파일에 잡이 있나** | ❌ **없음** |
| 리로드는 실행됐나 | ✅ 로그에 `Completed loading of configuration file` |

**리로드는 정상 동작했고, 읽은 파일이 옛것이었다.**

원인은 `prometheus.yml`을 **파일 하나로** bind mount하기 때문이다. `deploy.sh`가
`git reset --hard`로 파일을 바꾸면 **inode가 새로 생기는데**, 컨테이너는 기동 시점에 잡은
옛 inode를 계속 본다. 디렉터리 마운트였다면 경로를 매번 다시 찾으므로 문제가 없다.

여기에 두 번째 요인이 겹쳤다. 메인 배포 경로(`deploy.sh:275`)는

```
docker compose up -d --build --no-deps $DEPLOY_SERVICES     # --force-recreate 없음
```

**`--force-recreate`가 없다.** 서비스 정의나 이미지가 바뀌지 않으면 컨테이너를 그대로 둔다.
bind mount된 설정 파일 변경은 정의 변경이 아니므로 prometheus는 5일째 그대로 살아 있었다.
nginx는 바로 다음 줄에서 `nginx -s reload`로 따로 처리하는데 **prometheus에는 그런 단계가 없다.**

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **컨테이너 재생성**(`up -d --force-recreate prometheus`) | 즉시 해결. TSDB는 별도 볼륨이라 데이터는 남고 수집이 몇 초 끊긴다 | **이번 조치로 채택** |
| 리로드만(`POST /-/reload`) | **효과 없음.** 옛 inode를 다시 읽을 뿐이다 — 실제로 시도했고 실패했다 | 불가 |
| 마운트를 디렉터리로 바꾸기 | 근본 해결. 이후로는 리로드만으로 반영된다. compose 변경 + 배포가 필요하고 디렉터리에 파일이 `prometheus.yml` 하나뿐이라 부작용도 작다 | **후속 권장(미적용)** |
| `deploy.sh`에 prometheus 재생성 단계 추가 | nginx reload와 같은 자리. 다만 매 배포마다 수집이 끊긴다 | 보류 |

→ 재생성 후 잡 5종(`prometheus`·`node`·`backend`·`mysql`·`redis`)이 모두 `up = 1`.

**이건 앞으로도 반복된다.** 부하 테스트 중 `prometheus.yml`을 고치면(스크레이프 주기 조정 등)
리로드만으로는 반영되지 않는다. 디렉터리 마운트로 바꾸는 것을 권한다.

### 18. 기준선 스냅샷 (#1, 무부하)

`infra/scripts/metrics-snapshot.py`로 17종을 수집해 `infra/docs/load-test-records.md`에
**배포 커밋(`36d911b`)과 함께** 기록했다. 스크립트로 고정한 이유는 부하 중·개선 후에
**같은 질의·같은 시간창**으로 떠야 비교가 성립하기 때문이다.

#### 부하를 걸기 전에 이미 보이는 것 — `DB풀 max = 10`

HikariCP 기본값이다. MySQL `max_connections`는 151인데 **앱은 10개만 쓴다.**
동시 요청이 10을 넘으면 DB에 여유가 있어도 앱 쪽 풀에서 먼저 막힌다.

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **지금은 그대로 두고 부하로 재현한 뒤 바꾼다** | `pending` 상승과 p95 급등이 함께 나오면 병목이 풀이라는 **증거**가 남는다. 완료 기준이 "병목 원인 전달"이다 | **채택** |
| 미리 풀 크기를 올려 둔다 | 부하 때 문제가 안 나올 수는 있으나, **병목이 어디였는지 증명할 수 없고** 전후 비교의 '전'이 사라진다 | 기각 |

부하 중 판단 기준도 함께 적어 둔다.
- `pending` 상승 + p95 급등 → 원인은 쿼리가 아니라 **풀 크기**
- `pending` 0인데 느림 → **쿼리·인덱스**

이 구분이 "최소 개선"의 방향을 가른다.

#### 스냅샷의 성격을 문서에 못박았다

무부하 바닥값이다. HTTP 누적 415건은 대부분 헬스체크와 확인 호출이라 p95 0.06초를 부하 중
값과 직접 비교하면 안 된다. **실제 비교는 부하 중 수치끼리** 해야 한다.

#### 측정 질의 하나를 고쳤다

5xx가 0건이면 분자 시리즈가 없어 결과가 **비어 나온다**("데이터 없음").
부하 중에 **"오류 없음"과 "측정 실패"가 구분되지 않으면 위험하므로** `or vector(0)`을 붙였다.

### 19. 남은 것 (17·18절 반영)

- [x] ~~배포 후 `up` 3종 확인~~ — 17절. 잡 5종 전부 `up = 1`
- [x] ~~부하 전 기준선 스냅샷~~ — 18절, `load-test-records.md` #1
- [ ] **Grafana 대시보드에 패널이 없다** — JVM·Hikari·MySQL·Redis 패널 추가 또는 Explore 직접 질의
- [ ] **`prometheus.yml`을 디렉터리 마운트로 바꾼다**(17-2). 지금은 파일을 고쳐도 재생성 없이는 반영되지 않는다
- [ ] 오류율과 로그를 잇는 상관 ID가 없다 — 5xx 급증 시 시간대로 Loki를 뒤져야 한다
- [ ] DB 적재와 부하 실행 시간대 분리(monitoring.md §4)
- [ ] 부하 생성 도구·시나리오는 215·317 담당

### 20. Grafana 부하 테스트 대시보드

부하 중에는 여러 지표를 **동시에** 봐야 판단이 선다. Explore에서 PromQL을 하나씩 치면
"CPU는 괜찮은데 pending이 오른다" 같은 판단이 늦는다.

`infra/monitoring/grafana/provisioning/dashboards/loadtest.json` — 12패널.

| 행 | 패널 |
| --- | --- |
| 1 (판정) | 수집 대상 정상 · **DB풀 대기** · **5xx 오류율** · 지연 p95 · 처리량 |
| 2 (응답) | 지연 분위수 p50/p95/p99 · 느린 엔드포인트 top7 |
| 3 (DB) | HikariCP 풀(active/idle/pending/max) · MySQL(연결·슬로우·InnoDB 락 대기) |
| 4 (자원) | JVM 힙과 GC · 호스트 CPU/메모리 · Redis(메모리·eviction·명령) |

임계는 `monitoring.md` §3과 맞췄다(DB풀 대기 1/5, 5xx 0.5%/1%, p95 0.5s/1s).

#### 판단 기준을 패널 description에 넣었다

부하 중에 문서를 다시 열 여유는 없다. 패널 위에서 바로 읽히게 했다. 예:

> (HikariCP) active가 max에 붙고 pending이 오르면 풀 고갈이다.
> 기준선(2026-09-22) max=10은 HikariCP 기본값이며 MySQL max_connections는 151이다.

#### rate 구간을 스냅샷과 다르게 뒀다

| 용도 | 구간 | 이유 |
| --- | --- | --- |
| 대시보드 | `[1m]` | 부하 중 변화를 빨리 봐야 한다 |
| `metrics-snapshot.py` | `[5m]` | 전후 비교는 흔들림이 적어야 한다 |

**비교용 수치는 반드시 스냅샷 스크립트로 뜬다.** 구간이 다른 값을 섞어 비교하면 안 된다.

#### 곁다리로 고친 것 — `overview.json`이 backend를 빼고 보고 있었다

기존 "수집 대상 정상 여부" 패널이 `min(up{job!="backend"})`였다. 백엔드 지표가 늘 0이라
넣어 둔 우회로 보이는데, **그 결과 backend가 죽어도 패널은 초록이었다.**
8절에서 수집이 살아났으므로 제외를 해제했다(`min(up)`).

경고를 울리게 하려고 만든 패널이 **울리지 않도록 조정돼 있었다**는 점에서, 17-1의
`DEPLOY_SERVICES` 경고를 내가 읽지 않고 넘긴 것과 같은 종류의 문제다.

#### Grafana는 디렉터리 마운트라 파일만 넣으면 반영된다

`./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro` — **디렉터리** 마운트이고
provider가 30초마다 다시 읽는다(`updateIntervalSeconds: 30`). 파일을 넣고 35초 뒤
API 조회로 `uid=dodam-loadtest` 로드를 확인했다.

17-2의 prometheus(단일 파일 마운트, 재생성 필요)와 **같은 스택 안에서 동작이 다르다.**
설정을 바꿀 때 어느 쪽인지 먼저 봐야 한다.

### 21. 오늘(2026-09-22) 마무리 상태

| 항목 | 상태 |
| --- | --- |
| backend 지표 수집 | ✅ `up=1` (Day 6 이래 처음) |
| MySQL·Redis exporter | ✅ `up=1` |
| 기준 지표 정의·임계 | ✅ `monitoring.md` §3, 13종 |
| 기준선 스냅샷 | ✅ `load-test-records.md` #1 (커밋 `36d911b`) |
| 수집 스크립트 | ✅ `infra/scripts/metrics-snapshot.py` |
| 부하 대시보드 | ✅ `dodam-loadtest` |
| `deploy.sh` 서비스 목록 | ✅ exporter 2종 추가 |

**부하 테스트(수 09-23 / 토 09-26) 관측 준비는 끝났다.**

### 22. 남은 것

- [x] ~~`prometheus.yml` 디렉터리 마운트 전환~~ — 23절
- [ ] **오류율과 로그를 잇는 상관 ID가 없다.** 5xx가 늘면 시간대로 Loki를 뒤져야 한다
- [ ] **DB 적재와 부하 실행 시간대를 분리한다**(`monitoring.md` §4). seed 적재 중 측정은
      디스크·CPU가 적재에 먹혀 결과가 오염된다
- [ ] 부하 생성 도구·시나리오는 215(구동재)·317(정현진) 담당. 이 준비는 관측 쪽만 다룬다
- [ ] 부하 중/개선 후 스냅샷을 `load-test-records.md`에 **커밋 해시와 함께** 이어 적는다
- [ ] 병목 원인·잔여 한계를 PPT(315)에 전달 — 완료 기준

### 23. `prometheus.yml`을 디렉터리 마운트로 전환 (17-2 해소)

17-2에서 미룬 근본 해결. 두 가지를 함께 고쳤다.

| 원인 | 조치 |
| --- | --- |
| 단일 파일 bind mount → 컨테이너가 기동 시점 inode를 잡는다 | `./monitoring/prometheus:/etc/prometheus:ro` **디렉터리 마운트** |
| 메인 배포 경로의 `up -d`에 `--force-recreate`가 없다 | `deploy.sh`에 **prometheus reload 단계** 추가(nginx reload 바로 다음) |

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **디렉터리 마운트 + 배포 시 reload** | 경로를 매번 다시 찾으므로 파일 교체가 보인다. reload는 수집이 끊기지 않는다 | **채택** |
| `deploy.sh`에서 매번 `--force-recreate prometheus` | 확실하지만 배포마다 수집이 끊기고 기동 시간만큼 공백이 생긴다 | 기각 |
| 그대로 두고 필요할 때 수동 재생성 | 잊어버린다. 오늘 실제로 잊어서 잡이 안 뜬 것이다 | 기각 |

이미지 원본 `/etc/prometheus`에도 `prometheus.yml` 하나뿐이라(v3.1.0 확인) 디렉터리로 덮어도
가려지는 파일이 없다. 콘솔 템플릿은 `/usr/share/prometheus`에 있다.

#### 검증 — 실제로 inode를 바꿔 보고 확인했다

`os.replace`로 파일을 교체(= `git reset --hard`와 같은 방식)한 뒤 **리로드만** 했다.

```
호스트 파일          : scrape_interval: 20s
컨테이너가 보는 파일 : scrape_interval: 20s
리로드만으로 반영    : True
```

원복까지 마쳤다.

#### 첫 시도는 내 실수로 실패했다

처음엔 `sed`로 파일을 고치려 했는데 ssh 따옴표 중첩 때문에 패턴이 맞지 않아 **파일이 아예
바뀌지 않았다.** 그 상태로 리로드하니 당연히 설정이 그대로였고, 하마터면 "디렉터리 마운트도
효과 없다"는 잘못된 결론을 낼 뻔했다.

**호스트 파일과 컨테이너 파일을 둘 다 찍어 보고서야** 구분됐다. 원격에서 파일을 고칠 때는
고쳐졌는지부터 확인해야 한다 — 09-17의 `LIKE '[1-4]'`, 09-22의 컬럼명 추측과 같은 계열의 실수다.

### 24. cAdvisor 도입 시도 — 실패, 대안으로 전환

"컨테이너별로 누가 CPU를 먹는지" 보려고 cAdvisor를 넣었다가 **되돌렸다.**

#### 왜 필요했나

node-exporter는 **호스트 전체**만 본다. EC2 한 대에 MySQL·Redis·백엔드·Jenkins·Grafana가
모두 사는 구조라, 부하 중 CPU가 치솟아도 **누구 때문인지 구분할 수 없었다.**
"CPU 한계"라는 결론이 나와도 원인을 못 밝히면 개선 방향이 안 나온다.

#### 왜 실패했나

```
E manager.go:1116] Failed to create existing container: /system.slice/docker-<id>.scope:
  failed to identify the read-write layer ID ...
  open /rootfs/var/lib/docker/image/overlayfs/layerdb/mounts/<id>/mount-id: no such file
```

원인: 이 호스트의 **Docker 29.8.0이 containerd 이미지 스토어**를 쓴다(`Storage Driver: overlayfs`).
그러면 `/var/lib/docker/image/<driver>/layerdb/`가 아예 없고(실제로 `identity-cache.db` 하나뿐),
cAdvisor의 Docker 핸들러가 RW 레이어를 못 찾아 **컨테이너를 통째로 건너뛴다.**

| 시도 | 결과 |
| --- | --- |
| v0.49.1 기본 설정 | `id="/"` 루트 cgroup 1개만 |
| v0.52.1로 올림 | 동일 |
| `--containerd=/run/containerd/containerd.sock` 추가 | 동일 |
| `--docker_only=true` 제거 | systemd 서비스 47개는 잡히지만 **docker 컨테이너는 여전히 실패** |

cAdvisor 쪽 미지원 문제라 우리가 설정으로 풀 수 없다고 판단하고 중단했다.
**오늘이 부하 테스트 날이라 더 파지 않았다.**

#### 대안 — 이미 있던 지표로 대부분 해결됐다

micrometer가 **백엔드 프로세스의 CPU를 이미 내보내고 있었다.**

```
process_cpu_usage = 0.0027     (백엔드 JVM)
system_cpu_usage  = 0.0270     (호스트 전체)
→ 백엔드가 호스트 CPU 사용량의 약 10%
```

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **`process_cpu_usage`로 백엔드 몫만 분리** | 컨테이너별 세부는 없지만 **"백엔드냐 아니냐"**는 답할 수 있다. 추가 컨테이너·권한 없음 | **채택** |
| cAdvisor 계속 파기 | 상류 미지원이라 시간 대비 성공 확률이 낮다. 오늘 테스트를 못 돌린다 | 기각 |
| cgroup을 직접 읽는 수집기 자작 | 시간이 없고 유지보수 부담 | 기각 |
| Docker 스토리지 드라이버를 overlay2로 되돌림 | **위험하다.** 이미지 재빌드·재다운로드가 필요하고 시연 5일 전이다 | 기각 |

→ 대시보드 `호스트 CPU·메모리` 패널에 **백엔드 프로세스 CPU** 선을 추가했다.

**읽는 법**: 호스트 CPU는 높은데 백엔드 몫이 낮으면 → 범인은 MySQL·Jenkins 등 **다른 컨테이너**다
(앱을 고쳐도 소용없다). 둘이 같이 오르면 → **백엔드가 원인**이다.

#### 남는 한계 (→ 25절에서 해소됨)

컨테이너별 **메모리·디스크 I/O**는 여전히 볼 수 없다. CPU만 백엔드 몫이 분리된다.
MySQL이 메모리를 얼마나 쓰는지는 `mysql_global_status_*`로 간접 추정해야 한다.
이 점을 잔여 한계로 기록해 둔다.

#### 부수 확인 — 어제 고친 디렉터리 마운트가 실제로 동작했다

cAdvisor 잡을 추가할 때 **`prometheus.yml`을 고치고 리로드만으로 새 잡이 잡혔다**(재생성 불필요).
23절에서 임시 파일로 검증했던 것이 실제 작업에서 확인된 셈이다.

### 25. cAdvisor 재도입 성공 — 실패 원인은 `docker.sock`이었다

24절에서 철회했으나, **자원 영향이 작다면 넣자**는 판단으로 한 번 더 시도해 성공했다.

#### 결정적 단서

cAdvisor는 `docker.sock`이 보이면 **Docker 핸들러**로 컨테이너를 처리한다. 그런데 이 호스트는
containerd 이미지 스토어라 RW 레이어를 못 찾아 **컨테이너를 통째로 포기**했다(24절).

**소켓을 마운트하지 않으면** Docker 핸들러가 등록되지 않고 **raw cgroup 핸들러**로 떨어진다.
그러면 `/system.slice/docker-<id>.scope` cgroup을 그대로 읽어 정상 수집된다.

```
docker.sock 있음 → 컨테이너 0개 (RW 레이어 오류 반복)
docker.sock 없음 → 컨테이너 14개, 오류 0건
```

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **docker.sock 없이 raw cgroup 수집** | 컨테이너별 CPU·메모리를 얻는다. **소켓을 안 쓰니 보안도 더 낫다**(docker.sock은 사실상 호스트 root 권한) | **채택** |
| docker.sock 마운트 | 컨테이너 **이름**을 얻지만 이 호스트에서는 수집 자체가 안 된다 | 불가 |
| 포기하고 `process_cpu_usage`만 | 백엔드 몫만 알 수 있고 MySQL·Jenkins는 여전히 깜깜 | 24절의 임시안 |

**대가**: 컨테이너 이름을 모른다. cgroup 경로에서 12자리 짧은 ID를 뽑아 `container_id` 라벨로 넣었고,
`docker ps`의 CONTAINER ID와 같은 값이라 대조할 수 있다.

#### 자원 영향 — 실측했다

"다른 컨테이너에 영향이 없어야 넣는다"가 조건이었으므로 먼저 쟀다.

| 컨테이너 | CPU | 메모리 |
| --- | --- | --- |
| **cadvisor** | **0.25~0.9%** | **15MB** (한도 256MB) |
| redis-exporter | 1.46% | 10.9MB |
| prometheus | 1.28% | 78MB |

`--housekeeping_interval=10s`와 불필요 지표 비활성(`percpu`, `sched`, `tcp`, `process` 등)을 걸어
기본 설정보다 크게 낮췄다. **측정기가 측정 대상을 방해하면 안 된다**는 원칙은 mysqld-exporter의
`MAX_USER_CONNECTIONS 3`과 같다.

#### 첫 수집에서 바로 보인 것

```
jenkins   1,588 MB   ← 놀면서 가장 많이 쥐고 있다
mysql     1,054 MB
backend     551 MB
prometheus/grafana/loki  88 / 80 / 64 MB
```

부하 중 메모리가 빠듯해지면 **Jenkins가 1순위 후보**다. 지금은 호스트 메모리 여유가 72%라 문제없다.

#### 이 작업에서 내가 낸 실수 — `prometheus.yml` 구조를 깼다

cadvisor 잡을 redis 잡 뒤에 붙인다는 게, redis 잡의 `labels:` 블록 **앞에** 끼워 넣어
YAML이 깨졌다. 앵커 문자열이 `- targets: [...]` 줄까지만 매칭해서 그 아래 딸린 두 줄과 분리된 것이다.

```
line 77: field labels not found in type relabel.plain
```

**여러 줄 블록에 텍스트를 삽입할 때는 블록의 끝까지 앵커로 잡아야 한다.**
커밋된 원본을 `git show`로 꺼내 다시 만들고 파일 끝에 append하는 방식으로 고쳤다.

#### 그 덕분에 배포 스크립트 버그를 찾았다

깨진 설정으로 리로드했는데 **"리로드 OK"로 보였다.** 확인해보니 **busybox wget은 HTTP 500에도
종료코드 0을 반환**한다. 23절에서 내가 `deploy.sh`에 넣은 리로드 단계가

```sh
docker exec prometheus wget -q ... /-/reload >/dev/null 2>&1 || log "리로드 생략"
```

이 형태라 **설정 오류를 전혀 잡지 못하고 있었다.** 배포는 성공으로 끝나고 prometheus는
옛 설정으로 계속 도는, 조용히 틀리는 종류의 실패다.

→ 응답 코드를 직접 검사하도록 바꾸고, 실패 시 확인 명령까지 로그에 남기게 했다.

```sh
if docker exec prometheus wget -S --post-data="" -O- .../-/reload 2>&1 | grep -q "HTTP/1.1 200"; then
```

**23절에서 만든 장치를 오늘 내 실수로 검증한 셈이다.** 내가 깨뜨리지 않았으면
부하 테스트 중에 설정을 바꿨을 때 처음 드러났을 것이다.

#### 산출물

- `docker-compose.yml` cadvisor(v0.52.1, docker.sock 없음, mem 256m)
- `prometheus.yml` job cadvisor + `metric_relabel_configs`(container_id 추출, systemd 시리즈 제외)
- `deploy.sh` DEPLOY_SERVICES에 cadvisor, 리로드 응답코드 검사
- `loadtest.json` 컨테이너별 CPU·메모리 패널 2개(총 14패널)

### 26. k6 스크립트에 `THINK_TIME` 옵션 추가 — 한계 탐색이 가능해졌다

#### 문제 — 기존 설정으로는 병목이 드러나지 않는다

`backend/scripts/loadtest/user-journey.js`는 잘 만들어져 있었다. 로그인을 `setup()`에서만 해
`api_auth`(분당 20건)를 피하고, 429를 `rate_limited`로 분리하며, GET만 호출해 운영 DB를 건드리지 않는다.

문제는 **부하의 세기**였다.

```
1회 journey = 요청 7건 + 대기 약 10초  →  VU 1명당 0.7 req/s
VU 50명 = 약 35 req/s  →  동시 요청 약 1.4개
DB 커넥션 풀 = 10  →  1.4개만 쓰니 대기가 생길 수 없다
```

실사용을 흉내 낸 `randomSleep`이 원인이다. 의도는 맞지만, **이 설정으로는 아무리 오래 돌려도
커넥션 풀에 닿지 못해** 완료 기준인 "병목 원인 규명"을 할 수 없다.

#### 방안 비교

| 방안 | 검토 | 채택 |
| --- | --- | --- |
| **대기 시간을 환경변수 배율로** | 기본값은 현재 동작 그대로. `THINK_TIME=0`이면 한계 탐색. 스크립트 구조를 안 건드린다 | **채택** |
| VU를 대폭 늘림(500+) | 대기가 그대로라 k6 실행 머신의 부담만 커진다. VU 700쯤 필요 | 기각 |
| `randomSleep` 호출을 삭제 | 실사용 프로필을 잃는다. 둘 다 필요하다 | 기각 |
| 별도 `break` 프로필 신설 | `PROFILE`과 `THINK_TIME`이 직교하는 개념인데 섞이면 조합이 막힌다 | 기각 |

#### 구현

```js
const THINK_TIME = Math.max(0, parseFloat(__ENV.THINK_TIME ?? '1') || 0);

function randomSleep(min, max) {
    if (THINK_TIME === 0) return;   // sleep(0)도 이터레이션을 양보시키므로 호출 자체를 건너뛴다
    sleep((min + Math.random() * (max - min)) * THINK_TIME);
}
```

`setup()` 로그에 `LOAD_VUS`·`THINK_TIME`을 함께 찍게 했다.
**어떤 조건에서 잰 수치인지가 결과와 같이 남아야** 비교가 성립한다.

#### 실측 — 1 VU로 검증했다

| | `THINK_TIME=1`(기본) | `THINK_TIME=0` |
| --- | --- | --- |
| 이터레이션 | 약 11,000ms | **98.8ms** |
| VU 1명당 | 0.7 req/s | **62 req/s** |
| 체크 | 통과 | 통과(26건 실패 0) |

**VU 1명이 62 req/s**를 만든다. 50 VU면 동시 요청이 40개대가 되어 **풀 10개는 확실히 포화된다.**

백엔드 직결 시 엔드포인트별 지연(참고): `loans` 7ms · `card_summary` 9ms · `simulations` 11ms ·
`recommendation_summary` 16ms · **`diagnoses` 22ms**(가장 느림).

#### 실행 방법 세 가지를 헤더에 적었다

1. **`verify`** — 경로·계정 확인. 1 VU × 3회
2. **실사용 부하** — Nginx 경유, `LOAD_VUS=25`(20r/s 제한을 넘지 않는 선)
3. **한계 탐색** — k6를 컨테이너로 `app-net`에 붙여 `backend:8080` 직결 + `THINK_TIME=0`

```bash
docker run --rm -i --network infra_app-net \
  -e BASE_URL=http://backend:8080/api -e PROFILE=load -e LOAD_VUS=50 -e THINK_TIME=0 \
  -v "$PWD/backend/scripts/loadtest:/work" -w /work grafana/k6:latest run user-journey.js
```

**3번에서는 응답시간 임계(p95<800 등)가 실패하는 것이 정상이다.** 그 실패 지점이 찾으려던 한계다.

---

### 2026-09-23 · 상용 수준 부하 지표 재점검 — 톰캣 스레드 누락 발견

#### 무엇을 했나

부하 테스트를 돌리기 전에 "상용 앱이 보는 지표가 내 쪽에 다 준비됐는지" 데모 서버에서
직접 쿼리해 확인했다. Prometheus 수집 대상 6개(backend·cadvisor·mysql·node·prometheus·redis)는
전부 `up=1`. 상용 기준 10개 지표 중 **9개는 나오고 1개가 비어 있었다.**

```
처리량(RPS)              0.3777777777
지연 p95                 0.0207851062
DB풀 대기                0
톰캣 사용 스레드          없음          ← 여기
MySQL 실행중 쿼리         2
InnoDB 락 대기            0
Redis 캐시 적중           845
컨테이너별 메모리(개수)    14
컨테이너별 디스크I/O(개수) 14
백엔드 CPU 몫             0.0011752854
```

백엔드가 실제로 내보내는 tomcat 지표를 전부 뽑아 보니 **세션 계열뿐**이었다.

```
tomcat_sessions_active_current_sessions   tomcat_sessions_active_max_sessions
tomcat_sessions_alive_max_seconds         tomcat_sessions_created_sessions_total
tomcat_sessions_expired_sessions_total    tomcat_sessions_rejected_sessions_total
```

`tomcat_threads_busy_threads` · `tomcat_threads_config_max_threads`가 **아예 없다.**
Micrometer의 `TomcatMetrics`는 스레드 풀 값을 JMX MBean에서 읽는데,
Spring Boot는 `server.tomcat.mbeanregistry.enabled`가 기본 `false`라 MBean을 등록하지 않는다.
세션 지표는 MBean 없이도 읽히기 때문에 **"tomcat 지표가 나오긴 하네"로 착각하기 쉬웠다.**

왜 이게 빠지면 안 되는가 — 부하 테스트의 결론이 갈리는 지점이기 때문이다.
지금 구조는 **톰캣 스레드 200 대 DB 풀 10**이다. 지연이 올랐을 때

- `busy` 높음 + `pending` 높음 → 스레드가 **DB를 기다리는 중**. 병목은 DB 풀
- `busy` 높음 + `pending` 0 → 병목은 **애플리케이션 처리 자체**

`busy`가 없으면 이 둘을 구분할 수 없고, `pending`만 보고 무조건 DB 풀을 키우는
잘못된 개선으로 이어진다. 병목 원인을 PPT 315에 넘겨야 하므로 추측이 아니라 근거가 필요했다.

**변경 4건**

| 파일 | 변경 |
| --- | --- |
| `backend/src/main/resources/application.yml` | `server.tomcat.mbeanregistry.enabled: true` (신규 `server:` 블록) |
| `infra/monitoring/grafana/provisioning/dashboards/loadtest.json` | `톰캣 요청 스레드 (포화도)` 패널. 3행(DB) 바로 아래 전폭 배치, 아래 패널들은 y+8 |
| `infra/scripts/metrics-snapshot.py` | `톰캣 busy 스레드` · `톰캣 스레드 max` 2행 추가 |
| `infra/docs/dashboard-guide.md` | `## 5. 4행 — 요청 스레드가 찼는가` 신설, 진단 순서에 `[3-1]` 분기 추가, 이후 섹션 번호 +1 |

#### 다른 방안

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **`server.tomcat.mbeanregistry.enabled: true`** | 설정 한 줄. Micrometer가 이미 붙여 둔 `TomcatMetrics`가 그대로 동작한다. MBean 등록 비용은 기동 시 1회 | **채택** |
| 스레드 지표를 포기하고 `pending`만으로 판단 | 추가 작업 0. 그러나 위 표의 두 경우를 구분 못 해 **잘못된 개선을 유도**한다 | 기각 |
| k6의 VU 수·요청 동시성으로 대신 추정 | 클라이언트 관점 숫자라 **서버가 실제로 몇 개를 붙잡고 있는지**는 모른다. nginx 429로 걸러진 요청도 안 보인다 | 기각 |
| JMX exporter 컨테이너를 따로 띄워 수집 | 톰캣뿐 아니라 JVM MBean 전반을 볼 수 있다. 대신 컨테이너·포트·설정 파일이 늘고, 지금 필요한 건 지표 2개뿐 | 기각(과함) |
| 톰캣 `max-threads`를 DB 풀에 맞춰 10~20으로 줄이기 | 스레드가 차는 모습을 눈으로 보기는 쉬워진다. 그러나 **부하 특성 자체를 바꾸는 것**이라 측정이 아니라 조작이 된다 | 기각 |

#### 선택 이유

설정 한 줄로 **없던 지표가 생기는** 경우다. 비용은 기동 시 MBean 등록뿐이고 요청 경로에
오버헤드가 없다. 반대로 이걸 빼면 부하 테스트의 핵심 질문("스레드가 찼나, DB를 기다리나")에
답할 수 없어 결과물의 신뢰도가 떨어진다. 감수한 것은 **재배포가 필요하다는 점** —
인프라 쪽 파일만 고치면 Prometheus reload로 끝나지만, 이건 애플리케이션 설정이라
배포 전까지 패널이 "데이터 없음"으로 뜬다. 그래서 패널 설명과 가이드 양쪽에
"설정이 있어야 수집된다"는 문구를 박아 뒀다 — 배포 전에 패널을 보고
"부하가 없어서 비었나 보다"로 오해하는 것을 막기 위해서다.

#### 곁다리로 알게 된 것

- **Micrometer의 tomcat 지표는 두 종류의 출처를 섞어 쓴다.** 세션은 `Manager` 객체에서 직접,
  스레드·커넥션은 JMX MBean에서 읽는다. 그래서 MBean이 꺼져 있으면 **일부만 나온다.**
  "지표 이름에 tomcat이 있으니 수집되고 있다"는 판단이 틀릴 수 있다.
- 지표 존재 여부는 값 쿼리가 아니라 **이름 목록**으로 확인해야 한다. 값 쿼리는
  "0이라서 비었는지" "시리즈가 없어서 비었는지" 구분이 안 된다.

#### 내가 틀린 것

처음 점검에서 `톰캣 사용 스레드: 없음`을 보고 **"트래픽이 없어서 0"**으로 넘어갈 뻔했다.
데모 서버는 실제로 거의 유휴 상태라 그 해석이 그럴듯했다. 지표 이름 목록을 따로 뽑고 나서야
**시리즈 자체가 없다**는 걸 알았다. 스냅샷·가이드에 경고 문구를 넣은 이유가 이것이다.

#### 남은 것

- [ ] **재배포 후 `tomcat_threads_busy_threads` 수집 확인** — 애플리케이션 설정이라 배포 필요
- [ ] 배포 후 기준선 스냅샷 재측정(스레드 2행이 포함된 판으로)
- [ ] k6 결과를 Prometheus로 내보내기(`--out experimental-prometheus-rw`) — 클라이언트 지표와 서버 지표를 한 화면에서 비교
- [ ] nginx exporter — 429로 걸러진 요청이 서버 쪽 지표에 안 잡힌다
- [ ] 215·317과 DB 적재/실행 시간대 분리 합의

#### 산출물

- `backend/src/main/resources/application.yml`
- `infra/monitoring/grafana/provisioning/dashboards/loadtest.json`
- `infra/scripts/metrics-snapshot.py`
- `infra/docs/dashboard-guide.md`

#### 배포 후 검증 (2026-09-23 13:41 KST · 서버 커밋 `8f52cd0`)

`server.tomcat.mbeanregistry.enabled: true` 반영 후 데모 서버에서 확인했다.
**목표 지표 3개가 모두 수집된다.**

```
sum(tomcat_threads_busy_threads)        1     ← Prometheus 스크레이프 요청 자체
sum(tomcat_threads_current_threads)     10    ← 만들어 둔 스레드
max(tomcat_threads_config_max_threads)  200   ← 상한(톰캣 기본값 확인)
max(hikaricp_connections_max)           10    ← 비교용. 스레드 200 대 풀 10 구조 확정
```

스냅샷·Grafana 패널 반영도 확인했다.

- `metrics-snapshot.py` 실행 시 `톰캣 busy 스레드 1` · `톰캣 스레드 max 200` 정상 출력
- Grafana 컨테이너 안 `/etc/grafana/provisioning/dashboards/loadtest.json`에
  `톰캣 요청 스레드 (포화도)` 패널 존재

#### 예상보다 많이 얻었다 — MBean 하나로 지표 3계열이 함께 열렸다

설정 한 줄로 **스레드 3개만** 생길 줄 알았는데, MBean 기반 지표가 통째로 열렸다.

| 계열 | 지표 | 쓸모 |
| --- | --- | --- |
| threads | `busy` · `current` · `config_max` | 목표였던 것. 스레드 포화 판정 |
| connections | `current_connections` · `keepalive_current_connections` · `config_max_connections`(**8192**) | 연결은 맺었지만 처리 대기 중인 요청 수. 스레드와 구분된다 |
| global/servlet | `request_seconds_*` · `error_total` · `received/sent_bytes_total` | **Spring 필터 바깥**까지 포함한 서버 관점 지연·에러·전송량 |

특히 `tomcat_global_request_seconds_*`는 기존 `http_server_requests_seconds_*`(Spring MVC 계층)보다
**바깥쪽**을 잰다. 둘의 차이가 벌어지면 지연이 컨트롤러가 아니라
**필터·인증·요청 파싱 단계**에서 생긴다는 뜻이라 원인 구간을 한 번 더 좁힐 수 있다.

`config_max_connections=8192`를 확인하면서 **요청이 통과하는 문이 4단계**라는 게 명확해졌다.

```
nginx 20 req/s → 톰캣 커넥션 8192 → 톰캣 스레드 200 → DB 풀 10
```

뒤로 갈수록 좁아지고, **병목은 항상 가장 좁은 문**이다. 이 그림을 `dashboard-guide.md`
4행 절에 추가했다 — 커넥션이 높은데 busy가 낮은 상황을 문제로 오해하지 않게 하기 위해서다.

#### 남은 것 갱신

- [x] ~~재배포 후 `tomcat_threads_busy_threads` 수집 확인~~ → 완료
- [ ] 부하 테스트 직전 기준선 스냅샷 재측정(유휴 상태 기준선은 위 13:41 판으로 확보)
- [ ] `tomcat_global_request_seconds`와 `http_server_requests_seconds` 차이를 볼 패널 추가 검토
- [ ] k6 → Prometheus 내보내기 / nginx exporter / 215·317 시간대 분리 (기존 유지)

#### 기준선 스냅샷 #3 확보 (13:48 KST)

톰캣 스레드가 포함된 첫 유휴 기준선을 `docs/load-test-records.md`에 `#3`으로 남겼다.
부하 중 수치는 #2가 아니라 **#3과 비교**한다(배포 버전이 다르다).

스냅샷 도구에 없는 값도 함께 떠 뒀다 — `current_threads=10`,
`connections_current=2`, `connections_max=8192`, `global_error_total=0`,
그리고 **톰캣 평균 7.184ms vs Spring MVC 평균 6.908ms**.
이 **차이 0.28ms**가 지금의 필터·인증·파싱 오버헤드이며,
부하 중 이 간격이 벌어지면 병목이 컨트롤러 바깥에 있다는 신호가 된다.
유휴 상태에서 재어 둬야 의미가 있어서 부하 전에 확보했다.

수치가 #2와 달라진 것 세 가지는 **개선이 아니라 측정 조건 차이**라 명시해 뒀다.
p95 42ms→13ms는 #2가 재배포 직후 JIT 워밍업 구간이었기 때문,
요청 누적 349→81은 재시작으로 카운터가 리셋된 것, heap 77→121MB는 GC 전 자연 증가분이다.
**기준선끼리 비교해 "빨라졌다"고 쓰면 안 된다**는 점을 기록에 박아 뒀다.

---

### 2026-09-23 · 부하 테스트 계정 파일(`users.json`) 채우기

#### 무엇을 했나

부하 테스트 실행 준비를 점검하다 `backend/scripts/loadtest/data/users.json`이
**없다는 것**을 발견했다. `users.example.json`만 있는 상태라 스크립트가 시작 즉시 throw한다.

비밀번호는 `backend/database/seeds/README.md` "시연 계정 로그인" 절에 있었다 —
`DODAM_DEMO_PASSWORD_SEED.sql`(10번)이 U0001~U0004에만 BCrypt 해시를 넣고 평문은 `dodam1234`다.

**먼저 4개 계정이 실제로 로그인되는지 확인**하고(파일부터 만들면 실패 원인이
계정인지 파일인지 구분이 안 된다) 파일을 만들었다.

```
U0001  HTTP=200  토큰 있음      U0003  HTTP=200  토큰 있음
U0002  HTTP=200  토큰 있음      U0004  HTTP=200  토큰 있음
```

로컬과 서버(`/home/ubuntu/app/backend/scripts/loadtest/data/`) 양쪽에 두었다.
직결 실행은 서버에서 도커로 돌리므로 서버에도 필요하다.
`.gitignore`에 `data/users.json`이 있어 커밋되지 않는 것을 `git check-ignore`로 확인했다.

#### 다른 방안

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **`users.json`에 U0001~U0004 4개 계정** | 스크립트가 VU를 계정에 분산시킨다. 로그인은 setup에서 4회뿐이라 20r/m 제한에 안 걸린다 | **채택** |
| `DEMO_EMAIL`/`DEMO_PASSWORD` 환경변수(계정 1개) | 파일을 안 만들어도 된다. 그러나 **VU 전원이 같은 토큰·같은 사용자 데이터**를 조회해 DB 버퍼풀·캐시에 유리하게 편향된다. 실제보다 좋은 수치가 나온다 | 기각 |
| 부하 테스트 전용 계정을 새로 만들기 | 시연 데이터와 분리된다. 그러나 계정마다 계좌·카드·대출·진단 시드를 새로 넣어야 하고, **데이터 양이 다르면 지연도 달라져** 시연 환경 측정이 아니게 된다 | 기각 |
| 18개 seed 사용자 전부 사용 | VU 분산이 넓어진다. 그러나 U0005 이후는 `password_hash`가 NULL이라 로그인 자체가 안 된다 | 기각(불가) |

#### 선택 이유

**측정하려는 것이 시연 환경의 성능**이므로 시연 계정을 그대로 쓴다.
계정 4개면 VU가 4개 데이터셋에 분산돼 단일 계정보다 캐시 편향이 덜하고,
로그인 4회는 `20r/m` 제한에 여유 있게 들어간다. 감수한 것은 **분산이 4갈래뿐**이라는 점 —
실사용자 수백 명이 제각기 다른 데이터를 읽는 것보다는 캐시 적중이 높게 나온다.
이 한계는 결과 해석 시 "잔여 한계"로 적는다.

#### 내가 틀린 것 — 파일을 만들었는데도 "파일이 없다"고 나왔다

서버에 `users.json`을 `chmod 600`으로 두고 verify를 돌렸더니 이 에러가 났다.

```
Error: data/users.json이 없고 DEMO_EMAIL/DEMO_PASSWORD도 없습니다.
```

`ls -l`로 파일이 분명히 있는데 없다고 한다. 원인은 **uid 불일치**였다.

```
k6 컨테이너 기본 uid: 12345  /  호스트 ubuntu uid: 1000
```

`600`은 소유자만 읽을 수 있는데 컨테이너는 uid 12345로 돈다. k6의 `open()`은
읽기 실패와 파일 없음을 구분하지 않고 같은 예외로 던지므로
**권한 문제가 "파일 없음"으로 위장**된다. 파일을 다시 만들어 보는 헛수고를 할 뻔했다.

`--user "$(id -u):$(id -g)"`로 컨테이너를 호스트 uid로 돌려 해결했다.
권한을 644로 푸는 방법도 있었지만, 자격증명 파일의 권한을 낮추는 것보다
**실행 쪽을 맞추는 편**이 낫다고 판단했다. 이 함정을 스크립트 헤더에 박아 뒀다.

#### verify 통과 (30초)

```
journeys_completed  3        journey_failed  0.00%
http_reqs           32       http_req_failed 0.00%
http_req_duration   avg=31.78ms  p(95)=107.45ms
  cards 37.15ms · diagnoses 23.75ms · recommendation_summary 22.36ms
  simulations 19.17ms · loans 16.65ms · card_summary 15.33ms · account_summary 15.12ms
iteration_duration  avg=9.77s   ← THINK_TIME=1 기준 한 사이클
```

**iteration 9.77초**가 실측됐다. 요청 7건 + 대기 약 10초 구조가 확인된다.
`load` 프로필은 계단 duration 합이 고정이라 총 21분 + graceful 30초 ≈ **22분**이다.

#### 남은 것

- [x] ~~`users.json` 생성~~ → 로컬·서버 양쪽 완료, verify 통과
- [ ] 한계 탐색용 짧은 프로필 — 현재 `verify`(1 VU)와 `load`(22분) 사이가 비어 있다.
      DB 풀이 10이라 한계는 2~3분이면 드러나는데 매번 22분을 쓰는 건 낭비다
- [ ] 부하 측정 실행 (기준선 #3과 비교)

#### 산출물

- `backend/scripts/loadtest/data/users.json` (gitignore, 커밋 안 됨)
- `backend/scripts/loadtest/user-journey.js` — 헤더에 `--user` 함정과 계정 선택 이유 추가

---

### 2026-09-23 · 짧은 한계 탐색 프로필(`probe`) 추가

#### 무엇을 했나

프로필이 `verify`(1 VU 고정, 30초)와 `load`(22분) 둘뿐이라 **중간이 비어 있었다.**
DB 커넥션 풀이 10이라 포화는 수십 초면 드러나는데, 한 번 확인하려고 매번 22분을
쓰는 것은 개선-재측정 사이클(1시간 10분)을 통째로 늘리는 비용이다.

`probe` 프로필을 추가했다. **총 4분 20초.**

```
20s→5   40s 유지     ← 풀(10)보다 작다. 여기서 pending이 오르면 풀 문제가 아니다
20s→10  40s 유지     ← 풀 크기와 같다
20s→20  40s 유지     ← 풀의 2배
20s→40  40s 유지     ← 풀의 4배. PROBE_VUS로 조절
20s→0
```

계단을 두 배씩 올리고 **풀 크기(10)를 가운데** 두어 그 앞뒤에서 pending이 언제
생기는지 끼워 볼 수 있게 했다. 한 계단을 1분으로 잡은 것은 Prometheus scrape이
15초 간격이라 유지 구간에서 표본이 2~3개는 나와야 하기 때문이다.

함께 바꾼 것 두 가지.

- **`THINK_TIME` 기본값을 프로필별로** — probe는 0, 나머지는 1.
  한계를 재는데 대기가 있으면 VU를 올려도 동시 요청이 늘지 않아 풀에 닿지 못한다.
  매번 `-e THINK_TIME=0`을 붙이는 것은 빠뜨리기 쉽고, 빠뜨리면 **조용히 잘못된 결과**가 나온다.
- **임계를 프로필별로 분리(`buildThresholds()`)** — 기존에는 `options.thresholds`가 고정이라
  probe에서도 `p(95)<800` 같은 판정 기준이 적용됐다. probe는 임계를 넘기는 것이 목적이므로
  그대로 두면 **정상 동작이 실패(종료 코드 1)로 보고**된다.

```
probe   http_req_failed  rate<0.05  abortOnFail, delayAbortEval 30s
        http_req_duration p(95)<3000 abortOnFail, delayAbortEval 30s
verify  지연 임계 없음 (경로·형식 확인용)
load    기존 12개 그대로 (엔드포인트별 8개 포함)
```

probe의 임계는 **판정이 아니라 중단 조건**이다. 한계를 넘은 뒤로는 더 돌려도 얻을 것이
없으므로 `abortOnFail`로 즉시 끊는다. 실제로는 4분 20초보다 더 일찍 끝날 수 있다.

#### 다른 방안

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **`ramping-vus` 짧은 계단(5·10·20·40)** | 기존 `load`와 같은 executor라 결과를 나란히 읽을 수 있다. 계단이 있어 "몇 VU부터 꺾이는지"를 말할 수 있다 | **채택** |
| `ramping-arrival-rate`(open model) | 상용 한계 탐색의 정석이다. VU 방식은 서버가 느려지면 부하도 같이 줄어(closed model) 포화를 과소평가한다. 그러나 이 스크립트는 iteration이 **요청 9건 + sleep** 묶음이라 목표 RPS를 직관적으로 정하기 어렵고, `preAllocatedVUs`를 잘못 잡으면 VU 부족 경고가 결과를 오염시킨다. 지금 병목(풀 10)은 closed model로도 충분히 드러난다 | 기각(추후 재검토) |
| `load`의 stage 시간을 환경변수 배율로 축소 | 프로필을 안 늘려도 된다. 그러나 `load`는 **오래 버티는지**(커넥션 누수·GC 누적)를 보는 구성인데 배율로 줄이면 그 의미가 사라진다. 하나의 이름이 두 목적을 겸하면 **기록에서 어느 쪽이었는지 구분되지 않는다** | 기각 |
| 계단 없이 고정 VU로 한 판 | 가장 짧다. 그러나 꺾이는 지점을 모른다. "40 VU에서 느렸다"까지만 알 뿐 "12 VU부터 pending이 올랐다"를 말할 수 없다 | 기각 |
| 마지막 계단을 100 VU로 | 여유 있게 한계를 본다. 그러나 풀이 10인데 100은 과하고 계단이 하나 더 늘어 시간만 길어진다. 40에서 안 나오면 `PROBE_VUS`로 올린다 | 기각 |

#### 선택 이유

**시행착오 비용을 줄이는 것이 목적**이므로, 정확도를 조금 내주더라도 짧은 쪽을 택했다.
`arrival-rate`가 이론적으로 더 맞지만 지금 찾는 답("풀 10이 병목인가")은
closed model로도 나온다. 감수한 것은 **한계치의 절대값이 과소평가될 수 있다는 점** —
probe로 병목 위치를 찾고, 숫자로 보고할 값은 `load`에서 얻는다는 역할 분담으로 정리했다.

`abortOnFail`을 넣은 이유는 probe가 "무너지는 지점"을 찾는 도구이기 때문이다.
무너진 뒤에 계속 던지면 **서버 복구만 늦어지고 얻는 정보는 없다.**
`delayAbortEval: 30s`로 초기 워밍업 구간에서 성급히 끊기는 것은 막았다.

#### 곁다리로 알게 된 것 — 짧은 계단이 대시보드에서 보이느냐

처음에는 1분짜리 계단이 Grafana에서 안 보일까 걱정했다. 대시보드가 `rate(...[5m])`를
쓰기 때문에 1분 변화는 평활화된다. 그런데 **핵심 지표 두 개는 gauge라 무관하다.**

| 지표 | 종류 | 1분 계단에서 |
| --- | --- | --- |
| `hikaricp_connections_pending` · `active` | **gauge** | 그대로 보인다 |
| `tomcat_threads_busy_threads` | **gauge** | 그대로 보인다 |
| 처리량 · 5xx율 · 지연 p95 | rate/histogram | 흐려진다 → **k6 출력 숫자**를 쓴다 |

즉 probe는 **서버 측 포화는 Grafana로, 클라이언트 측 지연은 k6 요약으로** 읽는다.
이 역할 분담을 스크립트 헤더에 적었다. 대시보드의 rate 창을 `$__rate_interval`로
바꾸는 방법도 있지만, `load` 판독에도 영향을 주므로 지금은 건드리지 않았다.

#### 검증

`k6 inspect`로 세 프로필의 시나리오와 임계를 확인했다(실행은 아직 안 했다).

```
verify  per-vu-iterations  vus=1 iterations=3   thresholds: checks·http_req_failed·journey_failed
probe   ramping-vus        4분 20초             thresholds: http_req_duration·http_req_failed
        20s->5 40s->5 20s->10 40s->10 20s->20 40s->20 20s->40 40s->40 20s->0
load    ramping-vus        21분(기존 그대로)    thresholds 12개(엔드포인트별 8개 유지)
```

`load`의 계단과 임계가 **바뀌지 않은 것**을 특히 확인했다 — 임계를 함수로 옮기면서
기존 판정 기준이 틀어지면 이전 측정과 비교가 불가능해진다.

#### 남은 것

- [ ] probe 실행 (시연 시간대와 분리해서)
- [ ] `ramping-arrival-rate` 재검토 — probe로 병목을 찾은 뒤, 한계치 숫자가 필요하면
- [ ] 대시보드 rate 창 `$__rate_interval` 전환 검토

#### 산출물

- `backend/scripts/loadtest/user-journey.js` — `probe` 시나리오, `buildThresholds()`,
  `PROBE_VUS`, 프로필별 `THINK_TIME` 기본값, 헤더 사용법 4종

### 2026-09-23 · 병목 예측 — 부하 전에 각 층 상한을 실측

#### 무엇을 했나

"한계까지 돌리면 어디서 막히나"를 답하기 위해 **추측 대신 각 층의 상한을 실제로 쟀다.**
결과를 `docs/load-test-records.md`의 "병목 예측" 절에 계단 표로 남겼다.

핵심은 예측 자체보다 **부하 전에 기준을 박아 두는 것**이다. 부하 후에 숫자를 보고
"아마 DB겠지"라고 갖다 붙이면 확증 편향이 된다. 먼저 순위를 적어 두면
빗나갔을 때 빗나갔다고 말할 수 있다.

#### 재면서 바뀐 판단 두 가지

**① 디스크는 후보에서 빠졌다.** 전체 데이터 329MB가 버퍼풀 2048MB 안에 전부 들어간다.
가장 큰 `transaction`(271MB·28만 행)까지 메모리에 올라가므로 읽기 전용 테스트에서
디스크 I/O는 병목이 될 수 없다. 재기 전에는 28만 행이라는 숫자만 보고
디스크를 의심했는데 버퍼풀 크기를 확인하고 접었다.

**② DB 풀 10의 위험도를 낮춰 잡았다.** `spring.jpa.open-in-view: false`였다.
OSIV가 켜져 있었다면 요청 전체(약 32ms) 동안 커넥션을 쥐어 풀 10이 곧바로 치명적인데,
꺼져 있으니 트랜잭션 구간만 점유한다. 여전히 1순위지만 "확정"이 아니라 "유력"이다.

#### 예상 못 한 발견 — SerialGC

```
java -XX:MaxHeapSize=1g -XX:InitialHeapSize=256m -jar /app/app.jar
GC: Copy(minor) + MarkSweepCompact(major)   ← SerialGC
```

**4코어 호스트인데 단일 스레드 GC**를 쓰고 있다. 원인은 컨테이너 메모리 제한 1.5GB다 —
JVM은 컨테이너 메모리가 1792MB 미만이면 SerialGC를 자동 선택한다.
GC 옵션을 준 적이 없으니 아무도 의도하지 않은 설정이다.

부하로 할당 속도가 오르면 major GC 한 번에 수백 ms가 멈출 수 있고,
**평균은 멀쩡한데 p99만 튀는** 형태로 나타난다. 3순위 후보로 적어 뒀다.
지금 고치지는 않았다 — 실제로 GC가 문제인지 확인하기 전에 바꾸면
개선 효과를 측정할 기준선이 사라진다.

#### 더 급한 것 — 측정 자체가 오염될 수 있다

병목 순위보다 이쪽이 먼저다. 호스트는 **4 vCPU에 컨테이너 14개**이고
CPU 제한이 하나도 걸려 있지 않다(`NanoCpus=0`). 여기에 k6까지 얹으면
**백엔드의 한계가 아니라 "k6와 나눠 쓴 뒤 남은 CPU의 한계"를 재게 된다.**
게다가 그 차이가 얼마인지 사후에 알 수 없다.

| 대응 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **k6를 `--cpus=1`로 묶어 실행** | 영향 범위가 고정되고 결과에 "k6가 1코어 점유" 조건을 명시할 수 있다 | **채택(1차)** |
| 외부 머신에서 Nginx 경유로 실행 | CPU 경쟁이 완전히 사라진다. 그러나 20r/s 제한에 걸려 `map` 화이트리스트가 선행돼야 하고, 네트워크 지연이 측정에 섞인다 | 보류(필요 시) |
| 아무 제한 없이 그냥 실행 | 준비 0. 그러나 측정값이 실제보다 낮게 나오고 **얼마나 낮은지도 모른다** | 기각 |
| 백엔드 컨테이너에 CPU 할당 고정 | 재현성이 오른다. 그러나 운영 구성을 바꾸는 것이라 지금 측정이 실환경과 달라진다 | 기각 |

Jenkins도 같은 호스트에 있다(현재 유휴, 메모리 1.55GB). 부하 중 빌드를 걸지 않는다.

#### 남은 것

- [ ] probe 실행 후 이 예측과 대조 — 1순위(풀)·2순위(CPU) 중 어느 쪽이 먼저인지
- [ ] SerialGC 문제가 실제로 나타나면 그때 G1 전환 검토(지금은 기준선 유지)
- [ ] k6 `--cpus=1` 적용한 실행 명령을 스크립트 헤더에 반영

#### 산출물

- `infra/docs/load-test-records.md` — "병목 예측" 절(계단 표·예측 3순위·측정 왜곡 요인)

### 2026-09-23 · probe 첫 실행 — 예측이 뒤집혔다

#### 무엇을 했나

`probe` 프로필을 처음 돌렸다. 4분 20초 완주, **632 req/s · 실패 0 · p95 84ms.**
결과 전문은 `docs/load-test-records.md` `#4`에 있다.

실행 전에 두 가지를 준비했다.

1. **k6를 `--cpus=1`로 묶었다.** 4코어 호스트에서 부하 도구와 대상이 CPU를 나눠 쓰면
   측정값이 실제보다 낮게 나오는데, 제한을 걸어야 "얼마나 낮은지"를 조건으로 적을 수 있다.
2. **5초 간격 서버 지표 수집기(`/tmp/sampler.py`)를 함께 띄웠다.**
   k6 요약은 클라이언트 관점(지연·처리량)만 주고, Grafana는 rate 창이 5m이라
   1분짜리 계단이 평활화된다. gauge를 직접 찍지 않으면 **어디서 막혔는지를 못 본다.**

이 두 번째 준비가 결정적이었다. 없었으면 "632 req/s 나왔고 실패 0"에서 끝났을 것이다.

#### 결과 — 병목은 DB 풀이 아니라 호스트 CPU였다

예측 1순위가 HikariCP(10)였는데 **빗나갔다.** 실제 병목은 2순위로 적었던 CPU다.
근거 세 가지가 서로 맞물린다.

**① 시간 순서** — CPU가 먼저 포화되고 50초 뒤에 pending이 생겼다.

```
t=73   CPU 97%  pending 0   rps 444   ← CPU 포화 시작
t=123  CPU 97%  pending 1   rps 707   ← 50초 뒤에야 대기 발생
t=229  CPU 98%  pending 27  rps 696
```

**② MySQL이 놀고 있다** — pending 27인데 `mysql_threads_running`은 3~5(상한 151).
커넥션이 부족해서 기다린 게 아니라 **커넥션을 쥔 스레드가 CPU를 못 얻어** 반납이 늦은 것이다.

**③ 처리량이 평평하다** — VU를 20 → 40으로 두 배 올렸는데 721 → 696으로 오히려 줄었다.

컨테이너별 CPU(코어): **backend 2.165 · mysql 0.872** · k6 0.482 · prometheus 0.104.
합 3.67 / 4코어 = 92%.

#### 이 결과가 개선 방향을 바꾼다

**DB 풀을 키우는 개선은 효과가 없다.** 원래 `pending`이 오르면 풀을 늘리는 것이
1차 대응이었고 문서에도 그렇게 적어 뒀는데, CPU가 이미 98%면 커넥션을 더 줘도
그걸 쓸 CPU가 없다. 오히려 컨텍스트 스위칭이 늘어 악화될 수 있다.

**pending을 보고 풀부터 늘렸다면 시간만 쓰고 아무 개선도 없었을 것이다.**
`mysql_threads_running`을 같이 찍어 둔 덕분에 갈렸다.

#### 내가 틀린 것

병목 예측에서 **HikariCP를 1순위로 올린 근거가 약했다.** "명시적으로 가장 좁은 문"이라는
이유였는데, 좁은 문이라는 사실과 **그 문에 줄이 서는가**는 다른 문제다.
MySQL 쪽 여유(151 커넥션, 버퍼풀 2GB)를 확인해 놓고도 "앱 풀이 10이니 거기가 먼저"라고
단정했다. 실제로는 **DB에 닿기 전에 CPU에서 막혔다.**

다만 예측을 문서에 미리 적어 둔 것은 잘한 판단이었다. 적어 두지 않았으면
결과를 보고 "역시 CPU였다"고 사후 합리화했을 것이고, 틀렸다는 사실 자체가 남지 않았다.

#### 곁다리로 알게 된 것

- **k6의 CPU 영향이 예상보다 작았다** — 0.482코어(12%). `--cpus=1`로 묶었는데 절반만 썼다.
  외부로 빼도 회수는 약 12%라 **처리량 상한은 800 req/s 안팎**으로 추정된다.
  k6 동거를 가장 큰 왜곡 요인으로 봤는데 실측으로 순위가 내려갔다.
- **CPU가 포화돼도 지연은 안 무너질 수 있다.** p95가 84ms에 머물렀다(임계 3000).
  40 VU로는 큐가 짧아서다. **처리량 한계와 지연 붕괴점은 다른 지점**이며,
  이번에 찾은 것은 처리량 한계뿐이다.
- SerialGC는 발현하지 않았다. heap 136MB/1024MB, major GC 0회. 3순위 예측은 조건 미달.

#### 이번 측정의 한계 — 다음에 고칠 것

**컨테이너별 CPU를 실시간으로 못 봤다.** cAdvisor가 `docker.sock` 없이 도는 탓에
`name` 라벨이 없고 `container_id`만 준다. 위 수치는 probe가 끝난 뒤
`container_id`를 살아있는 컨테이너 목록과 대조해 **역산**한 것이다
(목록에 없던 `941e0f313849`가 사라진 k6 컨테이너였다).

| 대응 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| 지금처럼 사후 역산 | 추가 작업 0. 그러나 `--rm` 컨테이너는 끝나면 이름을 알 수 없고, 실시간 판독이 불가능하다 | 임시 유지 |
| cAdvisor에 `docker.sock` 다시 연결 | `name` 라벨이 살아난다. 그러나 **Docker 29 + containerd 이미지 저장소에서 cAdvisor가 뜨지 않는다**(09-22에 확인하고 뺀 이유) | 기각 |
| Prometheus relabel로 id→이름 매핑 테이블 유지 | 이름이 붙는다. 그러나 컨테이너를 새로 만들 때마다 id가 바뀌어 손으로 갱신해야 한다 | 기각 |
| 샘플러가 실행 시점에 `docker ps`로 매핑을 떠서 함께 기록 | 부하 실행마다 그 시점의 id→이름 표가 남는다. `--rm` 컨테이너도 실행 중에는 잡힌다 | **채택(다음 실행 시)** |

#### 남은 것

- [ ] `PROBE_VUS`를 올려 **지연 붕괴점** 찾기 (이번엔 처리량 한계만 찾았다)
- [ ] 샘플러에 `docker ps` 매핑 기록 추가
- [ ] 개선 방향 재검토 — 풀 크기가 아니라 **요청당 CPU**를 줄이는 쪽
      (N+1 제거 · DTO projection · 응답 크기 · Redis 캐시)
- [ ] PPT 315에 넘길 내용: 병목 = 호스트 CPU 4코어, 처리량 한계 ≈ 720~800 req/s

#### 산출물

- `infra/docs/load-test-records.md` `#4` — k6 요약, 서버 측 피크, 컨테이너별 CPU, 예측 대비 표

### 2026-09-23 · 엔드포인트별 지연 출력 복구 + 지연 붕괴점 탐색

#### 무엇을 했나 (1번)

probe 첫 실행(#4)에서 **전체 p95만 얻고 어느 API가 느린지 몰랐다.**
원인은 내가 만든 `buildThresholds()`였다 — k6는 **임계가 걸린 sub-metric만**
요약에 출력하는데, probe 분기에서 엔드포인트별 8개를 뺐기 때문이다.
판정 기준이 필요 없다고 뺐지만, 그 부작용으로 출력까지 사라진 것이다.

절대 실패하지 않는 임계를 걸어 출력만 끌어내기로 했다.

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **`min>=0`** | 응답 시간은 항상 0 이상이라 깨지지 않는다. "판정하지 않음"이 의도대로 읽힌다 | **채택** |
| `count>=0` | 가장 직관적이다. 그러나 **k6가 거부한다** — Trend가 지원하는 집계는 avg·min·max·med·p 뿐 | 기각(불가) |
| `p(95)<60000` 같은 큰 숫자 | 동작한다. 그러나 그 값이 **판정 기준처럼 읽혀** 나중에 "왜 60초?"라는 오해를 만든다 | 기각 |
| load와 같은 임계(p95<500/700)를 재사용 | 코드가 준다. 그러나 probe는 임계를 넘기는 것이 목적이라 **정상 동작이 실패로 보고**된다 | 기각 |

`count>=0`이 거부되는 것은 **실제로 돌려 보고** 알았다. 25초 타임아웃으로 짧게
실행해 임계 파싱만 확인하는 방식을 썼다 — 4분 20초를 다 쓰고 나서 파싱 에러를
보는 낭비를 피하려고. 실제로 첫 시도가 거부됐으므로 이 검증이 값을 했다.

**샘플러도 개선했다**(`infra/scripts/loadtest-sampler.py`). #4에서 컨테이너별 CPU를
사후 역산해야 했던 문제를, **매 틱 `docker ps`로 id→이름을 다시 읽어** 해결했다.
매 틱 읽는 이유는 부하 도구 컨테이너가 샘플러보다 늦게 뜨기 때문이다.
이번 실행 로그에는 `backend=1.75 mysql=0.88 k6-probe120=0.50`처럼 이름이 바로 찍혔다.

#### 무엇을 했나 (2번) — 지연 붕괴점

`PROBE_VUS=120`으로 돌렸다. 결과는 `docs/load-test-records.md` `#5`.

```
t=199   VU ~60   pending  46  busy  61  rps 746  p95  70ms   ← 쓸 수 있는 한계
t=213   VU 120   pending 108  busy 121  rps 733  p95 254ms   ← 붕괴
t=244   VU 120   pending 107  busy 119  rps 723  p95 421ms
```

**처리량 천장은 VU 60에서 이미 닿았다(745 req/s).** VU를 두 배 올리자 처리량은
3% 줄고 지연만 6배가 됐다. 포화된 계에 부하를 더 넣으면 대기열만 길어진다는
교과서적인 모양이다.

#### 개선 대상을 좁히다 — "아닌 것"을 다 걸러냈다

엔드포인트별 지연은 k6 출력을 `head -70`으로 자르는 바람에 놓쳤다.
그런데 **재실행하지 않고 서버 측 Prometheus에서 뽑았다** — `http_server_requests_seconds`에
`uri` 라벨이 있어 `increase[10m]`로 부하 구간을 그대로 집계할 수 있었다.
4분 20초를 아꼈고, 클라이언트가 아닌 서버 관점이라 오히려 정확하다.

결과: **8개 엔드포인트가 45~55ms로 균등**했다(최고/최저 차 22%, 호출 수도 균등).
특정 API를 골라 최적화할 여지가 없다는 뜻이다.

계층별로도 갈랐다 — 톰캣(필터 바깥) 49.08ms vs Spring MVC 48.89ms, **차이 0.19ms**.
유휴 기준선(#3)의 0.28ms보다 오히려 작다. **지연의 99.6%가 컨트롤러 안**이고
JWT 검증·필터 체인은 부하가 걸려도 비용이 늘지 않았다. 응답 크기도 요청당 1,052 bytes다.

걸러낸 결과 남은 후보는 **컨트롤러 내부(쿼리 실행·엔티티→DTO 변환·비즈니스 로직)뿐**이다.
요청당 CPU는 backend 2.35ms · mysql 1.18ms로 **앱 2 : DB 1**이다.

#### 내가 틀린 것 두 가지

**① 출력을 잘라서 데이터를 놓쳤다.** k6 결과를 `head -70`으로 받아 HTTP 섹션의
엔드포인트별 줄을 보지 못했다. 4분 20초짜리 실행 결과를 보는데 출력을 자른 것은
명백한 실수다. 다행히 서버 측에 같은 정보가 있어 재실행은 면했다.
**부하 실행 출력은 전문을 파일로 남긴다**가 다음 규칙이다.

**② `count>=0`을 검증 없이 썼다.** 직관적이라는 이유로 골랐는데 k6가 지원하지 않았다.
짧은 실행으로 먼저 확인하는 절차를 넣어 둔 덕에 4분을 버리지 않았다.

#### 남은 것

- [ ] **프로파일링** — 여기서 더 좁히려면 async-profiler / JFR이 필요하다.
      "어느 메서드가 CPU를 쓰는가"는 지금 지표로는 답할 수 없다
- [ ] 개선 1건 적용 → 재측정(전후 쌍) — 완료 기준의 핵심
- [ ] `load` 22분 한 판 (지속성·누수)
- [ ] 부하 실행 출력 전문을 파일로 저장하는 습관 (이번 실수)

#### 산출물

- `backend/scripts/loadtest/user-journey.js` — probe 엔드포인트별 표시용 임계
- `infra/scripts/loadtest-sampler.py` — 이름 매핑 포함 샘플러
- `infra/docs/load-test-records.md` `#5`

### 2026-09-23 · Grafana 화면 판독 기록 + 프로파일링 준비

#### 무엇을 했나 (1) — 대시보드 캡처 판독

`#5` 실행 중 Grafana 화면을 판독해 `load-test-records.md`에 남겼다.
샘플러 숫자와 겹치는 부분이 많지만 **그래프에서만 보이는 것이 두 가지** 있었다.

**① `/actuator/health`가 부하 중 610ms.** 다른 엔드포인트가 45~55ms일 때 이것만 튄다.
DB·Redis health indicator를 실제로 호출하므로 커넥션 풀 포화 대기가 그대로 얹힌 것이다.

곧바로 **컨테이너 재시작 위험**을 의심해 `HEALTHCHECK` 설정을 확인했다.
처음에는 `docker-compose.yml`을 뒤졌는데 없었고, **`Dockerfile`에 있었다.**

```
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
```

`/api/health`를 쓰고 그 경로는 부하 중에도 **2.74ms**였다(DB를 건드리지 않는다).
**재시작 위험 없음이 확인됐다.** 만약 `/actuator/health`를 썼다면 timeout 5s를
넘겨 5회 연속 실패 → 컨테이너 unhealthy로 갈 수 있었다. 운이 좋았다기보다
경로를 나눠 둔 설계가 맞았던 것이다.

**② Redis가 거의 쓰이지 않는다.** 부하 중에도 초당 0.76~0.96 명령, 메모리 1.4MB 평평.
이번 부하 경로(계좌·카드·대출·진단·추천·시뮬레이션 조회)에 **캐시가 전혀 없다.**
현재 Redis는 AI 코치 응답 캐시 전용이다. 병목이 컨트롤러 내부 CPU인 상황에서
같은 응답을 반복 계산 중이라면 캐시가 직접적인 해법이 된다 — 다만 계정 4개에
VU 120이 몰린 부하라 **캐시 효과가 과대평가되기 쉽다**는 점을 함께 적어 뒀다.

**확인 못 한 것:** 15:28~15:38 구간에 호스트 CPU가 40~80%로 출렁이는데
컨테이너별 CPU는 같은 구간에서 바닥이다. 컨테이너 밖 프로세스로 보이지만 출처 미확인.
실행 직전 유휴는 3.31%로 확인했고 부하 구간이 명확히 분리되므로 `#5`는 유효하다.
다음부터 **시작 직전 유휴값을 매번 기록**하기로 했다.

#### 무엇을 했나 (2) — 프로파일링 준비

병목이 "컨트롤러 내부"까지 좁혀졌으나 **어느 메서드인지는 지표로 답할 수 없다.**
프로파일링 수단을 정하고 `docs/profiling.md`에 절차를 적었다.

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **JFR을 환경변수(`JAVA_TOOL_OPTIONS`)로 주입** | JDK 21 내장이라 **설치가 0**이다. 이미지·보안 표면 그대로. `.env`에 한 줄 넣고 재배포하면 켜지고, 빼면 꺼진다 | **채택** |
| JFR을 `jcmd`로 제어 | 임의 시점 시작·덤프가 되어 가장 유연하다. 그러나 **JRE 이미지에는 `jdk.jcmd` 모듈 자체가 없다** | 기각(실증) |
| 런타임 이미지를 JDK로 교체 | jcmd·jstack·jmap 전부 사용 가능. 그러나 이미지가 **267MB** 커지고(459→726MB) 디버깅 도구가 컨테이너에 상주해 침해 시 공격 표면이 된다(security.md R3 맥락) | 기각 |
| async-profiler | flame graph, safepoint bias 없음. 가장 정확 | `.so`를 이미지에 넣고 `-agentpath` 로드 필요. **JFR로 답이 안 나오면 다음 수단** | 보류 |
| 코드에 타이머 삽입 | 준비 불필요 | 어디에 넣을지 모르는 상태라 **찾으려는 것을 미리 알아야 한다**는 순환 논리 | 기각 |
| APM 도입(Pinpoint 등) | 상시 관측이 된다 | 에이전트·수집 서버가 필요하고 남은 기간 대비 과하다 | 기각 |

#### 내가 틀린 것 — jcmd를 실행파일만 복사하면 될 줄 알았다

JRE 컨테이너에 `jcmd`가 없는 것을 보고, JDK 이미지에서 `jcmd`와 `libattach.so`를
꺼내 `docker cp`로 넣었다. 재배포 없이 실증하려는 의도는 좋았으나 결과는 실패였다.

```
Error occurred during initialization of boot layer
java.lang.module.FindException: Module jdk.jcmd not found
```

**JRE는 jlink로 모듈을 추려 만든 런타임이라 `jdk.jcmd` 모듈이 이미지 안에 아예 없다.**
실행파일은 껍데기일 뿐이고 모듈이 있어야 동작한다. 바이너리만 옮기면 된다는 생각이
틀렸다. 대신 이 실패 덕분에 **"JDK로 바꾸지 않고도 되는 방법"**을 찾게 됐고,
결과적으로 이미지를 키우지 않는 환경변수 방식으로 갔다.

컨테이너에 넣었던 파일은 제거했다(비루트 uid 10001이라 `-u 0`으로 지워야 했다).

#### 선택 이유

**프로파일링은 지금 몇 번 하면 되는 일이지 상시 운영할 기능이 아니다.**
그 판단이 방안 선택을 갈랐다 — 유연성(jcmd)을 위해 이미지를 267MB 키우고
보안 표면을 늘리는 것은 비용이 이득보다 크다. `duration` 안에 부하를 넣어야 한다는
제약은 실제로 문제가 안 된다(기동 2분 + 준비 1분 + probe 4분 20초 ≈ 7분 < 15분).

감수한 것: **임의 시점에 시작·중지할 수 없다.** 부하가 끝나도 `duration` 15분을
채워야 파일이 나온다. 그리고 `settings=profile`의 오버헤드 약 2%가 측정에 섞이므로
**이 실행의 처리량은 개선 전후 비교의 "전"으로 쓰지 않는다**(기준선은 `#5`).

#### 남은 것

- [ ] `.env`에 `JFR_OPTS` 추가 → 재배포 → 15분 안에 probe 실행 → `/tmp/load.jfr` 회수
- [ ] `jfr print --events jdk.ExecutionSample`로 상위 메서드 확인
- [ ] 프로파일 결과로 개선 1건 선정 → 적용 → 재측정(완료 기준의 전후 쌍)
- [ ] 프로파일링 끝나면 `.env`에서 `JFR_OPTS` 제거 후 재배포 (**잊으면 상시 2% 오버헤드**)
- [ ] 다음 측정부터 시작 직전 유휴 CPU를 기록에 남기기

#### 산출물

- `infra/docs/profiling.md` — 검토한 6개 방안, 켜기·부하·회수·읽기·끄기 절차, 해석 주의
- `infra/docker-compose.yml` — backend에 `JAVA_TOOL_OPTIONS: ${JFR_OPTS:-}`
- `infra/docs/load-test-records.md` — `#5 Grafana 화면 판독` 절

### 2026-09-24 · JFR 프로파일링 실행 — CPU를 쓰는 계층을 찾았다

#### 무엇을 했나

JFR을 켜고 probe(120 VU)를 돌려 **ExecutionSample 18,200개**를 얻었다.
결과 전문은 `docs/load-test-records.md` `#6`.

**결정적 수치: 우리 코드(`com.ssafy.dodam`)가 최상단 프레임인 샘플은 0.4%뿐이다.**
비즈니스 로직은 CPU를 거의 안 쓰고 프레임워크 계층이 대부분을 쓴다.
MySQL 드라이버 10.2% · Hibernate 8.8% · Jackson 8.6% · Micrometer 5.6% · Tomcat 5.0%.

개선 후보 두 개를 근거와 함께 뽑았다.

1. **MySQL 드라이버 SQL 파싱 10.2%** — `StringInspector.indexOfNextChar` 등 문자열 스캔이
   상위에 반복 등장한다. JDBC URL에 `cachePrepStmts`가 없어(기본 false) 같은 SQL을
   매번 다시 파싱하는 것으로 보인다. **URL만 고치면 되고 효과를 전후로 잴 수 있다.**
2. **Spring Security Observation 약 2.6%** — Micrometer 5.6% 중 46.5%가
   `ObservationFilterChainDecorator`였다. 우리는 이 지표를 대시보드에서 쓰지 않는다.

#### 실행 중 겪은 것 세 가지

**① Jenkins 빌드가 측정을 두 번 망칠 뻔했다.** 호스트 CPU가 부하도 없이 60~88%에서
안 떨어져서 `top`을 보니 root 소유 java가 **300% CPU**를 쓰고 있었다.
Jenkins의 gradle 빌드(테스트 1058개 포함)였다. 게다가 그 빌드가 끝나자
`deploy.sh`가 backend를 재배포해 **내가 06:55에 올린 컨테이너를 07:02에 교체**했다.
부하 중이었다면 측정과 JFR 타이머가 통째로 날아갔다.

→ 브리프의 "DB 적재와 실행 시간대 분리"에 **빌드·배포 시간대 분리**를 더해야 한다.
팀 공용 서버라 다른 사람의 push만으로도 빌드가 돈다.

**② 시작 직전 유휴 CPU를 기록하는 규칙이 바로 값을 했다.** `#5`에서 정한 규칙인데,
이번에 7.88%를 확인하고 나서야 "지금은 깨끗하다"고 말할 수 있었다.
(그 전 시도에서는 83.71%였고, 그대로 부하를 걸었다면 오염된 측정이 됐다.)

**③ `jfr` 도구가 원본을 덮어썼다.** `jfr summary`를 돌렸더니 `file is empty`.
**`jfr`도 JVM이라 `JAVA_TOOL_OPTIONS`를 물려받아 새 기록을 시작하며 원본을 비운 것이다.**
호스트에 `docker cp` 사본을 먼저 떠 둔 덕에 39MB를 잃지 않았다.
`-e JAVA_TOOL_OPTIONS=`로 비워 실행하는 절차를 `profiling.md`에 박아 뒀다.

#### 내가 틀린 것 — `#5`의 "필터·인증은 병목이 아니다"는 근거가 틀렸다

톰캣 평균(49.08ms)과 Spring MVC 평균(48.89ms)의 차가 0.19ms인 것을 보고
"필터·인증·파싱은 비용이 없다"고 적었다. **결론 방향은 맞았지만 근거가 틀렸다.**

`WebMvcMetricsFilter`는 필터 체인의 **가장 바깥**에 등록되므로
`http_server_requests_seconds`에는 **Security 필터 시간이 이미 포함**된다.
즉 0.19ms는 "Security 필터 비용"이 아니라 톰캣 커넥터와 메트릭 필터 사이만 잰 값이고,
**애초에 Security 필터를 볼 수 없는 지표였다.** 실제로는 약 4.3%를 쓴다.

**지표로 "없다"를 증명했다고 믿은 것이 잘못이었다.** 지표는 자기가 재는 구간만 말한다.
프로파일러가 필요했던 이유가 정확히 이것이다.

또 하나: 집계에서 `JwtAuthenticationFilter.doFilterInternal`이 우리 코드 1위(14.3%)로
보였는데, 쪼개 보니 **그 샘플의 98.3%가 `filterChain.doFilter()` 하위**였다.
필터 자신의 일은 약 4.6%다. **"우리 코드 중 가장 안쪽 프레임"만 세면
체인 바깥쪽 필터가 과대평가된다** — 집계 방식 자체의 함정이라 한 번 더 쪼개서 확인했다.

#### 남은 것

- [ ] **`JFR_OPTS`를 `.env`에서 제거하고 재배포** (안 하면 배포마다 15분 기록 + 상시 2% 오버헤드)
- [ ] 개선 ①: JDBC URL에 PreparedStatement 캐시 옵션 추가 → 재측정
- [ ] 개선 ②: Spring Security Observation 비활성화 → 재측정
- [ ] 개선은 **한 번에 하나씩** 적용한다(둘을 같이 넣으면 어느 쪽 효과인지 모른다)
- [ ] 재측정 기준선은 `#5`(JFR 없는 판)를 쓴다

#### 산출물

- `infra/docs/load-test-records.md` `#6`
- `infra/docs/profiling.md` — `jfr` 덮어쓰기 함정 반영

### 2026-09-24 · 개선 ① 적용과 재측정 — **완료 기준의 전후 쌍을 처음 확보**

#### 무엇을 했나

JFR 프로파일링(`#6`)에서 나온 1순위 후보를 적용하고 재측정했다.
`DB_URL`에 PreparedStatement 캐시 옵션 4개를 붙였다(커밋 `36a54b2`).

결과(`docs/load-test-records.md` `#7`): **처리량 631 → 667 req/s(+5.7%)**,
서버 측 피크 745 → 778(+4.4%), 지연 p95 285 → 276ms, 스토리 소요 p95 2.01 → 1.59s.

가장 뚜렷한 것은 **60 VU 구간에서 `pending`이 0을 유지**한 점이다.
`#5`는 같은 구간에서 커넥션 대기가 생기기 시작했는데 `#7`은 0이고, 처리량도 높다.

#### 배포 경로를 정식 절차 대신 직접 수정으로 택했다

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **서버 `docker-compose.yml` 직접 수정 → `up -d`** | `DB_URL`은 환경변수라 **재빌드가 필요 없다.** 수십 초면 끝나고 Jenkins 빌드가 CPU를 먹지 않는다. 다음 정식 배포의 `git reset --hard`가 자동으로 정리한다 | **채택(검증용)** |
| push → Jenkins 빌드 → `deploy.sh` | 정식 경로이고 기록이 남는다. 그러나 **재빌드 5~10분 + 그동안 CPU 3코어 점유**로 측정을 바로 이어서 할 수 없다(`#6`에서 실제로 당했다) | 효과 확인 후 진행 |
| 로컬에서만 바꾸고 측정 생략 | 비용 0. 그러나 **효과를 모르면 개선이라 부를 수 없다.** 완료 기준이 전후 비교다 | 기각 |

로컬 커밋은 먼저 남겼으므로 push만 하면 정식 반영된다.

#### 기대보다 효과가 작다 — 그대로 적었다

프로파일에서 MySQL 드라이버가 10.2%였으니 그만큼을 기대했는데 실제는 4~6%다.
**차이를 덮지 않고 이유 후보를 함께 적었다** — 드라이버 10.2%가 전부 재파싱은 아니고,
`useServerPrepStmts=true`는 파싱을 줄이는 대신 서버 사이드 prepare 왕복을 더하며,
무엇보다 **CPU가 여전히 98%라 병목 자체는 그대로**다. 총량이 줄어 같은 CPU로
조금 더 처리하게 됐을 뿐이다.

확인하려면 JFR을 다시 떠서 드라이버 비중이 실제로 줄었는지 봐야 한다(아직 안 했다).

#### 측정 조건이 불리했다 — 그래서 개선폭은 과소평가일 수 있다

시작 직전 유휴 CPU가 `#6`은 7.88%인데 **`#7`은 37.19%**였다.
컨테이너를 막 재생성해 워밍업이 `rate[1m]` 창에 남아 있었다.
`#5`에서 정한 "시작 직전 유휴 CPU 기록" 규칙이 없었다면 이 차이를 몰랐을 것이고,
개선폭을 그냥 숫자로만 비교했을 것이다.

**다음부터는 재생성 후 `rate[1m]` 창이 지나가도록 최소 1분 더 기다린다.**

#### 남은 것

- [ ] 개선 ② Spring Security Observation 비활성화 → 재측정 (**한 번에 하나씩**)
- [ ] 개선 ① 커밋 push → 정식 배포로 서버 상태와 git 일치시키기
- [ ] JFR 재측정으로 드라이버 비중이 실제로 줄었는지 확인(선택)
- [ ] `load` 22분 한 판 — 지속성·누수 확인
- [ ] PPT 315 전달: 병목 = 호스트 CPU 4코어, 한계 ≈ 745~780 req/s, 개선 ① 효과 +4~6%

#### 산출물

- `infra/docker-compose.yml` — `DB_URL`에 PreparedStatement 캐시 옵션
- `infra/docs/load-test-records.md` `#7` — 첫 전후 비교표

### 2026-09-24 · 개선 ② 적용과 재측정 — 누적 처리량 +9.4%

#### 무엇을 했나

`MANAGEMENT_OBSERVATIONS_ENABLE_SPRING_SECURITY=false`를 넣어 Spring Security의
필터 체인 Observation을 껐다(커밋 `aa00ced`). 근거는 JFR `#6` —
Micrometer 5.6% 중 46.5%가 `ObservationFilterChainDecorator`였고,
우리 대시보드는 `spring_security_*` 지표를 하나도 쓰지 않는다.

**결과(`#8`): 서버 측 피크 처리량 745 → 831 req/s(누적 +11.5%),
k6 처리량 631 → 690(+9.4%), 지연 p95 285 → 266ms, 스토리 소요 p95 2.01 → 1.47s(-27%).**

#### 적용 방법 — application.yml 대신 환경변수

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **환경변수 `MANAGEMENT_OBSERVATIONS_ENABLE_SPRING_SECURITY`** | Spring Boot의 relaxed binding이 `management.observations.enable.spring.security`로 풀어 준다. **컨테이너 재생성만으로 반영**돼 측정을 바로 이어갈 수 있다 | **채택** |
| `application.yml`에 프로퍼티 추가 | 설정이 코드와 함께 관리돼 더 정석이다. 그러나 yml은 jar 안에 들어가 **이미지 재빌드(5~10분 + CPU 3코어 점유)**가 필요하고, 그동안 측정을 못 한다 | 기각(측정 단계에서는) |
| Observation 자체를 전역 비활성화 | 더 많이 회수된다. 그러나 **우리가 쓰는 지표(http_server_requests 등)까지 꺼진다** | 기각 |

측정이 끝나면 `application.yml`로 옮기는 것이 맞다 — 지금은 배포 경로에 따라
환경변수가 빠질 수 있다.

#### 검증을 먼저 설계했다

"껐다"를 어떻게 확인할지 정해 두고 적용했다 — **`/actuator/prometheus`에서
`spring_security*` 지표 줄 수가 0이 되는지**. 적용 전에 해당 지표가 존재하는 것을
확인해 뒀으므로(`spring_security_filterchains_*` 등) 0이 되면 확실하다.

환경변수 이름이 맞는지 확신이 없었는데, **검증 방법이 있었기 때문에 시도해 볼 수 있었다.**
결과는 0줄 — 정상 동작.

#### 조건 차이를 그대로 적었다

`#7`의 시작 유휴 CPU는 37.19%, `#8`은 5.77%다. **`#8`이 유리한 조건**이므로
②만의 효과 +3.4%는 과대평가일 수 있다(프로파일 기대치는 2.6%).
반면 `#5 → #8` 누적 +9.4%는 `#5`도 정상 조건이라 신뢰할 수 있다.

`#7`에서 얻은 교훈대로 **`#8`부터는 안정화 조건(CPU<10% 그리고 고부하 프로세스 없음)을
자동으로 기다린 뒤 측정을 시작**하도록 절차를 바꿨다. 이번에는 한 번에 통과했다.

#### 남은 한계 — 병목의 위치는 바뀌지 않았다

개선 두 건은 **요청당 CPU를 줄였을 뿐**이다. 호스트 CPU는 여전히 97~98%에서 포화하고
120 VU에서 `pending`이 100을 넘는다. 구조를 바꾸려면 스케일 업(4 → 8 vCPU)이나
응답 캐싱이 필요하며, 이는 남은 기간과 범위를 넘는다 — **잔여 한계로 PPT 315에 전달한다.**

#### 남은 것

- [ ] `aa00ced` push → 정식 배포 (지금 서버는 직접 수정 상태)
- [ ] 측정 종료 후 `MANAGEMENT_OBSERVATIONS_*`를 `application.yml`로 이관 검토
- [ ] `load` 22분 한 판 — 지속성·누수 확인 (아직 미실시)
- [ ] PPT 315 전달 내용 정리

#### 산출물

- `infra/docker-compose.yml` — `MANAGEMENT_OBSERVATIONS_ENABLE_SPRING_SECURITY`
- `infra/docs/load-test-records.md` `#8` — 누적 전후 비교표

### 2026-09-24 · 시드 재적재가 측정에 미치는 영향 정리 + 개선 ② 롤백 발견

#### 무엇을 했나

"시드를 다시 적재하면 지금까지 한 게 의미 없어지나"라는 물음에 답하기 위해
**무효가 되는 것과 되지 않는 것을 갈랐다.** `docs/load-test-records.md`에
"측정 조건 — 데이터 크기" 절로 남겼다.

| 항목 | 영향 | 이유 |
| --- | --- | --- |
| 개선 ① PreparedStatement 캐시 | **유효** | SQL 문자열 파싱 비용이라 행 수와 무관 |
| 개선 ② Observation 비활성화 | **유효** | 필터 체인 오버헤드라 DB와 무관 |
| 처리량·지연 절대값 | **무효** | 쿼리가 처리하는 행 수가 달라진다 |
| 병목 판정(호스트 CPU) | 재확인 필요 | 쿼리가 무거워지면 균형이 바뀔 수 있다 |

현재 데이터를 실측했다 — 전체 330MB, `transaction` 271MB·**348,794행**,
거래 기간 2026-03-01~09-27(**6개월**), 버퍼풀 2,048MB 대비 **16%**.

24개월 확장(706)은 약 4배이므로 전체 약 1,140MB, 버퍼풀 대비 **약 56%**로 추정된다.
**버퍼풀 안에는 여전히 들어가므로 디스크가 새 병목이 될 가능성은 낮고**,
병목은 여전히 CPU일 것으로 본다. 다만 요청당 처리 행이 늘어 처리량은 떨어진다.

여기서 **한 번 더 확장하면 버퍼풀을 넘어 성격이 다른 병목(디스크)**이 된다는 점을
경계선으로 적어 뒀다.

#### 내가 놓친 것 — 개선 ②가 서버에서 롤백돼 있었다

push 확인차 서버를 봤더니 HEAD가 `899080b`(706 FE 커밋)로 바뀌어 있었고
`docker-compose.yml`에 `MANAGEMENT_OBSERVATIONS`가 **0개**였다.
`deploy.sh`의 `git reset --hard origin/<브랜치>`가 내가 직접 수정한 내용을 지운 것이다.

- 개선 ①(`36a54b2`)은 **머지되어 살아 있다** — 실행 중 컨테이너에 `cachePrepStmts=true` 확인
- 개선 ②(`aa00ced`)는 **아직 머지 전이라 사라졌다**

**직접 수정은 "다음 정식 배포 때 자동 정리된다"고 기록해 뒀는데, 그 정리가
개선까지 함께 지운다는 점을 명시하지 않았다.** 검증용 임시 반영과 정식 반영 사이에
머지가 끼면 롤백된다 — 앞으로는 검증 직후 곧바로 머지 여부를 확인한다.

#### 남은 것

- [ ] `aa00ced` 머지 확인 → 개선 ② 재반영
- [ ] 시드 재적재 시: 적재 완료 → CPU 안정 대기 → 데이터 크기 표 갱신 → 새 기준선 1판
- [ ] `load` 22분 한 판 (지속성·누수)
- [ ] PPT 315 전달 내용 정리

#### 산출물

- `infra/docs/load-test-records.md` — "측정 조건 — 데이터 크기" 절

#### 정정 — 개선 ②는 롤백된 적이 없다 (2026-09-24 08:09)

바로 위에 "개선 ②가 서버에서 롤백돼 있었다"고 적었는데 **사실이 아니다.**

확인 시각이 08:02:48이었고 컨테이너는 **08:02:39에 재생성된 직후**(9초 경과)였다.
배포가 진행 중이어서 그 순간 HEAD가 아직 `899080b`였을 뿐이고,
08:03:45에 다시 재생성되면서 `aa00ced`가 반영됐다.

```
서버 HEAD            06ff920 [S15P21D105-693] [Infra] perf: Spring Security Observation 비활성화
docker-compose.yml   최신 커밋 aa00ced (develop에 정상 머지)
실행 중 컨테이너      MANAGEMENT_OBSERVATIONS_ENABLE_SPRING_SECURITY=false
                     DB_URL에 cachePrepStmts=true
                     spring_security 지표 0줄
```

**개선 ①·② 모두 정상 반영돼 있다.**

**무엇이 잘못이었나:** 배포가 진행 중일 수 있다는 가능성을 배제하지 않고
한 시점의 스냅샷으로 결론을 냈다. 컨테이너 생성 시각이 9초 전이라는 것이
화면에 있었는데도 "방금 재생성됨 = 배포 진행 중일 수 있음"으로 연결하지 못했다.

**앞으로:** 배포 직후 상태를 판단할 때는 컨테이너 생성 시각과 현재 시각의 차이를 보고,
1분 이내면 안정될 때까지 기다린 뒤 다시 확인한다.

### 2026-09-24 · 706 시계열 예측 seed 서버 재적재 (S15P21D105-706)

백엔드 요청: 거래 이력을 6개월 → **24개월(2024-09 ~ 2026-08)**로 늘린 seed를 시연 서버에 재적재한다.
요청 순서는 `6(492) → 8(구독) → 9(알림) → 11(625) → 13(633) → 14(636) → 15(655) → 17(679) → 18(680)`.

#### 1. 적재 전 확인

| 항목 | 값 |
| --- | --- |
| 서버 `~/app` HEAD | `06ff920` — seed 9개 파일 md5가 로컬 작업본과 **전부 일치**(24개월 판이 이미 올라와 있다) |
| 백업 | `~/backups/mysql/dodam-20260924-171751.sql.gz` (9.5M, 적재 직전 수동 실행) |
| 실회원 | **0명** — `user` 4,000행 전부 seed 계정, `account` 비-SEED 0행 |
| 현재 거래 | 348,794행 / 2026-03-01 ~ 2026-09-27 (6개월 + 625의 9월) |
| 시연 4명 구독 | **23건** (655 적용 상태) |
| VERIFY 1번(불변 기준) | `347652 / 195906782800 / 499687287` — 요청서 기대값과 **이미 일치** |

마지막 줄이 중요하다. 적재 전에 이미 기대값과 같으므로, 적재 후에도 같으면 "492가 과거 18개월을
붙이면서 기존 6개월을 건드리지 않았다"가 성립한다. 어긋나면 원인은 이번 적재다.

#### 2. 요청서의 "주의 ①"(625 중단) — 우회안 2개 중 하나는 쓸 수 없다

요청서는 `ERROR 3819 tmp_dodam_625_preflight_chk_1` 회피안으로 (A) 빈 DB에 1~18번 전부,
(B) 11번 건너뛰기를 줬다. **파일을 읽어 보니 (B)는 성립하지 않는다.**

15번(655) preflight에 이 검사가 있다:

```sql
(SELECT COUNT(DISTINCT tx.user_id) ... WHERE tx.transaction_id LIKE 'SEED-SEP-U%-202609-INC-%')
  AS users_with_september_income_count       -- CHECK (= 4)
```

`SEED-SEP-...-INC-`는 **11번만 만든다**. 11번을 건너뛰면 15번이 같은 방식으로 멈추고,
15번에 딸린 17번까지 줄줄이 막힌다. (이번 DB에는 이전 625 실행분이 남아 있어 우연히 통과할
여지는 있지만, 그건 "건너뛰어도 된다"가 아니라 "옛 9월 행에 기대게 된다"는 뜻이라 쓰지 않는다.)

**원인을 다시 보면** 625는 `subscription_id LIKE 'SUB-U%'`인 시연 4명 구독을 세어 6을 기대하는데,
655가 같은 `SUB-U000x-0y` 형식으로 5~7건씩(23건) 다시 만들어 놔서 어긋난다.
8번을 다시 돌려도 소용없는 이유도 파일에 있다 — 8번의 DELETE는 자기가 넣을 6건
(`tmp_dodam_492s_expected` 조인)만 지우고, 655가 추가한 03~07번은 남긴다. 그래서 6이 아니라 23이 된다.

#### 3. 선택 — (C) 시연 4명 구독만 8번 직전 상태로 되돌린다

| 방안 | 내용 | 판정 |
| --- | --- | --- |
| (A) 빈 DB + 1~18번 | 요청서 1순위 | **기각.** Flyway 재적용 + 1~5·7·10·12·16번까지 전부 재적재 + fixture 4,001행 재생성이 필요하고, 앱이 만든 데이터(진단·추천 89행, 현금흐름 90행, 세션 170행)가 사라진다. 시연 4일 전 폭이 너무 크다 |
| (B) 11번 건너뛰기 | 요청서 2순위 | **기각.** 위 2절 — 15번이 같은 이유로 멈춘다 |
| **(C) 시연 4명 `SUB-U%` 구독·결제이력 삭제 후 8번 실행** | 8번이 정식 6건을 다시 만들어 625 preflight가 통과 | **채택** |

(C)를 고른 이유: 15번(655)이 어차피 시연 4명의 `SUB-U%` 구독을 **전부 지우고 23건으로 다시 만든다**.
즉 이 삭제는 15번이 되돌려 놓을 행만 잠깐 비우는 것이고, 최종 상태는 빈 DB에 1~18번을 돌린 것과 같다.
삭제 범위는 시연 4명 · `SUB-U%` 한정이며 나머지 3,996명은 건드리지 않는다. 직전 백업도 있다.

#### 4. 실행

**결정 변경 (17:35).** 위 표에서 (C)를 권했으나 **김지우가 (A) 빈 DB + 1~18번 전체 재적재를 선택**했다.
그 사이 6번(492)은 이미 기존 DB에 적재돼 있었고(160초, 1,392,768행), 그 결과는 아래 5절에 남긴다.
(C)의 임의 DELETE는 실행하지 않았다 — 샌드박스가 막았고, 그 시점에 선택을 물었다.

| 6번 종류별 (적재 직후) | 행수 | 기간 |
| --- | --- | --- |
| FIX | 288,000 | 2024-09-01 ~ 2026-08-20 |
| INC | 96,000 | 2024-09-10 ~ 2026-08-25 |
| SAV | 96,000 | 2024-09-11 ~ 2026-08-26 |
| SUB | 144,000 | 2024-09-12 ~ 2026-08-25 |
| VAR | 768,768 | 2024-09-01 ~ 2026-08-28 |

계약 위반 0, `주거래 입출금` last_transaction_date 2026-08-20. 이 상태는 곧 전체 재적재로 덮인다.

#### 5. 전체 재적재 절차 (선택 A)

빈 DB에서 시작하므로 요청서의 9개 파일이 아니라 README 1~18번 전부를 돌린다.
fixture 4종(491·522·523·524)은 서버 `~/seed-load/make_seed_fixtures.py`가 `infra/.env`의 실제 키로 다시 만든다.

##### 5-1. 막힌 것 — **빈 DB에서는 Flyway가 스키마를 못 만든다**

`DROP DATABASE dodam; CREATE DATABASE dodam;` 뒤 backend를 올려 마이그레이션을 받으려 했더니 V6에서 멈췄다.

```
Migrating schema `dodam` to version "6 - create transaction category and transaction"
Error Code : 1824   Message : Failed to open the referenced table 'card'
Location  : db/migration/V6__create_transaction_category_and_transaction.sql   Line : 29
```

`transaction.card_id`가 `card`를 FK로 거는데 `card`는 **V11**에서 생긴다.
`application-prod.yml`의 `out-of-order: true` 주석이 적어 둔 그대로 **번호 구간을 팀별로 나눠 쓴 결과**다 —
기존 DB는 V11이 먼저 적용된 뒤에 V6가 추가돼 통과했지만, **빈 DB에 V1→V44를 번호순으로 적용하면 V6에서 깨진다.**
즉 "빈 DB에 1~18번"은 seed 문제가 아니라 **마이그레이션만으로는 스키마를 처음부터 만들 수 없다**는 문제였다.

**우회:** 백업에서 스키마만 가져왔다. 데이터는 한 행도 들이지 않았다.

```bash
./scripts/restore.sh ~/backups/mysql/dodam-20260924-171751.sql.gz   # 임시 DB dodam_restore_test (운영 미접촉)
mysqldump --no-data --routines --events dodam_restore_test | mysql dodam      # 테이블 정의만
mysqldump --no-create-info dodam_restore_test flyway_schema_history transaction_category | mysql dodam
```

두 번째 줄만으로는 안 되고 세 번째 줄이 필요하다. `flyway_schema_history` 34행이 없으면 다음 기동에서
Flyway가 전부 다시 적용하려 들고, `transaction_category` 20행은 **V6 마이그레이션이 직접 넣는 기준 데이터**라
`--no-data`로는 따라오지 않는다(6번 seed의 선행 조건 "카테고리 기준 행 20개"가 이것이다).

결과: 테이블 48개 / flyway 34행 전부 success / 카테고리 20행 / 데이터 0행 — **마이그레이션이 끝난 빈 DB**.

##### 5-2. 그 사이 한 번 꼬였다 — 크래시 루프 중인 backend가 같은 DB를 만지고 있었다

V6에서 죽은 backend는 `restart` 정책 때문에 계속 재기동하며 **내가 만드는 중인 DB에 마이그레이션을 다시 시도**했다.
그래서 첫 시도의 `flyway_schema_history` 복사가 `Duplicate entry '1'`로 끊겼고, 그 뒤 `transaction_category`가
비어 있는 채로 남았다(끊긴 지점 이후라 적재가 안 됐다).

`docker compose stop backend`로 멈춘 뒤 DROP → 스키마 → 기준행 순서를 다시 밟아 정상화했다.
**교훈: DB를 갈아엎는 동안 앱 컨테이너는 stop 상태여야 한다.** 기동 실패는 곧 재기동 루프이고,
재기동 루프는 곧 동시 쓰기다.

##### 5-3. 1~18번 적재 (1회차) — 전부 성공, 그러나 지문이 어긋났다

빈 스키마에 `~/seed-load/706/run706.sh`로 1~18번을 연속 적재했다(17:39:13 ~ 17:43:46, **4분 33초**).
느린 구간은 492(140초) · 522(39초) · 524(29초) · 8번 구독(25초) · 680(13초)이고 나머지는 4초 이하였다.
README 규약대로 **15번 뒤에 9번(알림)을 한 번 더** 돌렸다(15번이 시연 4명 구독을 다시 만들기 때문).
12번(인구통계 백필)은 건너뛰었다 — 491 최신판을 새로 돌린 DB에는 불필요하다고 README가 적고 있다.

VERIFY 결과는 2~9번 전부 기대값과 일치했지만 **1번(불변 지문)만 달랐다.**

| VERIFY | 기대 | 실측(1회차) | |
| --- | --- | --- | --- |
| 1 행수·합계 | 347652 / 195906782800 | 347652 / 195906782800 | ✅ |
| 1 지문 | 499687287 | **3277713042** | ❌ |
| 2 전체 | 1390608 / 2024-09 ~ 2026-08 | 1390608 / 2024-09-01 ~ 2026-08-28 | ✅ |
| 4 월 | 24 | 24 | ✅ |
| 5 급여 | 25행, 2025-09부터 3012400 | 동일 | ✅ |
| 7 U0003 | FIX 249 / SAV 36 | 249 / 36 | ✅ |
| 8 대출상환 | 25행(PAID 24) | 25(24) | ✅ |
| 9 계약위반 | 0 | 0 | ✅ |

**원인: 데이터가 아니라 대리키(id)였다.** 지문은

```sql
BIT_XOR(CRC32(CONCAT_WS('|', transaction_id, transaction_date, transaction_time, amount,
  category_id, COALESCE(account_id,'-'), COALESCE(card_id,'-'), payment_method, merchant_name)))
```

로 `account_id`·`card_id`를 포함하는데, 실측값이 `user_id` 4001~8000 · `account_id` 9169~ · `card_id` 6001~ 이었다.
**`mysqldump --no-data`가 `CREATE TABLE ... AUTO_INCREMENT=9169`를 그대로 들고 온다.** 스키마를 백업에서
떠 오면서 옛 DB의 채번 위치까지 같이 온 것이고, 그래서 행 내용은 같은데 id만 통째로 밀렸다.
(행수·합계가 정확히 맞는데 지문만 다른 것이 바로 그 신호다.)

**조치:** 파이프에 `sed "s/ AUTO_INCREMENT=[0-9]*//g"`를 넣어 채번을 1부터로 되돌리고 1~18번을 다시 적재했다.

##### 5-4. 2회차 적재와 검증 — 요청서 기대값 전부 일치

채번을 1부터로 되돌린 스키마에 1~18번을 다시 적재했다(17:48:03 ~ 17:51:58, **3분 55초**).

VERIFY(`DODAM_TRANSACTION_VERIFY.sql`) 결과:

| # | 검사 | 기대 | 실측 |
| --- | --- | --- | --- |
| 1 | 기존 6개월 불변 | 347652 / 195906782800 / **499687287** | 347652 / 195906782800 / **499687287** ✅ |
| 2 | 전체(시연 4명 제외) | 1390608 / 2024-09 ~ 2026-08 | 1390608 / 2024-09-01 ~ 2026-08-28 ✅ |
| 3 | 종류별 | 체인 후 492 기준보다 소폭 증가 | FIX 288167 · INC 96001 · SAV 96010 · SUB 143998 · VAR 769789 ✅ |
| 4 | 월 결손 | 24 | 24 ✅ |
| 5 | U0003 급여 | 25행, 2025-08까지 2,900,000 → 2025-09부터 3,012,400 | 동일 ✅ |
| 6 | U0003 변동소비 | 2026-03~09 설계표 값 | 974,000 / 939,000 / 1,653,000 / 1,015,000 / 1,031,000 / 994,000 / 978,000 ✅ |
| 7 | U0003 고정비·저축 | FIX 249 / SAV 36 | 249 / 36 ✅ |
| 8 | 대출 상환 | 25행(PAID 24), 잔액 12,998,492 | 25(24), 12,998,492 ✅ |
| 9 | 계약 위반 | 0 | 0 ✅ |

추가 검증

- `DODAM_DEMO_DEPOSIT_SAVING_678_VERIFY.sql` — 적금 3건(3,300,000 / 12,100,000 / 30,000,000), 납입 192 + 예정 1,
  목표 '내 집 마련 준비' 150,000,000에 계좌 3건 연결, 다른 사용자 침범 0 ✅
- `DODAM_DEMO_U0003_LEDGER_679_VERIFY.sql` — 2026-03~09 월별 INC/FIX/SAV/SUB/VAR 구성이 계약대로 ✅
- 13번 fixture는 **서버 실제 키로 처음 돌렸는데 그대로 통과**했다(요청서 주의 ②).
  preflight `ready=1 · fixture_hash_valid=1`, 마스킹 `110000-**-**3101`, 상환 25건(PAID 24 · SCHEDULED 1).
- 680은 4,000명 산출, U0003 934점(연체 평균 649.0 / 비연체 888.6, 성별 평균차 7점).

앱 실동작 — backend 기동(17:55:01, healthy) 후 `/api/health` 200, 시연 계정 로그인 200,
`GET /v1/transactions?startDate=2024-09-01&endDate=2024-09-30` 에서 `SEED-U0003-202409-VAR-44` 등 **2024년 9월 거래가 조회된다.**

#### 6. 마무리

| 항목 | 값 |
| --- | --- |
| 최종 행수 | user 4,000 / account 9,004 / card 6,000 / transaction 1,394,125 / subscription 6,017(시연 4명 23) / notification 23 |
| DB 크기 | **932MB**(transaction 892MB). 재적재 전 330MB, 버퍼풀 2,048MB 대비 **46%** |
| 백업 | 적재 전 `dodam-20260924-171751.sql.gz`(6개월) · `-172715`(6번만 반영) / 적재 후 `-175729`(21M) |
| 정리 | 임시 DB `dodam_restore_test` DROP, fixture 4종 파일 삭제(암호문·해시 포함), 작업 테이블 7개 DROP |
| 로그 | `~/seed-load/706/run706.out`(1회차) · `run706b.out`(2회차) · 파일별 `*.sql.log` |

**작업 테이블 7개**는 README가 적어 둔 523·524 fixture 6개 + `tmp_dodam_679_fixed_seasonal` 1개다.
뒤의 것은 README에 없다 — **679 파일의 cleanup 절이 자기 작업 테이블 8개를 지우면서 `_fixed_seasonal`만 빠뜨렸다**
(`DROP TABLE IF EXISTS tmp_dodam_679_card_usage … _context` 목록에 없다). 이번엔 수동으로 지웠고, 백엔드에 전달한다.

#### 7. 남은 것 / 전달할 것

- [ ] **백엔드에 알릴 것 1** — "빈 DB에 1~18번"은 **지금 코드로는 불가능하다**. V6가 V11의 `card`를 FK로 걸어
      마이그레이션이 V6에서 멈춘다(5-1절). 재현 조건이 아니라 구조 문제라 seed 문서에도 적어 두는 게 좋다.
- [ ] **백엔드에 알릴 것 2** — 679 cleanup에 `tmp_dodam_679_fixed_seasonal` 누락(6절).
- [ ] **백엔드에 알릴 것 3** — 625 preflight와 655의 충돌은 그대로 남아 있다. 이번엔 빈 DB라 안 걸렸을 뿐,
      "655가 적용된 DB에서 6·8번을 다시 돌리면 11번이 멈춘다"는 조건은 변하지 않았다.
- [ ] 앱이 만들던 파생 데이터(진단·추천·리포트·패턴·현금흐름)는 **전부 사라졌다**. 시연 전 U0003으로
      현금흐름 예측·패턴 분석·진단을 `forceRefresh: true`로 한 번씩 만들어 둬야 한다(README 699절 3번과 같은 순서).
      DB를 비웠으므로 699 RESET 자체는 필요 없다(초기화할 저장본이 없다).
- [ ] 693 부하 테스트 — 데이터가 330MB → 932MB로 커졌으니 `infra/docs/load-test-records.md`의
      "측정 조건 — 데이터 크기" 표를 이 값으로 갱신하고 새 기준선을 한 판 받는다. 추정 1,140MB보다 작다(실측 932MB, 버퍼풀의 46%).
- [ ] 부하 테스트 계정 파일(`backend/scripts/loadtest/data/users.json`)은 이메일·비밀번호만 쓰므로 **그대로 유효**하다(id 변경 영향 없음).

#### 8. 사후 점검 (2026-09-25 14:46) — "빠진 seed 있나" 대조

seed 디렉터리 20개(VERIFY 제외)와 서버 적재 로그를 대조했다. **미적재 2개, 둘 다 의도적이고 실측으로 확인했다.**

| 파일 | 왜 안 넣었나 | 실측 근거 |
| --- | --- | --- |
| `DODAM_USER_DEMOGRAPHICS_BACKFILL.sql` (12번) | 491 최신판이 이미 채운다 | `birth_date` NULL 0 · `gender` NULL 0 · 여 2,000 / 남 2,000 |
| `DODAM_DEMO_U0003_DERIVED_RESET_699.sql` (699) | 초기화할 저장본이 없다 | 적재 직후 `monthly_report`·`pattern_analysis` 0행 |

나머지 18개는 전부 적재됐다.

##### 8-1. 놓친 것 — 14번 자체 검증이 `contract_violation_count = 1027`을 찍고 있었다

적재 당시 491·625·633 로그만 보고 **14번(636) 로그를 확인하지 않았다.** 다시 보니 1,027이 찍혀 있었다.

원인은 데이터가 아니라 **파일 안의 검증 경계**다. 706이 창을 25개월로 넓혔는데 파일 끝 검사만
`transaction_date < '2026-03-01'`로 남아 있어 과거 18개월 행 1,027건을 전부 위반으로 셌다.
백엔드가 **2026-09-24 `0b20801`에서 경계를 `2024-09-01`로 고쳤고**(그 커밋의 seed 변경은 이 한 줄이 전부다),
서버는 2026-09-25 00:41 KST 배포로 이미 그 커밋 위에 있다.

같은 DB에서 두 경계를 직접 돌려 확인했다.

```
구판 경계(2026-03-01) → 1027      신판 경계(2024-09-01) → 0
U0003 VARIABLE 1,466행 / 25,754,000원   (신판 README 기대값과 일치)
```

**데이터는 그대로 맞고 재적재는 필요 없다.** `0b20801`의 14번 변경은 생성 로직이 아니라 SELECT 한 줄이라
다시 돌려도 같은 행이 나온다. 전체 `DODAM_TRANSACTION_VERIFY.sql`의 9번(계약 위반 0)은 별도 검사이고 원래 0이었다.

**교훈:** 파일별 `*.sql.log`를 **전부** 훑어야 한다. 이번엔 `OK`(mysql 종료코드 0)와 주요 파일 3개만 보고 넘어갔는데,
seed의 자체 검증은 종료코드가 아니라 출력 숫자로 말한다.

##### 8-2. seed가 아니라 앱이 채워야 하는 것 (현재 0행)

`diagnosis` · `recommendation` · `cashflow_forecast` · `improvement_action` · `daily_report` · `user_device` · `user_oauth_account` · `finance_connection`.
`notification`은 23행으로 **9번 기대값(5+6+6+6) 그대로**지만, 재적재 전에는 31,301행이었다 —
차이는 전부 앱 스케줄러가 몇 주에 걸쳐 쌓은 것이라 seed로는 돌아오지 않는다. 시연에 알림함 볼륨이 필요하면 별도 판단이 필요하다.

#### 9. 고친 것 (2026-09-25) — 스크립트 레포화 + 문서 2건 + seed 2줄

8절에서 드러난 문제를 "다음 사람이 같은 데서 안 막히게" 네 갈래로 반영했다.

##### 9-1. `infra/scripts/seed-load.sh` 신설

서버 `~/seed-load/706/`에만 있던 즉석 스크립트를 레포로 옮기면서 이번에 밟은 함정을 전부 넣었다.

| 하위 명령 | 하는 일 |
| --- | --- |
| `bootstrap <덤프>` | 안전 백업 → 임시 DB 복구 → **정의만** 복사(`AUTO_INCREMENT` 제거) → flyway 34행 + 카테고리 20행 → 임시 DB 삭제 → 결과 검사 |
| `load` | fixture 4종 생성 → 1~18번 순차 적재(12번 생략, 15번 뒤 9번 재실행) → 남은 `tmp_dodam_*` 정리 → fixture 파일 삭제 |
| `check [로그dir]` | 로그의 위반 수치 스캔 + VERIFY 3종 실행 |
| `all <덤프>` | 위 셋을 순서대로 |

가드로 넣은 것:
- **backend가 떠 있으면 거부한다**(`ALLOW_BACKEND_UP=1`로만 우회). 5-2절에서 겪은 동시 쓰기 방지.
- `bootstrap`은 DB 이름을 직접 입력해야 진행한다(`restore.sh --into-production`과 같은 방식).
- 부트스트랩 후 **카테고리 20행 · user 0행 · flyway 성공 여부**를 검사하고 어긋나면 멈춘다.
- fixture 파일은 적재 직후 지운다(계좌·카드번호 암호문과 해시가 들어 있다).

##### 9-2. `check`가 실제로 하나를 더 잡았다 — 679도 같은 경계 문제였다

로그 스캐너를 **이번 적재 로그 24개에 실제로 돌려 봤더니** 두 건이 걸렸다.

```
DODAM_DEMO_U0003_STORY_SEED.sql.log    contract_violation_count = 1027   (8-1절, 백엔드가 0b20801에서 수정)
DODAM_DEMO_U0003_LEDGER_679_SEED.sql.log    violation_count = 201        ← 새로 발견
```

679의 9g 검사도 하한이 `transaction_date < '2026-03-01'`로 남아 있었다. 이 검사의 id 패턴
(`%-FIX-%`·`%-SAV-%`·`%-TRF-%`·`%-INC-01`)은 **492가 만든 과거 블록 행까지 같이 잡기 때문에**
24개월 확장 뒤에는 과거 FIX 179 + SAV 22 = 201건이 전부 위반으로 잡힌다.

서버에서 갈라 재 봤다.

```
과거(2026-03-01 이전) 해당 행                      201
그중 실제로 계약을 어긴 행(계좌·카드·금액)           0
24개월 창(2024-09-01~2026-09-27) 밖                  0
하한을 2024-09-01로 고친 검사                        0
```

**데이터는 정상이고 검사식이 창 확장을 못 따라간 것이다.** 636과 같은 종류라 백엔드가 636에 쓴 것과
같은 방식(하한을 492 창 시작으로)으로 고치고, 왜 그런지 주석에 남겼다.

##### 9-3. 나머지 두 건

- `DODAM_DEMO_U0003_LEDGER_679_SEED.sql` cleanup에 `DROP TABLE IF EXISTS tmp_dodam_679_fixed_seasonal;` 추가(6절).
- `backend/database/seeds/README.md` — "빈 DB에 1~18번을 처음부터 적재하기" 절 신설.
  V6→V11 FK 제약, 스키마 부트스트랩 3줄과 **각 줄이 왜 필요한지**, `AUTO_INCREMENT` 함정,
  backend 중지 이유, `OK`가 통과가 아니라는 점, 12번·699 생략 근거, 625를 건너뛸 수 없는 이유.
- `infra/docs/load-test-records.md` — 데이터 크기 표를 **재적재 전/후 2열**로 바꾸고 실측 반영.
  예상 1,140MB 대비 **실측 932MB**(행 수는 오차 0.1%). 크기만 빗나간 이유를 행당 크기로 따져 적었다
  (815B/행 → 671B/행). **다음 추정은 "현재 크기 × 배수"가 아니라 "행당 크기 × 새 행 수"로 잡는다.**

##### 9-4. fixture 생성기를 레포로 옮겼다 (해소)

491·522·523·524 fixture를 만드는 `make_seed_fixtures.py`가 **서버 `~/seed-load/`에만 있었다.**
`backup.sh`는 MySQL 덤프만, `backup-secrets.sh`는 `.env`만 묶으므로 **어느 백업에도 없었다** —
그 디렉터리가 사라지면 2번(491)을 적재할 수 없고, 6~18번이 전부 491에 딸려 있어 seed 전체가 막힌다.

**언제 왜 그렇게 됐나** (서버 타임스탬프 + 커밋 대조)

| 시각(2026-09-14) | 흔적 |
| --- | --- |
| 10:26 | seed `.sql` 9개가 레포에서 `~/seed-load/`로 복사됨 |
| 10:45~10:47 | 1번·2번 첫 적재 |
| **11:28:20** | `make_seed_fixtures.py` 작성 — `fixture_491~524.sql`도 같은 분 |
| 12:25 | 구동재 `b47c91e` "fixture 생성 계약(491~524) 기록" — README에 서버 경로를 적음 |

같은 날 이 로그 8절(10:16)에 "인프라는 DB에 아무 변경도 하지 않았다(조회만), 백엔드 담당자 진행 중"이라고 적어 뒀고,
나흘 뒤 구동재가 올린 `make_demo_loan_fixture.py` 머리말이 이 파일을 선례로 인용하며
**"시연 계정 대출 1건만 다루기 위해 레포에 둔다"**고 적는다. 즉 **의도적 제외가 아니라
"레포에 둔다"는 결정이 서기 전에 서버에서 급히 만들어져 그대로 남은 것**으로 보인다.
(서버는 전원 `ubuntu` 계정이라 파일 소유자로 사람을 특정할 수는 없다.)

**조치:** 서버 파일을 그대로 `backend/scripts/make_seed_fixtures.py`로 커밋했다(13·16번 생성기와 같은 자리).
하드코딩된 키·해시가 없고 `.env`에서 읽기만 하며 출력 SQL에 원문 번호가 없는 것을 확인했다.
로컬에서 더미 키로 실행해 서버와 **같은 건수·같은 바이트 크기**의 fixture 4종이 나오는 것까지 확인했다
(4001 / keys 4000·cards 6000 / loans 3000·minus 1000 / deposits 2000·savings 2000).
`seed-load.sh`의 기본 경로도 레포 쪽으로 바꿨다.

#### 10. 부하 테스트 준비 — 파생 데이터 재생성 (2026-09-25 16:03~16:06)

재적재로 앱 파생 데이터가 전부 사라져 **k6 journey 9개 엔드포인트 중 3개가 빈 응답**이었다.
부하 테스트를 다른 담당이 돌리기로 해서, 그 전에 4계정(U0001~U0004)에 다시 만들었다.

**왜 계정마다 5건씩인가:** journey의 목록 조회가 `page=0&size=5`다. 재적재 전에는 계정별 진단이
1·3·8·2건으로 들쭉날쭉해서 VU마다 응답 크기가 달랐다. **한 페이지가 꽉 차게 5건으로 맞추면
계정 간 조건이 같아져 측정이 흔들리지 않는다.**

| 계정 | 진단 | 추천 | 시뮬레이션 | 현금흐름 | 패턴 |
| --- | --- | --- | --- | --- | --- |
| U0001 | 5 | 3 | 5 | 1 | 1 |
| U0002 | 5 | 7 | 5 | 1 | 1 |
| U0003 | 5 | 6 | 5 | 1 | 2 |
| U0004 | 5 | 3 | 5 | 1 | 1 |

엔드포인트 응답 크기 (4계정 전부 200)

| | 재생성 전 | 재생성 후 |
| --- | --- | --- |
| `diagnoses?page=0&size=5` | 117B (빈 페이지) | **7,5xxB** |
| `recommendations/summary` | 220B (빈 요약) | **439~502B** |
| `simulations?page=0&size=5` | 117B (빈 페이지) | **1,472~1,531B** |

나머지 5개(`accounts/summary`·`cards`·`cards/summary`·`loans`·`loans/summary`)는 재적재 직후부터 정상이었다.
`U0001 loans=41B`(빈 배열)는 **523 seed가 짝수 사용자에게만 대출을 주기 때문**이고 재적재 전과 같다 — 결함이 아니다.

**걸린 것:** 시뮬레이션 3건이 400 `월 감소액이 최근 월평균 지출을 초과합니다`로 거부됐다
(U0001 GROCERY 80,000 / U0003 SHOPPING 100,000·TAXI 30,000). U0003은 636 스토리가 변동 소비를
카테고리별로 잘게 나눠 놓아 TAXI가 월 24,000 수준이다. 금액을 낮춰(8,000~20,000) 5건씩 채웠다.
**부하 테스트용 시뮬레이션 파라미터는 계정별 실제 지출 규모를 보고 잡아야 한다.**

**추천 건수는 재적재 전(4계정 합계 64건)보다 적다(19건).** 이전 값은 QA 중 여러 번 생성되며 쌓인 것이라
재현 대상이 아니다. 요약 응답이 비지 않는 것이 목적이다.

**새 기준선 조건에 적을 것:** "2026-09-25 16:06 파생 데이터 재생성 직후, 계정별 진단 5·시뮬 5".

### 2026-09-25 · 24개월 시드 적재 후 재측정 — 예상이 빗나갔다(좋은 쪽으로)

#### 무엇을 했나

시드가 적재된 상태에서 새 기준선을 잡았다(`docs/load-test-records.md` `#9`).

데이터는 예측과 거의 맞았다 — `transaction` 348,794 → **1,394,125행(정확히 4.0배)**,
전체 330MB → **928.3MB**(예측 1,140MB보다 작다. 인덱스가 예상보다 효율적이었다).
버퍼풀 대비 45.3%로 **여전히 메모리 안**이라 디스크가 병목이 되지 않을 것이라는
예측은 맞았다.

**빗나간 것은 성능이다. 처리량이 떨어질 것으로 봤는데 오히려 같거나 약간 올랐다.**

```
#8 (6개월)   690.3 req/s  p95 266.07ms
#9 (24개월)  703.6 req/s  p95 271.47ms    ← 측정 오차 수준(±2%)
```

#### 왜 빗나갔나 — 부하 경로가 `transaction`을 읽지 않는다

엔드포인트별로 보니 8개 전부 변화가 없거나 오히려 빨라졌고 `diagnoses`만 +3%였다.
이 API들은 `account`·`card`·`loan`·`diagnosis`·`recommendation`·`simulation` 테이블과
**사전 계산된 파생 데이터**를 읽는다. 139만 행짜리 `transaction`을 직접 스캔하는
쿼리가 시나리오 안에 없다.

**내 예측의 잘못:** "데이터가 4배가 되면 쿼리가 무거워진다"고 했는데,
**어느 테이블이 4배가 되는지와 부하 경로가 그 테이블을 읽는지를 연결하지 않았다.**
`transaction`이 커진다는 사실만 보고 전체 성능을 추정했다.
테이블 크기와 측정 경로를 대조했어야 했다.

#### 이번 판으로 드러난 진짜 한계 — 시나리오 커버리지

서버 지표에는 있는데 k6가 부르지 않는 경로가 많다.

```
/api/v1/recommendations 286.79ms · /api/v1/diagnoses/latest 240.29ms
/api/v1/users/me 232.31ms · /api/v1/accounts 170.58ms · /api/v1/goals 168.61ms
/api/v1/cashflow/events 138.13ms · /api/v1/spending/monthly-summary 108.94ms
/api/v1/transactions 99.79ms
```

특히 `/api/v1/transactions`(목록·페이징)와 `/api/v1/spending/monthly-summary`(기간 집계)는
**행 수에 직접 비례**한다. 24개월 데이터의 영향은 이쪽에서 나타날 것이다.

**지금 결과로는 "데이터가 늘어도 괜찮다"고 말할 수 없다.**
"우리가 재는 8개 경로는 데이터 증가에 둔감하다"까지만 말할 수 있다.
이 구분을 기록에 명시했다 — PPT에서 과장하지 않기 위해서다.

#### 확보한 것

- **24개월 데이터 기준 새 기준선(`#9`)** — 이후 비교는 이것과 한다
- 개선 ①② 효과가 데이터 4배 상태에서도 유지됨을 확인
- 병목은 여전히 호스트 CPU(97~98% 포화, pending 108, busy 120)
- 디스크는 병목이 아님(928MB < 버퍼풀 2,048MB) — 예측 적중

#### 남은 것

- [ ] 거래 조회·집계 API를 시나리오에 추가할지 결정
      (추가하면 24개월 데이터의 영향을 실제로 잴 수 있다. 다만 시나리오가 바뀌면
       `#9`와의 비교가 끊기므로 **별도 시나리오로 분리**하는 편이 낫다)
- [ ] `load` 22분 한 판 — 지속성·누수 (아직 미실시)
- [ ] PPT 315 전달 내용 정리

#### 산출물

- `infra/docs/load-test-records.md` `#9`

### 2026-09-25 · PPT 315 전달 자료 작성

#### 무엇을 했나

남은 세 가지(PPT 전달 / `load` 22분 / 거래 시나리오) 중 **PPT 전달을 먼저** 했다.

판단 근거는 **블로킹 여부**다. 완료 기준이 "…병목 원인·잔여 한계가 PPT 315에 전달된다"인데
앞의 네 항목(전후 결과·변경 버전 연결·병목 원인·잔여 한계)은 이미 확보돼 있고
**전달만 남았다.** 게다가 PPT는 받는 사람도 반영할 시간이 필요해서 늦게 주면 못 쓴다.
`load` 22분과 거래 시나리오는 내가 혼자 하는 일이라 뒤로 미룰 수 있다.

`infra/docs/load-test-summary.md` (146줄)를 새로 썼다. 기존 `load-test-records.md`는
측정 원본이라 9판 분량이고 PPT 담당자가 읽기 어렵다.

#### 작성 원칙 — 세 가지를 정해 놓고 썼다

**① 용어를 비전문가 기준으로 바꿨다.** PPT를 만드는 사람과 보는 심사위원이
백엔드 담당이 아니다.

| 원래 | 문서 표현 |
| --- | --- |
| PreparedStatement 캐시 | DB 커넥션의 **쿼리 문장 캐시** |
| Spring Security Observation | 사용하지 않는 **보안 필터 계측** |
| journey_duration p95 | **화면 흐름 1회 소요** |
| HikariCP pending | DB 커넥션 대기 |

**② 사용자 체감에 가장 가까운 지표를 앞세웠다.** 처리량 +9.4%보다
**"로그인 후 계좌·카드·대출·진단·추천을 차례로 여는 흐름이 2.01초 → 1.47초(-27%)"**가
슬라이드에서 훨씬 잘 읽힌다.

**③ 과장할 수 있는 지점에 경고를 박았다.** "데이터 4배에도 성능 유지"는
그대로 쓰면 "데이터가 늘어도 안전하다"로 확대되기 쉽다.
**측정한 8개 경로가 둔감한 것이지 거래 목록·집계 API는 재지 않았다**는 단서를 달았다.

#### 내가 놓칠 뻔한 것 — 데이터 조건이 섞여 있었다

초안을 검토하다 발견했다. **§2 최종 성능(780 req/s)은 24개월 데이터**이고
**§4 개선 전후(631→690)는 6개월 데이터**인데, 나란히 두니 같은 조건처럼 읽혔다.

개선 효과는 시드 확장 **전**에 측정했다 — 같은 데이터에서 비교해야 효과가 갈리기 때문이다.
이건 올바른 측정 순서였지만, **문서에서 조건을 밝히지 않으면 숫자가 어긋나 보인다.**
두 절 제목과 요약 블록에 데이터 조건을 각각 명시했다.

#### 잔여 한계를 6개로 정리했다

숨기지 않고 영향과 해결 방향을 함께 적었다.

1. 서버 CPU 4코어가 물리적 상한 → 서버 증설
2. 거래 목록·집계 API 미측정 → 별도 시나리오
3. 부하 도구가 같은 호스트(0.48코어 점유) → 실제 한계는 약 800 req/s로 추정
4. 테스트 계정 4개에 집중 → 캐시 적중률이 실사용보다 높음
5. 장시간 지속 테스트 미실시 → 누수 미확인
6. 한 호스트 14개 컨테이너 → 빌드·부하 시간대 분리가 운영 규칙으로 필요

#### 남은 것

- [ ] 315 담당자에게 `infra/docs/load-test-summary.md` 공유
- [ ] `load` 22분 한 판 — 잔여 한계 5번을 지울 수 있다
- [ ] 거래 조회·집계 별도 시나리오 — 잔여 한계 2번

#### 산출물

- `infra/docs/load-test-summary.md`

### 2026-09-25 · `load` 22분 지속 테스트 — 누수 없음, 전 항목 합격

#### 무엇을 했나

잔여 한계 5번("장시간 지속 테스트 미실시 → 누수 미확인")을 지우기 위해
`load` 프로필을 22분 돌렸다(`docs/load-test-records.md` `#10`).

**결과: 100만 요청(1,003,064) · 300만 검증(3,009,176) 전건 통과 · 오류 0.00%.**
지연 p95 128.59ms, p99 185.11ms로 **임계(800/1500)를 크게 밑돌았고
엔드포인트별 임계 8개도 전부 통과**했다. `load`는 합격/불합격 판정용 프로필인데 합격이다.

#### 설정 선택 — `THINK_TIME=0`으로 돌렸다

| 방안 | 검토 내용 | 채택/기각 |
| --- | --- | --- |
| **`THINK_TIME=0`, `LOAD_VUS=50`** | 누수를 보려면 **부하가 충분해야** 한다. 이 조합은 probe의 60 VU 구간과 비슷한 강도(~790 req/s)를 22분 유지한다 | **채택** |
| `THINK_TIME=1`(기본), 50 VU | 실사용 흉내라 현실적이다. 그러나 동시 요청이 2개 남짓이라 **커넥션 풀에 닿지도 못하고**, 누수가 있어도 드러나지 않는다 | 기각 |
| `LOAD_VUS=120`으로 포화 유지 | 가장 가혹하다. 그러나 22분 내내 포화면 **누수가 아니라 포화가 지배**해 구분이 안 되고, 같은 서버를 쓰는 다른 작업에 영향이 크다 | 기각 |

#### 누수 판정 — 없음

시작 지표를 **부하 전에 따로 찍어 둔 것**이 결정적이었다.

| 지표 | 시작 | 종료 | 판정 |
| --- | --- | --- | --- |
| JVM heap | 152.37 MB | **142.96 MB** | ✓ 오히려 감소 |
| HikariCP active | 0 | **0** | ✓ 전부 반납 |
| HikariCP idle | 10 | **10** | ✓ 풀 정상 |
| 톰캣 스레드 | 10 | 52 | 정상(풀 확장, 상한 200) |

heap이 22분 내내 107~221MB에서 등락하고 **상승 추세가 없다.** `pending`이
13~38로 꾸준히 있었는데도 종료 시 커넥션이 전부 반납됐다.

스레드 증가(10→52)를 누수로 오해하지 않도록 기록에 근거를 적었다 —
**톰캣은 만든 스레드를 바로 줄이지 않는다**(유휴 정리 기본 60초 후).

#### 곁다리로 알게 된 것 — 짧은 테스트는 JIT가 덜 데워진 상태를 잰다

```
#9  probe (120 VU, 4분 20초)   703.6 req/s
#10 load  (50 VU, 22분)        795.8 req/s   ← 순간 최대 914.9
```

두 가지가 겹쳤다. probe는 **포화 구간이 평균을 깎고**, load는 오래 돌아
**JIT 컴파일이 충분히 이뤄진다.** 부하 10분 경과 시점에 순간 914.9 req/s까지 올랐다.

→ **probe는 병목 위치를 빨리 찾는 용도**이고, **지속 가능한 처리량의 대표값은
`load` 쪽(795.8)이 더 정확하다.** PPT 요약 문서의 숫자를 이 값으로 갱신했다.

#### 내가 틀린 것 — 샘플러를 잘못된 디렉터리에서 띄웠다

`sampler.py`는 `docker compose exec`를 쓰므로 compose 파일이 있는 `infra/`에서
실행해야 하는데 `/home/ubuntu/app`에서 띄웠다. 모든 값이 `-`로 찍혔고,
**부하 10분이 지나서야 알아차렸다.**

다행히 ① 부하 자체는 정상이었고 ② 시작 지표를 부하 전에 따로 찍어 뒀으며
③ 누수 관찰에 중요한 후반부(44~50 VU)는 확보해서 판정에는 지장이 없었다.
하지만 **22분을 통째로 버릴 뻔했다.**

→ 규칙: **백그라운드 작업은 시작 직후 첫 출력을 반드시 확인한다.**
(앞서 `#6`에서 `deploy.sh` 재배포에 당했을 때도 같은 교훈이었는데 형태만 달랐다.)

#### 남은 것

- [x] ~~장시간 지속 테스트~~ → 완료, 잔여 한계 5번 해소
- [ ] 거래 조회·집계 별도 시나리오 (잔여 한계 2번)
- [ ] 315 담당자에게 `load-test-summary.md` 공유

#### 산출물

- `infra/docs/load-test-records.md` `#10`
- `infra/docs/load-test-summary.md` — 지속 처리량·누수 결과 반영, 잔여 한계 5번 해소 표시

### 2026-09-25 · 거래 조회 시나리오 신규 작성과 측정 — 잔여 한계 2번 해소

#### 무엇을 했나

`backend/scripts/loadtest/ledger-journey.js`를 새로 만들고 측정했다(`#11`).

**별도 파일로 만든 이유:** `user-journey.js`는 `#5`~`#10`의 기준선이라
시나리오를 바꾸면 이전 기록과 비교가 끊긴다.

**FE 실사용 패턴을 코드에서 확인하고 그대로 옮겼다.**
가계부 화면(`useLedgerData`)이 7개 요청을 `Promise.all`로 동시에 던지고
그중 거래 목록 2종은 `fetchAllPages`로 전체 페이지를 순회한다(size=100).
k6에서는 `http.batch`로 동시성을 재현했다 — **순차로 부르면 서버가 받는
동시 요청 수가 달라져 실제보다 가벼운 부하**가 된다.

**결과: 852 req/s · 오류 0% · p95 327ms.** 기존 시나리오(781 req/s)보다 오히려 높다.

#### 측정 전에 발견한 것 — 계정당 거래가 246~608건뿐이다

시나리오를 짜기 전에 데이터를 먼저 확인한 것이 판단을 바꿨다.

```
전체 1,394,125건 / 4,000명 (평균 349, 최대 1,833)
로그인 가능 계정 4개, 각자 246 · 245 · 608 · 246건
인덱스 idx_transaction_user_date (user_id, transaction_date) 존재
```

**부하가 읽을 수 있는 것은 139만 행이 아니라 수백 건이다.** 이 사실을 모르고
측정했다면 "거래 조회도 빠르다"를 잘못 일반화했을 것이다.

이 발견은 `#9`("데이터 4배인데 성능 그대로")의 **더 근본적인 이유**이기도 하다.
그때 "파생 테이블을 읽어서"라고 적었는데, 그보다 먼저 **계정이 읽을 데이터 자체가 작다.**

사용자에게 측정 방식을 물어 **실사용 패턴 그대로**를 택했다.
결과가 "가볍다"로 나올 것을 알면서도 측정한 이유는, **근거를 갖춘 "가볍다"와
측정하지 않은 "모르겠다"는 다르기** 때문이다.

#### 내가 틀린 것 — FE 동작을 덜 따라 했다

`verify`에서 3회 중 1회 실패. 재현하니 **2026-09만 HTTP 400**이었다.

```
{"success":false,"message":"종료일은 오늘 이후일 수 없습니다.","code":"INVALID_REQUEST"}
```

스크립트가 현재 달의 **월말(9/30)**을 종료일로 보냈는데 오늘이 9/25다.
FE는 `useLedgerData`에서 **현재 달이면 오늘까지**로 자르고 있었다.
FE 코드를 읽을 때 날짜 범위 계산 부분을 대충 보고 넘긴 것이 원인이다.

**백엔드 버그가 아니라 내 스크립트 문제였고, `verify`를 먼저 돌린 덕에 본 측정 전에 걸렀다.**
4분 20초짜리 측정을 오염된 채로 돌릴 뻔했다.

#### 새로 관찰된 것 — 톰캣 스레드가 179까지 올랐다

| 지표 | `#9` | `#11` |
| --- | --- | --- |
| MySQL 실행 중 쿼리 | 2~7 | **최대 12** |
| MySQL CPU | 0.60~0.88 코어 | **0.84~1.10 코어** |
| 톰캣 busy 스레드 | 120 | **179** (상한 200) |

거래 조회는 DB를 실제로 더 쓰고, 페이지 순회 + 동시 요청 때문에
**톰캣 스레드가 처음으로 상한에 근접**했다. 오류는 없었지만
이 시나리오에서는 스레드가 **두 번째 제약**으로 보이기 시작한다.

#### 남은 것

- [x] ~~거래 조회·집계 API 미측정~~ → 해소
- [ ] (신규) 사용자 1명이 대량 거래를 보유한 경우는 여전히 미측정
      — 그런 계정이 없다(최대 1,833건). 잔여 한계 2'번으로 옮겨 적었다
- [ ] 315 담당자에게 `load-test-summary.md` 공유

#### 산출물

- `backend/scripts/loadtest/ledger-journey.js` (583줄)
- `infra/docs/load-test-records.md` `#11`
- `infra/docs/load-test-summary.md` — 잔여 한계 2번 해소, 2'번 신설

#### `#11` 대시보드 화면 보관 (2026-09-25)

측정 중 Grafana 화면을 `infra/docs/images/`에 넣고 `load-test-records.md` `#11`에서
참조하도록 했다. 레포에 `backend/docs/pattern-evaluation/*.png` 선례가 있어
측정 결과 이미지를 커밋하는 관례는 이미 있다. 두 장 합쳐 753KB다.

| 파일 | 내용 |
| --- | --- |
| `2026-09-25-11-ledger-latency-db.png` | 지연 분위수 · 느린 엔드포인트 · HikariCP · MySQL |
| `2026-09-25-11-ledger-resources.png` | JVM 힙/GC · 호스트 CPU·메모리 · Redis · 컨테이너별 자원 |

**이미지만 넣지 않고 판독 표를 함께 적었다.** 이미지는 나중에 값을 다시 읽어야 하고
검색도 되지 않는다. 수치를 텍스트로 남겨야 PPT나 인수인계에서 바로 쓸 수 있다.

**그래프에서만 보이는 것 두 가지**

- **JVM 힙이 톱니 모양이다.** `used`가 일정 폭으로 오르내리고 우상향 추세가 없다.
  `#10`(22분)에서 숫자로 확인한 "누수 없음"을 거래 조회 경로에서 **모양으로 재확인**했다.
  숫자 두 개(시작 152MB → 종료 143MB)보다 그래프가 더 설득력 있다.
- **Redis가 여전히 초당 0.78건.** 거래 조회 경로에도 캐시가 없다.
  CPU가 병목인 지금 **캐시는 아직 쓰지 않은 카드**라는 점이 그림으로 드러난다.

17:40 근처의 작은 봉우리는 `verify` 재실행분이라 본 측정과 구분해 적었다.

### 2026-09-25 · 느린 엔드포인트 패널 수정 + 기록 정정

#### 무엇을 했나

사용자가 "느린 엔드포인트가 저게 맞나?"라고 물어 목록 전문을 받아 보니 **맞지 않았다.**

```
/actuator/health        5.52 ms
/actuator/prometheus    9.72 ms  (max 98.4 ms)
/api/health             950 µs
/api/v1/auth/login      99.3 ms  (max 130 ms)
/api/v1/auth/logout     15.1 ms
```

두 종류가 섞여 있었다.

| 문제 | 왜 걸러야 하나 |
| --- | --- |
| `/actuator/*`, `/api/health` | 운영·모니터링용이라 **개선 대상이 될 수 없다.** 부하가 끝나면 이것들만 남아 상위를 차지한다 |
| 호출률이 낮은 경로 | 로그인은 `setup()`에서 계정 수만큼(4회)만 부른다. **표본 4건의 p95는 의미가 없는데** 숫자가 커서 상위에 올라온다 |

쿼리에 `uri!~"/actuator.*|/api/health"` 필터와 `호출률 > 0.5/s` 조건을 넣고,
패널 제목을 `느린 엔드포인트 p95 (사용자 API만)`으로 바꿨다.

**고치기 전에 부하 피크 시점(08:35 UTC)을 지정해 두 쿼리를 나란히 돌려 비교했다.**
현재 쿼리는 7위에 `/actuator/prometheus`가 끼었고, 개선 쿼리는 실제 API 6개만 남았다.
부하 중에도 이미 한 자리를 낭비하고 있었던 것이다.

#### 내가 틀린 것 — 캡처 범례만 보고 "가장 느린 API"를 단정했다

`#11` 기록에 **"`/api/v1/spending/categories` 497ms가 9개 중 가장 느리다"**고 적었는데
틀렸다. 캡처 이미지의 범례에 **보이는 항목만** 보고 판단했고, 실제로는
`/api/v1/cashflow/events`가 더 느렸다.

피크 시점으로 다시 쿼리한 값:

```
cashflow/events 468.5 · spending/categories 456.4 · spending/daily 446.8
transactions/categories 443.9 · transactions 442.6 · spending/monthly-summary 442.2
```

**6개가 442~469ms로 촘촘해서 순위 자체는 큰 의미가 없다.** 그래도 문서에 틀린 사실을
남겨 두면 나중에 그대로 인용되므로 정정 절을 따로 추가했다.

**교훈:** 이미지 판독은 **범례가 잘려 있을 수 있다.** 스크롤이 있는 패널이면
숫자를 그래프에서 읽지 말고 **쿼리로 다시 확인**해야 한다.
`#5`·`#11` 판독 때 모두 캡처만 보고 표를 만들었는데, 이번처럼 목록 전문을 받지
않았다면 틀린 채로 남았을 것이다.

#### 곁다리 — 서버 uri 수와 k6 태그 수가 다르다

패널에 6개만 나오는 것이 처음엔 누락으로 보였는데 정상이다.
k6의 name 태그는 9개지만 **서버 uri 기준으로는 6개**다 —
`/api/v1/transactions` 하나에 목록·숨김·일별·카테고리 조회가 모두 포함된다.
이 점을 패널 설명과 가이드에 적어 뒀다.

#### 산출물

- `infra/monitoring/grafana/provisioning/dashboards/loadtest.json` — 패널 쿼리·제목·설명
- `infra/docs/dashboard-guide.md` — 필터 이유 설명
- `infra/docs/load-test-records.md` `#11` — 정정 절과 패널 수정 경위

### 2026-09-25 · 패널 수정 검증 부하 (#12)

#### 무엇을 했나

패널 쿼리를 고친 뒤 **실제로 제대로 나오는지** 확인하려고 한 판 더 돌렸다.
Grafana를 재시작해 프로비저닝을 확실히 반영한 뒤 `#11`과 같은 조건으로 실행했다.

**부하 결과가 `#11`과 1% 이내로 일치했다**(790.3 vs 797.9 req/s, p95 329.86 vs 327.59ms).
의도한 검증은 아니었지만 **측정 재현성**이 덤으로 확인됐다.

#### 검증 방법 — 같은 시점·같은 데이터로 두 쿼리를 비교

"고쳤으니 되겠지"로 넘기지 않고, **부하 중 같은 시각을 지정해** 수정 쿼리와 예전 쿼리를
나란히 실행했다. 시점이 다르면 비교가 성립하지 않는다.

| 순위 | 수정된 쿼리 | 예전 쿼리 |
| --- | --- | --- |
| 1 | `cashflow/events` 17.4ms | **`/api/v1/auth/login` 97.9ms** |
| 2 | `spending/monthly-summary` 16.2ms | **`/actuator/prometheus` 21.5ms** |
| 3~ | 실제 API 4개 | 실제 API 5개 |

**예전 쿼리는 상위 두 자리를 쓸모없는 경로에 뺏기고 있었다.**

#### 이번에 확실해진 것 — 잘못된 결론으로 이어질 뻔했다

가장 심각한 것은 `/api/v1/auth/login`이 **1위(97.9ms)**로 나온다는 점이다.
로그인은 `setup()`에서 계정 수만큼 **4회**만 호출되므로 표본이 4건이고,
그 p95는 사실상 최댓값이다.

이대로 패널을 믿었다면 **"로그인이 가장 느리니 로그인을 최적화하자"**는
결론으로 갔을 것이다. 실제로는 부하와 무관한 초기화 비용이다.

**호출 수가 적은 경로를 걸러야 하는 이유가 이것이다.** p95는 표본이 충분할 때만
의미가 있는데, 대시보드는 그 조건을 알려주지 않는다.

#### 산출물

- `infra/docs/load-test-records.md` `#12`

#### `#12` 대시보드 화면 보관 (3장)

`infra/docs/images/`에 3장을 추가하고 `#12` 절에서 참조하도록 했다.
`#11`과 마찬가지로 **이미지만 넣지 않고 판독 표를 함께** 적었다.

| 파일 | 내용 |
| --- | --- |
| `2026-09-25-12-latency-endpoint-db.png` | 지연 분위수 · **느린 엔드포인트(수정판)** · HikariCP · MySQL |
| `2026-09-25-12-threads-jvm-host.png` | 톰캣 스레드 · JVM 힙/GC · 호스트 CPU·메모리 · Redis |
| `2026-09-25-12-containers.png` | 컨테이너별 CPU·메모리 |

첫 장에 **수정된 느린 엔드포인트 패널이 실제로 사용자 API 6개만 보여 주는 모습**이
담겨 있다. 쿼리 검증(`#12` 본문)과 별개로 **화면 증거**가 남았다.

#### 새로 관찰된 것 — 톰캣 스레드 풀이 상한까지 확장됐다

```
#9  user-journey    busy 120
#11 ledger-journey  busy 179
#12 ledger-journey  busy 181 · current 200   ← 생성 스레드가 상한 도달
```

`busy`(처리 중)는 181인데 **`current`(만들어 둔 스레드)가 200**이다.
톰캣이 부하에 대응해 풀을 끝까지 확장했다. 오류는 없었고 `busy`가 200에 붙지도
않았으므로 **고갈은 아니지만 여유가 19개뿐**이다.

거래 조회 시나리오가 스레드를 많이 쓰는 이유는 분명하다 — 가계부 화면이
7개 요청을 동시에 던지고 거래 목록 2종은 페이지를 끝까지 순회한다.
**한 사용자 동작이 `user-journey`보다 훨씬 많은 동시 요청을 만든다.**

**함의:** 지금은 CPU가 먼저 포화하므로 스레드는 병목이 아니다.
그러나 **잔여 한계 1번(CPU 증설)을 해결하면 스레드가 다음 한계로 올라올 수 있다.**
증설 후 재측정 시 `tomcat_threads_*`를 먼저 봐야 한다는 뜻이며,
이 점을 기록에 남겨 뒀다 — 증설만 하고 스레드 설정을 그대로 두면
기대만큼 처리량이 늘지 않을 수 있다.

### 2026-09-25 · 개선 ③ 조사·적용·철회 — 요청당 쿼리 수

#### 무엇을 했나

요청당 6.54개 쿼리의 정체를 `performance_schema`로 밝히고, 개선안을 적용했다가
**테스트 323개가 깨져 되돌렸다.** 전문은 `docs/load-test-records.md` "개선 ③ 시도와 철회".

#### 조사 결과가 예상과 정반대였다

N+1을 의심하고 들어갔는데 **N+1은 없었다.**

```
요청당 6.54개 = 트랜잭션 제어 4.5개 + 실제 데이터 조회 약 1.7개
  SET autocommit 2.0 · COMMIT 1.0 · SET SESSION TRANSACTION READ ONLY/WRITE 1.5
```

**오버헤드가 본체의 3배**다. 쿼리 자체는 이미 효율적이었다.

덤으로 개선 ①이 잘 동작한다는 증거도 나왔다 —
`Com_stmt_prepare 1,728` 대 `Com_stmt_execute 3,824,456`으로,
준비 1회로 약 2,200회 재사용 중이다.

#### 적용했다가 철회한 과정

`spring.datasource.hikari.auto-commit: false` + `hibernate.connection.provider_disables_autocommit: true`를
넣었다(환경변수로는 불가 — relaxed binding이 `provider_disables_autocommit`의
언더스코어를 점으로 바꿔 키가 깨진다).

**단위 테스트 하나는 통과했지만 전체를 돌리니 1,388개 중 323개가 실패했다.**
`404`, `EmptyResultDataAccessException`, 참조 무결성 위반 — 전형적인 "커밋 안 됨" 증상이다.

`git stash`로 되돌려 같은 테스트를 돌리니 통과해 **원인이 내 변경임을 확정**했다.

#### 철회 근거 — 테스트만의 문제가 아니었다

원인은 `JdbcTemplate`으로 픽스처를 만드는 테스트 57개 파일이었다.
"그럼 `application-prod.yml`에만 넣으면 되겠네"로 갈 뻔했는데,
**운영 코드를 확인한 것이 판단을 바꿨다.**

| 위치 | 작업 |
| --- | --- |
| `CashflowResultWriter` (3곳) | 현금흐름 결과 저장 |
| **`JdbcAccountBalanceWriter` (2곳)** | **계좌 잔액 수정** |

이들이 `@Transactional` 밖에서 호출되면 커밋되지 않는다.
**잔액이 유실될 수 있는 위험**을 이득(쿼리 31% 감소, 효과는 아직 미측정)과
남은 기간에 견주어 감당할 수 없다고 봤다.

#### 배운 것 — 단위 테스트 하나로 안심하면 안 된다

처음에 `HealthApiIntegrationTest` 하나만 돌려 통과한 것을 보고 넘어갈 뻔했다.
**트랜잭션 동작에 닿는 변경이라 전체를 돌린 것**이 맞았다.

지금까지 개선 ①②는 설정 변경이었지만 **트랜잭션 경계를 건드리지 않았다.**
이번 것은 같은 "설정 한 줄"처럼 보여도 성격이 달랐다.
**변경의 크기가 아니라 닿는 범위로 검증 수준을 정해야 한다.**

#### 남긴 것

이 조사는 실패가 아니다. 요청당 쿼리의 정체를 수치로 밝혔고,
**왜 지금 고칠 수 없는지를 근거와 함께** 남겼다.
`load-test-summary.md` 잔여 한계에 `5'`번으로 추가했다(선행 조건 2가지 명시).

#### 산출물

- `infra/docs/load-test-records.md` — "개선 ③ 시도와 철회" 절
- `infra/docs/load-test-summary.md` — 잔여 한계 `5'` 추가
- `backend/src/main/resources/application.yml` — **변경 없음(철회)**

### 2026-09-25 · 선행 조건 ② 조사 — 운영 코드 JDBC 쓰기 7곳 추적

#### 무엇을 했나

개선 ③ 철회 시 남긴 선행 조건 중 ②(운영 코드의 JDBC 쓰기가 트랜잭션 경계 안인가)를
끝까지 추적했다. **코드 읽기만 하므로 위험이 없는 작업**이다.

**결과: 7곳 전부 트랜잭션 경계 안.** 표는 `docs/load-test-records.md`에 남겨
다음 사람이 같은 추적을 반복하지 않도록 했다.

- `JdbcAccountBalanceWriter`(잔액 수정 2곳) → `MinusAccountCommandService` 클래스 `@Transactional`
- `CashflowResultWriter`(3곳) → `CashflowForecastStore.complete()` 메서드 `@Transactional`
- `JdbcAccountTransactionWriter`(거래 INSERT) → `AccountTransferService.save()`,
  `AccountSyncHandler.apply()` 둘 다 `private`이고 `transactionTemplate.execute()` 안에서만 호출

`EntityManager.executeUpdate()`/`createNativeQuery()` 사용은 0건이었다.

#### 조사 중 내가 틀린 것 — "6곳"이 아니라 7곳이었다

철회 기록에 **"운영 코드 JdbcTemplate 쓰기 6곳"**이라고 적었는데 틀렸다.
검색 패턴을 넓히자 하나가 더 나왔다.

```
1차: grep "jdbcTemplate\.(update|execute|batchUpdate)"        → 6곳
2차: grep "\.(update|batchUpdate|execute)\(" + jdbc|template  → 7곳
```

새로 나온 것이 하필 **`JdbcAccountTransactionWriter`(거래 INSERT)**였다.
변수명이 `jdbcTemplate`이 아니었으면 2차에서도 놓쳤을 것이다.

**교훈: grep 기반 조사는 완전성을 증명하지 못한다.** 첫 검색 결과를 숫자로 단정해
기록까지 남긴 것이 잘못이었다. "현재 패턴으로 찾은 것은 N곳"이라고 썼어야 했다.

#### 조사 결과가 좋은데도 적용을 권하지 않는 이유

7곳이 모두 안전해도 **적용 시점에 안전망이 없다.**
설정을 넣으면 테스트 323개가 깨지고, 그 상태에서는 진짜 회귀가
"원래 깨진 것"에 묻혀 구분되지 않는다. 놓친 8번째가 잔액·거래 쓰기라면
**검증 수단이 사라진 채 배포**하게 된다.

**순서가 반대였다는 것이 이번 조사의 결론이다.**

```
❌ 설정 적용 → 테스트 깨짐 → 운영 코드 확인   (내가 한 순서)
✅ 테스트 픽스처 정리 → 설정 적용 → 검증 → 배포 → 측정
```

선행 조건 ①(테스트 픽스처 57개 파일)은 테스트 코드 리팩터링이라
693(부하 테스트) 범위를 넘는다. 별도 작업으로 넘긴다.

#### 산출물

- `infra/docs/load-test-records.md` — "선행 조건 ② 조사 완료" 절(7곳 추적표)
- `infra/docs/load-test-summary.md` — 잔여 한계 `5'` 보강
