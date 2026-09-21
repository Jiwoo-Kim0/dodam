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
