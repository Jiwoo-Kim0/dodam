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
