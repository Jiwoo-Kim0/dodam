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
