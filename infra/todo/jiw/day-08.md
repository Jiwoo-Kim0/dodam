# Day 8 — 실제 진행 기록

계획: `infra/todo/day-08.md`, CI/CD 문서: `infra/docs/ci-cd.md`

## 2026-09-14

### 착수 배경 — "docker를 새로 빌드하면 기존 것은 어떻게 되나"에서 출발
배포 파이프라인 설명 중 나온 질문에서 시작해, `deploy.sh`의 롤백 이미지 보존 로직을 실제로 검증했다.
**의도대로 동작하지 않고 있었다.**

### 1. 롤백 이미지 보존이 무력화돼 있던 문제 (수정)

**증상 — EC2 실측**

```
infra-backend:latest     sha256:93a784911df3   (2026-09-14 00:37 빌드)
infra-backend:rollback   sha256:93a784911df3   ← latest와 동일 ID
실행 중 backend 컨테이너 → sha256:9ad0e1d19698   ← 태그 없는 별개 이미지
```

`infra-backend:rollback`이 **직전에 서비스하던 이미지가 아니라, 방금 빌드한(=검증되지 않은) 이미지**를
가리키고 있었다.

**원인 — 태그를 잡는 시점이 빌드보다 늦다**

| 순서 | 주체 | 동작 | `infra-backend:latest`가 가리키는 것 |
| --- | --- | --- | --- |
| 1 | Jenkinsfile `② Build & Test` | `compose -f $WORKSPACE/infra/docker-compose.yml build backend` | **새 이미지로 이동** |
| 2 | `deploy.sh:77` (수정 전) | `docker tag infra-backend:latest infra-backend:rollback` | 새 이미지 (= 잘못된 롤백 지점) |

워크스페이스(`$WORKSPACE/infra/`)와 배포 대상(`$APP_DIR/infra/`) **양쪽 모두 compose 프로젝트명이
`infra`**(compose 파일이 있는 디렉터리명)라 동일한 `infra-backend` 이미지 이름을 공유한다.
`docker-compose.yml`에 `name:`이 없고 `.env.example`에도 `COMPOSE_PROJECT_NAME`이 없어 기본값이 쓰인다.

**영향**: 코드는 `git reset --hard`로 롤백돼도 **이미지는 롤백되지 않는다.**
compose 설정 실수로 인한 실패는 여전히 구제되지만, **앱 코드 자체가 기동을 깨뜨리는 경우
롤백해도 같은 깨진 이미지가 다시 뜬다.** `ci-cd.md`에 적어둔
"빌드 자체가 깨져 있으면 롤백조차 못 한다"는 방어 논리가 이 경로에서 성립하지 않고 있었다.

### 2. 파생 발견 — 실행 중인 이미지가 스토어에서 사라져 있었다

```
$ docker inspect -f '{{.Image}}' backend
sha256:9ad0e1d19698606a1bc3b739980adecb251b099c41db9c5466c6de035cde7c69
$ docker image inspect sha256:9ad0e1d19698...
Error response from daemon: No such image
```

digest 표현 차이인지 확인하려고 nginx·mysql로 대조했으나 **둘 다 `.Image`와 이미지 `.Id`가 정확히 일치**했다.
즉 표현 문제가 아니라 **해당 이미지가 실제로 삭제된 것**이다.
`latest` 태그가 새 빌드로 옮겨간 뒤 아무 태그도 참조하지 않는 상태가 되어 GC된 것으로 보인다.
(Docker 29 / containerd 스냅샷터, overlayfs)

→ 그래서 "실행 중 컨테이너의 이미지를 잡는다"만으로는 부족하다. **성공한 이미지에 고정 태그를 붙여
GC 자체를 막는 조치**가 함께 필요하다.

### 3. 수정 내용 — `infra/scripts/deploy.sh`

**(1) 롤백 지점 캡처를 "태그" 기준에서 "실제 서비스 중인 것" 기준으로 변경**

```bash
DEPLOYED_TAG="infra-backend:deployed"   # 헬스체크를 통과한 마지막 이미지
PREV_IMAGE=""
RUNNING_IMAGE="$(docker inspect -f '{{.Image}}' backend 2>/dev/null || true)"
if [ -n "$RUNNING_IMAGE" ] && docker image inspect "$RUNNING_IMAGE" >/dev/null 2>&1; then
    docker tag "$RUNNING_IMAGE" infra-backend:rollback && PREV_IMAGE="infra-backend:rollback"
elif docker image inspect "$DEPLOYED_TAG" >/dev/null 2>&1; then
    docker tag "$DEPLOYED_TAG" infra-backend:rollback && PREV_IMAGE="infra-backend:rollback"
    log "실행 중 이미지를 찾지 못해 마지막 검증 이미지($DEPLOYED_TAG)를 롤백 지점으로 사용한다"
else
    log "WARN: 보존할 backend 이미지가 없다 — 실패해도 코드만 롤백된다(이미지 롤백 불가)"
fi
```

**(2) 배포 성공 시 `infra-backend:deployed` 고정 태그 부여** (end-to-end 통과 직후)

```bash
NEW_IMAGE="$(docker inspect -f '{{.Image}}' backend 2>/dev/null || true)"
docker image inspect "$NEW_IMAGE" >/dev/null 2>&1 || NEW_IMAGE="infra-backend:latest"
docker tag "$NEW_IMAGE" "$DEPLOYED_TAG" 2>/dev/null \
    || log "WARN: $DEPLOYED_TAG 태그 실패 — 다음 배포의 롤백 fallback이 없다"
```

두 가지를 동시에 해결한다. 다음 배포의 fallback 롤백 지점이 되고, 태그가 붙어 있으므로 GC되지 않는다.

**설계 판단**: 셋 다 실패하면 **WARN을 남기고 코드만 롤백**한다.
없는 안전망을 있는 척하는 것(= 수정 전 동작)이 가장 나쁘다. 배포 로그에 드러나게 했다.

**검증** — EC2에서 태그를 만들지 않는 dry-run으로 분기 판정만 확인

```
RUNNING_IMAGE=sha256:9ad0e1d19698...
[dry-run] 분기C: WARN — 보존할 이미지 없음, 코드만 롤백     ← 새 로직 (정직한 실패)
[old]     docker tag infra-backend:latest infra-backend:rollback
          → sha256:93a784911df3 (검증 안 된 새 이미지)      ← 기존 로직 (조용한 오동작)
```

`bash -n` 문법 검사 통과.

**반영 시점**: Jenkinsfile이 `$WORKSPACE/infra/scripts/deploy.sh`(새로 체크아웃한 사본)를 실행하므로,
**develop 머지 후 첫 배포부터** 적용된다. EC2에 수동으로 올릴 필요 없다.
첫 배포는 `infra-backend:deployed`가 없어 분기C(WARN)로 가고, 그 배포가 성공하면 태그가 생겨
그다음 배포부터 fallback이 살아난다.

### 4. 문서 갱신 — `infra/docs/ci-cd.md`
- ③ Deploy 표 1행: "현재 backend 이미지" → "**실행 중인** backend 컨테이너의 이미지"
- 전체 흐름 mermaid: `롤백 지점 확보 / 커밋 + 실행 중 이미지 보존`
- "이미지까지 보존하는 이유" 아래에 경위·실측값·새 동작을 경고 블록으로 추가
- 최종 갱신일 2026-09-09 → 2026-09-14

### 5. 함께 확인된 현재 상태 (조치 안 함)

| 항목 | 내용 |
| --- | --- |
| **develop 빌드 실패 중** | 빌드 #75(09-14 00:38), #76(09-14 09:14) 모두 FAILURE. 둘 다 `./gradlew clean build` 실패 — Build & Test 단계에서 막혀 배포까지 가지 않았다. 서비스는 09-13 13:52에 뜬 컨테이너로 정상(healthy) |
| **디스크 누적** | 이미지 61개 12.9GB(4.56GB 회수 가능), **빌드 캐시 240개 29.05GB**. 배포마다 744MB 이미지가 쌓이는데 `prune`을 도는 곳이 파이프라인·cron 어디에도 없다. 현재 디스크는 309GB 중 40GB(13%) 사용이라 급하지는 않음 |

### 6. 확인 요청 — Flyway 적용 상태 및 시드 적재 여부 (조회만, 변경 없음)

**Flyway**: 적용 18건 / 실패 0건, 마지막 적용 `V30` (2026-09-11 16:31).
레포에는 20개가 있고 **`V18`(recommendation)·`V19`(simulation)가 미적용**이며 해당 테이블도 없다.
누락이 아니라 배포가 막혀서다 — 두 파일은 09-13 16:37 / 23:43에 develop에 들어왔는데,
현재 backend 컨테이너는 그 이전인 09-13 13:52 기동분이고 이후 빌드 #75·#76이 gradle 실패로 배포되지 않았다.
**develop 빌드가 고쳐져 배포가 성공하면 기동 시점에 자동 적용된다.**

번호가 뒤섞여 적용된 것(V3 → V10~V13 → V4 …)은 정상이다.
`application-prod.yml`에 `out-of-order: true`가 있고, 담당 영역별로 번호 구간을 나눠 쓴다(V1~V9 BE-A, V10~ BE-B).

**시드 적재: 전혀 안 되어 있다.** 운영 DB 34개 테이블 중 데이터가 있는 것은 2개뿐이다.

| 테이블 | 행 수 | 출처 |
| --- | --- | --- |
| `flyway_schema_history` | 18 | Flyway |
| `transaction_category` | 20 | **V6 마이그레이션이 넣는 기준 행** (시드 아님) |
| 나머지 32개 전부 | **0** | — |

`financial_company` / `financial_product` / `financial_product_option`도 0이다.
즉 Day 4의 TODO **"시드 데이터 적재 (테이블 생성 이후, 백엔드 담당자 진행)"** 는 여전히 미완이다.
`backend/database/seeds/README.md` 기준으로 시드는 Flyway가 실행하지 않고 `mysql` 클라이언트로 직접 적재해야 하며,
`infra/mysql/init/`도 비어 있어 자동 적재 경로가 없다.

적재 순서 1번(금융상품, Jira 525)은 선행 조건이 **V10뿐이라 지금 바로 적재 가능**하다.
2~5번은 애플리케이션 코덱으로 만든 fixture 테이블(`tmp_dodam_*_crypto`)이 같은 스키마에 있어야 해서 선행 작업이 필요하다.

### 7. 이어서 확인된 것 — `ddl-auto` 임시 조치 제거 조건은 이미 충족

`SPRING_JPA_HIBERNATE_DDL_AUTO: none`(`c43f40c`, 09-08 10:16 긴급 복구 때 투입)의 제거 조건은
day-04.md에 "누락 테이블 3개(`financial_company`·`financial_product`·`financial_product_option`)의
마이그레이션이 들어오면 제거하고 `validate`로 복귀"로 적혀 있다.
그 테이블을 만드는 `V10`은 **2026-09-09 14:08에 적용 완료** — 조건은 5일 전에 충족됐고 아직 켜져 있다.

지금 상태의 위험: 엔티티와 스키마가 어긋나도 기동 시점에 걸리지 않고 런타임에 터진다.
V18·V19처럼 새 엔티티가 들어오는 중이라 어긋날 여지가 있는 시기다.

**제거 순서**: 지금 바로 지우면 V18·V19 미적용 상태와 겹쳐 판단이 어려워진다.
develop 빌드 수정 → 배포 성공(V18·V19 적용) → 그다음 제거가 안전하다.

### 8. 시드 적재 — 백엔드 담당자 진행 중이라는 공유를 받고 재확인 (10:16)

**운영 DB에서는 진행 흔적이 없다.** 인프라는 DB에 아무 변경도 하지 않았다(조회만).

| 확인 항목 | 결과 |
| --- | --- |
| 주요 테이블 행 수 | `financial_company`·`financial_product`·`user`·`account`·`card`·`transaction`·`goal`·`subscription` **전부 0** |
| 실행 중인 쿼리 | 없음 (조회용 자기 자신 1건뿐) |
| 작업 테이블 `tmp_dodam_*` | 없음 |
| mysql 컨테이너 부하 | CPU 0.93%, 메모리 634MiB / 3GiB — 사실상 유휴 |

→ 로컬·별도 환경에서 진행 중이거나 아직 착수 전인 것으로 보인다. **운영 반영은 별도로 필요하다.**

**적재 시 인프라 쪽 여유는 충분하다**

| 자원 | 현재 | 판단 |
| --- | --- | --- |
| 디스크 | 309GB 중 40GB(13%) 사용, mysql 볼륨 207MB | 492 거래 시드 약 348,000행이 들어와도 여유 |
| 메모리 | 15GiB 중 11GiB available, mysql `mem_limit` 3GB / 버퍼풀 2GB | 충분 |

**적재 중 주의할 인프라 동작 두 가지**

1. **배포 실패 시 롤백이 mysql을 재생성한다.**
   `deploy.sh`의 `rollback()`은 `up -d --no-deps --force-recreate $DEPLOY_SERVICES`를 쓰는데
   목록에 `mysql`이 포함된다. 적재 중에 걸리면 진행 중 트랜잭션이 끊긴다.
   시드가 멱등하게 작성돼 있어 복구는 되지만 시간이 버려지고 `tmp_dodam_*` 작업 테이블이 남을 수 있다.
   지금은 develop 빌드가 Build & Test에서 실패해 배포가 시작되지도 않지만,
   **gradle 실패가 고쳐지는 순간 배포가 걸린다.** 적재와 겹치지 않게 조율이 필요하다.
   (정상 배포 경로인 `up -d --build`는 mysql 서비스 정의가 바뀌지 않는 한 재생성하지 않으므로 영향 없음)

2. **`container-watch.sh`가 5분마다 돈다.** 적재 부하로 mysql이 일시적으로 unhealthy가 되면
   Mattermost 알림이 뜬다. 오탐이니 놀라지 말 것.

백업 cron은 매일 04:00이고 `mysqldump --single-transaction`이라 적재 중에 겹쳐도 일관된 시점이 덤프된다(부분 적재 상태가 찍힐 뿐 무해).

### 9. 재확인 — 오늘 실제 배포 2건에서 같은 오동작이 그대로 재현됐다 (10:43)

세션 재개 시점에 develop 빌드가 복구돼 **#77(10:31)·#78(10:39) 두 건이 SUCCESS**로 배포됐다.
아직 이 수정이 머지되지 않았으므로 **수정 전 로직이 그대로 돈 배포**다. 실측:

```
실행 중 backend 컨테이너의 이미지 → sha256:191b61e19e7a   (10:39:04 기동, #77 배포분)
infra-backend:latest              → e1659ce2c0bd          (#78 Build & Test가 옮겨감)
infra-backend:rollback            → a05f5c7db7b0          ← 셋 다 서로 다르다
$ docker image inspect sha256:191b61e19e7a
Error response from daemon: No such image
```

`rollback`이 **실제로 서비스 중인 이미지도, 최신 이미지도 아닌 제3의 이미지**를 가리킨다.
1·2절에서 기록한 오동작이 가설이 아니라 **오늘 배포에서 실제로 반복되고 있음**이 확인됐다.

**2절의 "이미지가 사라졌다" 판정을 더 강한 대조군으로 재검증했다.**
기존 대조군(nginx·mysql)은 둘 다 **pull 받은 이미지**라, "로컬 빌드 이미지는 `.Image`와
이미지 `.Id`의 표현이 다를 수 있다"는 가능성을 배제하지 못했다.
그래서 **로컬 빌드 + 실행 중**인 `jenkins`로 다시 대조했다.

| 컨테이너 | 이미지 출처 | `.Image` vs 이미지 `.Id` |
| --- | --- | --- |
| nginx / mysql | pull | 일치 |
| **jenkins** | **로컬 빌드** | **일치** (`7c47363bbd6f`) |
| backend | 로컬 빌드 | **불일치** — `.Image`가 스토어에 없음 |

로컬 빌드여도 표현은 일치한다. 따라서 backend의 경우는 표현 차이가 아니라
**태그를 잃은 이미지가 실제로 사라진 것**이 맞다. (Docker 29.8.0 / containerd 스냅샷터, overlayfs)

→ 수정본의 분기 설계(실행 중 이미지 → `:deployed` → WARN)와 **성공 시 `:deployed` 고정 태그**가
   둘 다 필요하다는 근거가 보강됐다. 어느 쪽 원인이든 동작은 동일하게 안전하다.

### 10. 해소된 항목 — V18·V19 적용 완료, `ddl-auto` 제거 조건 충족

#78 배포의 backend 기동 로그(10:39:04):

```
Current version of schema `dodam`: 30
Migrating schema `dodam` to version "18 - create recommendation" [out of order]
Migrating schema `dodam` to version "19 - create simulation"     [out of order]
Migrating schema `dodam` to version "31 - create pattern analysis"
Successfully applied 3 migrations to schema `dodam`, now at version v31
```

6절에서 "배포가 막혀서 미적용"이라고 본 판단이 맞았다 — 배포가 풀리자 기동 시점에 자동 적용됐다.
7절의 `SPRING_JPA_HIBERNATE_DDL_AUTO: none` 제거 선행 조건(V18·V19 적용)이 이로써 **충족**됐다.

### 11. 이미지·빌드 캐시 정리 정책 — `infra/scripts/docker-gc.sh` 신규 (미적용)

5절에서 **미결**로 남겨둔 항목. 스크립트를 작성했고, **아직 EC2에서 실행하지 않았다**(운영 삭제 작업).

**현황 재측정 (10:43)**

| 항목 | 값 |
| --- | --- |
| 이미지 | 64개 13.48GB (회수 가능 4.56GB) |
| **빌드 캐시** | **248개 30.2GB, active 0** |
| 디스크 | 309GB 중 42GB(14%) |
| cron | `backup.sh`(04:00), `container-watch.sh`(5분) — **정리 작업 없음** 확인 |

**설계**

| 대상 | 처리 | 이유 |
| --- | --- | --- |
| 이미지 | `docker image prune -f` (**`-a` 금지**) | dangling만. `latest`/`rollback`/`deployed`는 태그가 있어 살아남고, 실행 중 이미지는 docker가 건너뛴다 |
| 빌드 캐시 | `builder prune -f --max-used-space 10GB` | 전부 날리면 다음 배포의 gradle 빌드가 처음부터 돈다. 상한선만 건다 |
| 중지된 컨테이너 | `container prune -f` | — |
| **볼륨·네트워크** | **건드리지 않음** | `mysql_data`·`jenkins_home`이 여기 있다. `system prune --volumes`를 넣지 말 것 |

`--keep-storage`는 Docker 29에서 빠졌다(`builder prune --help` 확인). `--max-used-space`를 쓴다.

**배포와의 경합 방지**: `deploy.sh` 프로세스가 떠 있으면 정리를 건너뛴다.
빌드 중인 캐시를 걷어내면 그 배포가 느려지거나 깨진다.

**이 정리가 안전한 전제가 `:deployed` 태그다.** 태그가 없으면 "방금 서비스에 올린 이미지"가
dangling으로 분류돼 정리 대상이 된다. 3절 수정과 이 스크립트는 짝이다 — 순서상 **수정이 먼저 배포돼야 한다.**

**남은 절차**: EC2 1회 수동 실행으로 회수량·보존 이미지 확인 → 문제 없으면 cron 등록(주 1회 새벽).
백업(04:00)·container-watch(5분)와 시간이 겹치지 않게 잡는다.

### 12. 커밋 정리 (12:20~) — Jira 키는 **475**

세션 재개 시점에 EC2 상태는 10:43 측정과 동일했다. 새 배포 없음(backend는 여전히 #78, 10:38:55 기동),
빌드 캐시 248개 30.2GB(active 0), 디스크 14%.

**커밋 전 발견한 문서 결함**: `ci-cd.md`에 새로 넣은 "디스크 정리" 절이
**"배포 대상 서비스" 절 중간에 삽입**되어, 그 절을 닫던 인용 블록
("목록에 없는 서비스는 배포가 아예 건드리지 않는다")이 새 절 아래로 떨어져 나가 있었다.
원위치로 되돌렸다.

**Jira 키 정리** — Day 8은 `S15P21D105-475`, Day 7은 `-474`.
현재 브랜치명이 `S15P21D105-474-exterior-connection`이라 직전 인프라 커밋 6건이 모두 474지만,
이 작업은 Day 8이므로 **475**로 커밋한다. (브랜치는 그대로 둔다)

| 커밋 | 내용 |
| --- | --- |
| `75cb038` | `fix:` 롤백 지점을 실행 중인 이미지 기준으로 — `deploy.sh` + `ci-cd.md` 롤백 관련 |
| `3129a92` | `feat:` `docker-gc.sh` 신규 + `ci-cd.md` 디스크 정리 절 |
| (이 커밋) | `docs:` Day 8 기록 |

`ci-cd.md`는 변경 성격이 둘로 나뉘어 해당 부분끼리 나눠 스테이징했다.

**아직 push하지 않았다.** GC 실행은 수정이 배포된 뒤로 미룬다(사용자 판단).

## 2026-09-15

### 13. 프론트 연동 문의에서 발견한 레이트 리밋 오설정 (수정)

**착수 배경** — 프론트에서 "`EXPO_PUBLIC_API_BASE_URL=https://j15D105.p.ssafy.io/api`를 넣으면
백엔드와 연동되는가"라는 질문이 들어왔다. 답하려고 경로 체계를 실측하다 별건을 찾았다.

#### 연동 문의에 대한 답 (조회만, 변경 없음)

외부에서 실측:

```
GET  https://j15D105.p.ssafy.io/api/health        → 200 {"success":true,"data":{"status":"UP"}}
POST https://j15D105.p.ssafy.io/api/v1/auth/login → 400 (이메일/비밀번호 필수)
```

즉 **그 값으로 연동된다.** 다만 조건이 있다 — 백엔드에 `servlet.context-path`가 없고
컨트롤러가 `@RequestMapping("/api/v1/...")` 절대경로를 쓴다. nginx `location /api/`는
`proxy_pass`에 URI를 붙이지 않아 원본 경로를 그대로 넘긴다.
따라서 base가 `.../api`면 FE는 `/v1/cards` 식으로 이어 붙여야 한다.
`/api/v1/cards`로 붙이면 `/api/api/v1/cards`가 된다.

**함정 — 인증 없이 찍으면 없는 경로도 401이다.**

| 경로 | 응답 |
| --- | --- |
| `/api/v1/cards` (정상) | 401 |
| `/api/api/v1/cards` (오타) | 401 |
| `/api/v1/nonexistent-xyz` (없음) | 401 |

`SecurityConfig`가 `/api/v1/**`를 `authenticated()`, 나머지를 `anyRequest().denyAll()`로 잡아
라우팅 전에 거부하기 때문이다. **401을 "경로는 맞고 토큰만 없다"로 읽으면 안 된다.**
경로 검증은 `permitAll`인 `GET /api/health`나 `POST /api/v1/auth/login`으로 해야 한다.
(`permitAll`은 health + `signup`/`login`/`refresh` POST뿐)

함께 전달한 것:
- FE에 아직 `EXPO_PUBLIC_API_BASE_URL`을 읽는 코드가 없다 — 참조처는 `eas.json`과 `.env.example`뿐,
  `frontend/src/api/`는 비어 있다.
- `eas.json`의 `env`는 EAS 빌드 전용이라 로컬 `expo start`에는 적용되지 않는다. `frontend/.env` 필요.
- 운영 `CORS_ALLOWED_ORIGINS=http://localhost:8081` 하나뿐 — 네이티브는 무관하지만 Expo 웹은 막힌다.
- 호스트명 대문자 `j15D105`는 DNS·TLS·nginx `server_name` 모두 대소문자를 구분하지 않아 그대로 동작한다.

#### 발견한 문제 — 로그인 레이트 리밋이 한 번도 적용되지 않고 있었다

```nginx
location ^~ /api/auth/ { limit_req zone=api_auth ... }   # 분당 10req
```

실제 인증 경로는 `/api/v1/auth/...`(`AuthController`의 `@RequestMapping`)다.
**이 location은 매칭된 적이 없고**, 로그인 요청이 일반 버킷(초당 20req)으로 빠져
무차별 대입 방어가 사실상 없는 상태였다. Day 3에 걸어둔 이래 계속 그랬다.

주석에 "해당 엔드포인트는 아직 미구현, 미리 걸어둠"이라고 적혀 있던 것이 원인으로 보인다.
구현 시점에 경로를 대조하지 않았다.

#### 수정 — `infra/nginx/conf.d/default.conf`

```nginx
limit_req_zone $binary_remote_addr zone=api_auth:10m rate=20r/m;   # 10r/m → 20r/m

location ~ ^/api/v1/auth/(login|signup)$ {
    limit_req zone=api_auth burst=10 nodelay;
```

**설계 판단 3가지**

| 판단 | 이유 |
| --- | --- |
| `refresh`·`logout`·`session` 제외 | 앱이 자동으로 도는 호출이다. 막으면 정상 사용 중에 429로 끊긴다 |
| prefix가 아닌 **정규식** location | 아래 `location /api/`가 `^~` 없는 일반 prefix라 매칭 순서상 정규식이 먼저 이긴다. `^~ /api/v1/auth/`로 쓰면 위 3개까지 함께 걸린다 |
| 10r/m → 20r/m, burst 5 → 10 | 버킷 기준이 `$binary_remote_addr`다. SSAFY 캠퍼스망처럼 팀 전체가 같은 공인 IP로 나가면 버킷을 공유해, 한 명의 로그인 테스트가 팀 전체를 막는다. FE 연동이 막 시작되는 시점이라 더 그렇다 |

**검증** — 실행 중인 nginx는 건드리지 않고, EC2에서 동일한 인증서 볼륨을 붙인
일회용 컨테이너로 `nginx -t`:

```
nginx: configuration file /etc/nginx/nginx.conf test is successful
--- exit=0 ---
```

**반영 시점**: `deploy.sh:163`이 배포마다 `docker exec nginx nginx -s reload`를 돌리므로
**develop 머지 후 첫 배포에 자동 반영**된다. 수동 조치 불필요.
(conf.d는 bind mount라 `git reset --hard`로 파일은 바뀌지만, reload가 없으면 최대 6시간
— compose command의 6h reload 루프 — 동안 구 설정이 살아 있다)

`infra/todo/security.md` R6의 레이트 리밋 항목을 체크하고 경위를 함께 적었다.
"미적용이었다"는 사실이 남아야 발표 때 근거가 된다.

### 남은 의문 — 같은 오설정이 더 있는지

`^~ /api/auth/` 하나만 경로가 어긋나 있었는지, 다른 location도 대조가 필요하다.
현재 확인된 것: `/api/health`(200), `/actuator/`(404 차단), `/jenkins/`(403),
`/jenkins/project/`(웹훅), `/grafana/` — 전부 실제 경로와 일치한다.

### 14. 시연 계정 비밀번호 시드 적용 (11:43) — 운영 DB 쓰기

백엔드 담당자가 만든 `~/seed-load/DODAM_DEMO_PASSWORD_SEED.sql`(Jira 521)을 검토하고 적용했다.
운영 DB에 대한 **첫 쓰기 작업**이다(그전까지 인프라는 조회만 했다).

**적용 명령**

```
docker exec -i mysql sh -c 'mysql -h127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" dodam' \
    < ~/seed-load/DODAM_DEMO_PASSWORD_SEED.sql
```

**사전 검토**

| 확인 | 결과 |
| --- | --- |
| 파괴적 구문 | 없음 — `UPDATE` 1건뿐 |
| 영향 범위 | `email IN (4건)` + `deleted_at IS NULL`. `email`은 `UNIQUE KEY` 평문 `VARCHAR(255)`(V1)라 **최대 4행 보장** |
| 대상 존재 | `user_id` 1~4 실재, 탈퇴 아님, `password_hash` 전부 NULL |
| 실제 사용자 영향 | **0** — 4,000명 전원이 `@dodam.invalid` 시드 계정 |
| 멱등성 | 재실행 안전(같은 값 UPDATE) |
| 해시 형식 | `$2a$10$` + 53자 = 60자, 유효한 BCrypt cost 10 |
| 되돌리기 | 같은 조건 `password_hash = NULL` |
| 배포 경합 | `deploy.sh` 미실행 확인 후 적용 |

`email`이 평문인지 먼저 확인한 이유: ERD에 `VARBINARY` 암호화 컬럼이 있어서,
`email`도 암호화 컬럼이면 `WHERE email IN (...)`이 0행을 잡고 조용히 아무 일도 안 했을 것이다.
V1 확인 결과 평문이라 문제없었다.

**결과** — 파일 내장 검증과 별도 조회가 일치

```
demo_users_with_password = 4 / other_users_with_password = 0
user_total 4000 / with_password 4   (user_id 1~4, $2a$10$...len=60, updated_at 11:43:06)
```

**전달한 지적 2가지 (인프라가 고칠 것이 아님)**

1. 파일 주석이 "시연 비밀번호는 `backend/database/seeds/README.md` 시연 계정 로그인 절에 있다"고
   가리키는데 **그 절이 없다.** README 70행에 "별도 비밀번호 설정 절차가 필요하다"만 있다.
   지금 상태로는 적용은 됐는데 **비밀번호를 아는 경로가 레포에 없다.**
2. 이 SQL만 `~/seed-load/`에 있고 **레포에 없다.** 다른 시드 9개는 `backend/database/seeds/`에 있다.
   EC2를 다시 만들면 사라진다.

**보안상 인지해 둘 것**: 공개된 `https://j15D105.p.ssafy.io/api/v1/auth/login`에
공유 비밀번호를 가진 계정 4개가 생겼다. 데이터가 전부 가짜라 피해 범위는 제한적이지만,
**13절의 로그인 레이트 리밋 수정이 아직 배포되지 않았다**(현재 초당 20req 버킷).
머지를 서두르는 편이 좋다.

### 15. 별건 — 일일 백업이 09-15부터 조용히 실패하고 있었다 (미수정)

14절 적용 전 백업 신선도를 확인하다 발견했다.

```
-rw------- ... Sep 14 04:00 dodam-20260914-040001.sql.gz          8,742 B
-rw-rw-r-- ... Sep 15 04:00 dodam-20260915-040001.sql.gz.partial  8,138,858 B   ← 최종본 없음
```

**덤프 자체는 정상이다.** gzip 검증 통과, 헤더 `-- MySQL dump 10.13 Distrib 8.0.46`,
압축 해제 82,728,391바이트. 데이터는 멀쩡한데 **검증 단계가 스크립트를 죽였다.**

**원인 — `backup.sh:58`의 SIGPIPE 경합**

```bash
zcat "$TMP" | grep -q "MySQL dump" || fail "덤프 내용이 예상과 다름"
```

`grep -q`는 첫 매칭에서 즉시 종료한다. "MySQL dump"는 헤더 1행에 있으므로 grep은 바로 빠지는데,
zcat은 아직 82MB를 더 쓰려던 참이라 **SIGPIPE로 죽는다.** 스크립트 상단의 `set -euo pipefail`이
파이프라인 종료 코드를 zcat의 141로 잡아 `|| fail`이 발동한다.

실측 재현:

| 대상 | `zcat \| grep -q` 종료 코드 |
| --- | --- |
| 09-15 백업(82MB 해제) | **141 (SIGPIPE)** |
| 09-14 백업(작음) | 0 |

**DB가 작을 때는 zcat이 grep보다 먼저 끝나서 드러나지 않았다.**
09-14 시드 적재로 덤프가 8.7KB → 8.1MB로 커진 순간부터 매번 실패한다.

**영향**: 유효한 최신 백업이 **2026-09-14 04:00**이고, 이는 **시드 적재 이전**이다.
지금 DB를 잃으면 09-14 하루치 시드 작업이 전부 날아간다.

**파생 문제**: 보관 정리가 `-name "*.sql.gz"`라 `.partial`은 매칭되지 않아 **영원히 쌓인다.**
`.partial`은 `chmod 600`이 `mv` 뒤에 있어 권한도 `644`로 남는다
(`BACKUP_DIR`이 700이라 실제 노출은 없다).

**수정 방향** (사용자 확인 후 적용): `grep -q` 대신 조기 종료가 없는 형태로 바꾼다.
`zcat "$TMP" | head -c 200 | grep -q "MySQL dump"` 처럼 zcat 출력을 먼저 잘라내거나,
검증만 `set +o pipefail` 구간으로 감싼다. 수정 후 1회 수동 실행으로 최종본 생성을 확인한다.

### 16. `backup.sh` 수정 및 백업 공백 해소 (11:52~11:53)

15절에서 찾은 SIGPIPE 버그를 고치고, 수정본으로 백업을 1회 돌려 공백을 닫았다.

**실패 이력 확인** — `backup.log`

```
[backup 2026-09-14 04:00:01] 완료: 12K
[backup 2026-09-15 04:00:01] 덤프 시작 → .../dodam-20260915-040001.sql.gz
[backup 2026-09-15 04:00:06] ERROR: 덤프 내용이 예상과 다름
```

실패는 **09-15 한 번**이고, 에러 메시지도 가설과 정확히 일치했다.
09-14 04:00이 마지막 성공이며 그 시점은 시드 적재(09-14 10:26~13:51) 이전이다.

**진단 확정** — 04:00 덤프의 마지막 3행에 `-- Dump completed`가 있었다.
**덤프는 완주했고 검증만 죽인 것**이 확인됐다.

**수정 — `infra/scripts/backup.sh`**

```bash
# (수정 전)
zcat "$TMP" | grep -q "MySQL dump" || fail "덤프 내용이 예상과 다름"

# (수정 후)
TAIL="$(zcat "$TMP" 2>/dev/null | tail -3)"
case "$TAIL" in
    *"Dump completed"*) ;;
    *) fail "덤프가 끝까지 기록되지 않음(완료 표식 없음)" ;;
esac
```

`tail`은 EOF까지 읽으므로 조기 종료가 없고 따라서 SIGPIPE도 없다.
**헤더가 아니라 꼬리를 보는 쪽이 검증으로서도 낫다** — 시작이 아니라 완주를 확인한다.

함께 고친 것: 보관 정리가 `-name "*.sql.gz"`뿐이라 `.partial`이 영원히 쌓이던 문제.
`\( -name "*.sql.gz" -o -name "*.sql.gz.partial" \)`로 넓혔다.

**검증 — 양성/음성 대조**

| 대상 | `gzip -t` | 완주 표식 판정 |
| --- | --- | --- |
| 정상 덤프 | 통과 | **통과** |
| 중간에 끊긴 덤프(앞 100KB만) | **통과** | **거부** |

`gzip -t`만으로는 잘린 덤프를 못 잡는다는 것도 함께 확인됐다
(mysqldump가 중간에 죽어도 gzip 스트림 자체는 정상 종료될 수 있다).
구 로직을 정상 덤프에 다시 돌려 `exit=141`도 재확인했다.

**실행 결과**

```
[backup 2026-09-15 11:53:44] 덤프 시작 → .../dodam-20260915-115344.sql.gz
[backup 2026-09-15 11:53:47] 완료: 8.1M
exit=0
```

최신 백업 검증: gzip OK, `CREATE TABLE` 43개, 14절에서 넣은 시연 비밀번호 해시 포함.
권한 `600`으로 정상 생성.

**정리**: 테스트로 만든 중복분(11:52)은 삭제했다.
09-15 04:00 `.partial`은 남겨뒀다 — 유효한 덤프이고, 이제 보관 정리 대상에 포함되어 8일 뒤 자동 삭제된다.

**아직 해소되지 않은 것**: 백업 실패가 `backup.log`에만 남고 **아무 데도 알리지 않는다.**
`container-watch.sh`는 컨테이너를 보지 cron 작업을 보지 않는다.
이번에도 09-15 04:00에 ERROR가 찍혔지만 하루 가까이 아무도 몰랐다.

## 2026-09-16

### 17. 소셜 로그인 — compose 변수 누락 수정 및 `.env.example` 정리

**배경**: 카카오 로그인 연동에 무엇이 필요한지 확인하던 중, 백엔드가 2026-09-15에
소셜 로그인을 이미 구현해둔 것을 발견했다(`backend/docs/auth-social.md`, S15P21D105-611).
엔드포인트는 `POST /api/v1/auth/social/{provider}`, 카카오는 `/v2/user/me` 검증 방식이다.
인프라 문서(`external-integrations.md` 4절)가 예상했던 흐름과 같지만 **엔드포인트 경로와
환경변수 이름이 다르다**(`/auth/oauth/` → `/auth/social/`, `KAKAO_APP_KEY` → `KAKAO_APP_ID`).

#### 발견 1 — compose가 소셜 변수를 backend에 전달하지 않고 있었다

`application.yml`이 참조하는데 `docker-compose.yml`에 없었다.

```
app.social.kakao.app-id     : ${KAKAO_APP_ID:}      ← application.yml
app.social.google.client-ids: ${GOOGLE_CLIENT_ID:}  ← application.yml
docker-compose.yml backend environment → 두 변수 모두 없음
```

**왜 위험한가**: 기본값이 빈 문자열이라 **백엔드는 정상 기동한다.** 카카오는 "우리 앱 토큰인지"
검사만 생략되고 로그인은 되지만, **구글 로그인만 런타임에 503**(`SOCIAL_PROVIDER_UNAVAILABLE`)로
떨어진다. 09-08 `JWT_SECRET` 유실과 같은 유형인데, 그때는 기동 실패로 즉시 드러난 반면
이번엔 겉보기 정상이라 더 늦게 발견된다.

**왜 `check-env.sh`가 못 잡는가**: 3-1 앱↔compose 대조가 `application-prod.yml`만 읽는데
소셜 설정은 `application.yml`에 있다. 검사 사각지대다.

**조치** — `infra/docker-compose.yml` backend `environment`에 2줄 추가.

```yaml
KAKAO_APP_ID: ${KAKAO_APP_ID:-}
GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:-}
```

`:-` 기본값을 붙인 이유는 **콘솔 앱이 아직 없어 값이 비어 있기 때문**이다. 기본값 없이
`${KAKAO_APP_ID}`로 쓰면 `check-env.sh` 3)번이 필수 변수로 뽑아 **빈 값 → 배포 실패**가 된다.
(확인: 추가 후에도 필수 변수 목록 15개 그대로, 두 변수 미포함)

누락 경위를 주석으로 함께 남겼다 — 특히 "check-env가 잡지 못하므로 병합 중 유실 주의".

#### 발견 2 — `.env.example`에 쓰지 않는 secret 항목이 남아 있었다

`KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` / `GOOGLE_CLIENT_SECRET` 3줄.
`auth-social.md`가 "이 흐름에서 쓰지 않는다"고 명시한 값들이다.
실제로 이 줄들 때문에 "카카오 콘솔의 Client Secret을 넣어야 하냐"는 혼동이 발생했다.

**조치** — 3줄 제거. 제거 이유와 "서버 리다이렉트 방식으로 바꾸면 그때 다시 추가"를 주석으로 남겼다.
`KAKAO_APP_ID`·`GOOGLE_CLIENT_ID`에는 발급 위치와 **비었을 때의 증상**을 적었다.

#### 검증

docker가 이 WSL 배포판에 붙어 있지 않아 `docker compose config`를 쓸 수 없었다.
PyYAML로 파싱해 backend environment 18개 키와 두 변수의 값 형태를 확인했다.
**EC2에서 실제 배포로는 아직 검증하지 않았다.**

#### 확정된 값 (문서 갱신 필요)

| 항목 | 값 |
| --- | --- |
| 엔드포인트 | `POST /api/v1/auth/social/{provider}` (`kakao` \| `google`) |
| 카카오 검증 | access token → `GET https://kapi.kakao.com/v2/user/me` |
| 구글 검증 | ID token → JWK 서명 + `iss`/`aud` |
| 서버 필요 값 | `KAKAO_APP_ID`(선택), `GOOGLE_CLIENT_ID`(구글 쓰려면 필수) |
| FE 필요 값 | 카카오 **네이티브 앱 키** (서버와 무관, APK에 박힘) |
| 아웃바운드 | `kapi.kakao.com`, `www.googleapis.com` HTTPS 필요 — **미확인** |

### 18. `GOOGLE_CLIENT_ID` EC2 `.env` 적용 (10:53) — 운영 파일 쓰기

구글 OAuth 클라이언트 2개(Android·웹)가 생성되어 값을 받았다. 두 ID 모두 같은 프로젝트 번호
`184017850372` 계열이고, 백엔드가 `aud ∩ client-ids`로 보므로 **쉼표로 둘 다** 넣었다.

```bash
cp -p .env .env.bak.20260916-google-client-id   # 백업 먼저
# GOOGLE_CLIENT_ID= → 두 클라이언트 ID를 쉼표로
bash infra/scripts/check-env.sh                 # → 통과 (필수 변수 15개)
```

**아직 백엔드에 전달되지 않는다.** EC2에 배포된 `docker-compose.yml`에는 소셜 변수 2줄이 없다
(17절 수정이 아직 push·머지되지 않았다). `.env`에 값만 들어간 상태이고,
**배포가 한 번 돌아야 컨테이너가 이 값을 받는다.**

#### 함께 발견한 것 — `.env`에 이미 카카오 값이 들어와 있었다 (10:12 수정됨)

```
KAKAO_CLIENT_ID=9d60...(32자리 hex)   ← 아무 코드도 읽지 않는 변수
KAKAO_CLIENT_SECRET=                   ← 〃
KAKAO_APP_ID=1573091                   ← 백엔드가 읽는 값. 정상
GOOGLE_CLIENT_SECRET=                  ← 아무 코드도 읽지 않는 변수
```

카카오 콘솔 앱은 이미 생성된 것으로 보인다(앱 ID `1573091`). `KAKAO_CLIENT_ID`의 32자리 값은
**카카오 앱 키 형식**인데, 이 이름의 변수를 읽는 코드가 없어 아무 효과가 없다.
그 값이 네이티브 앱 키라면 FE가 `app.json`에 넣어야 할 값이다 — 확인 필요.

`.env.example`에서는 17절에 미사용 3줄을 제거했지만, **운영 `.env`의 해당 줄은 지우지 않았다**
(값이 들어 있어 임의 삭제하지 않음). 정리 여부는 사용자 확인 후.

### 19. 운영 `.env` 미사용 소셜 항목 제거 (11:00)

18절에서 발견한 `KAKAO_CLIENT_ID`의 32자리 값은 **카카오 REST API 키**로 확인됐다(사용자 확인).
우리 흐름에서 서버가 카카오에 하는 호출은 `GET /v2/user/me`에 사용자 access token을 Bearer로
싣는 것뿐이라 앱 키를 실을 자리가 없다. 3줄을 제거했다.

```bash
cp -p .env .env.bak.20260916-remove-unused-social
# KAKAO_CLIENT_ID / KAKAO_CLIENT_SECRET / GOOGLE_CLIENT_SECRET 제거
chmod 600 .env
bash scripts/check-env.sh   # → 통과 (필수 변수 15개)
```

제거 후 운영 `.env`의 소셜 항목은 백엔드가 실제로 읽는 2개만 남았다.

```
KAKAO_APP_ID=1573091
GOOGLE_CLIENT_ID=<Android>,<웹>   (184017850372- 계열 2개)
```

이로써 `.env.example`(17절 커밋)과 운영 `.env`의 구성이 일치한다.

**카카오 네이티브 앱 키는 아직 받지 않았다.** 서버와 무관하고 FE가 `app.json`에 넣을 값이다.

### 20. 카카오·구글 아웃바운드 연결성 확인 (11:0x) — 조회만

`auth-social.md`가 요구하는 두 호스트로 EC2 호스트와 backend 컨테이너 양쪽에서 확인했다.
금융망·OpenAI 때(7절 계열)와 같은 절차다.

| 대상 | EC2 호스트 | backend 컨테이너 |
| --- | --- | --- |
| `https://kapi.kakao.com/v2/user/me` | HTTP 401 (0.06s) | HTTP 401 (0.05s) |
| `https://www.googleapis.com/oauth2/v3/certs` | HTTP 200 (0.08s) | HTTP 200 (0.08s) |

**네트워크 차단 없음.** 카카오의 401은 토큰 없이 호출했으므로 정상이다(요청이 카카오까지
도달해 인증 단계에서 거절된 것). 구글 JWK는 인증이 필요 없어 200이다.

→ 아웃바운드 화이트리스트 등록이나 방화벽 작업은 필요 없다. 소셜 로그인이 실패한다면
네트워크가 아니라 설정(클라이언트 ID·키 해시·동의항목) 쪽을 봐야 한다.

### 21. 카카오 이메일 동의항목 사용 불가 — 확인 및 결정 (11:2x)

동의항목 화면에서 `카카오계정(이메일)`이 **"권한 없음"**이고, 신청 버튼에
**"이 앱은 비즈 앱이 아닙니다. 비즈 앱으로 전환 후 신청 자격을 확인할 수 있습니다"**로 막혔다.

**비즈니스 인증 ON ≠ 비즈 앱이다.** 인증이 켜져 있어도 앱 유형이 비즈 앱이 아니면
신청 자격 확인까지 가지 못한다. 비즈 앱 전환에는 카카오톡 채널 연결과
사업자 정보 등록(사업자등록번호)이 필요한데, 교육 프로젝트라 쓸 수 있는 사업자 정보가 없다.

**결정: 카카오는 이메일 없이 간다.** 로그인 기능 자체는 영향받지 않는다.

- 카카오 로그인·토큰 발급·서버 검증·사용자 식별(닉네임) → 전부 정상
- 카카오 가입자는 **기본값으로** 대체 이메일 `kakao.{해시20자}@social.dodam.invalid`
- 기존 이메일 회원과 자동 연결 안 됨, 가입 시 금융망 등록 시도 안 함(`finance_connection` FAILED)
- 구글은 제약 없음 — 실제 이메일 수집됨

백엔드가 이 경로를 정상 경로로 구현해 두었다(`auth-social.md` 통합 테스트 1번 케이스)
→ **코드 변경 없음.** 시연에서 이메일 연결이 필요한 시나리오는 구글 로그인으로.

#### `external-integrations.md` 4절 갱신 (함께 처리)

결정을 남기면서 남은 것에 있던 4절 갱신도 같이 했다. 구식 표기가 새 내용 옆에 남아 있으면
오히려 오해를 키우기 때문이다.

| 갱신 | 이전 → 이후 |
| --- | --- |
| 엔드포인트 | `/api/v1/auth/oauth/{provider}` → `/api/v1/auth/social/{provider}` |
| 카카오 검증 | "access token 또는 OIDC" → **access token 확정**, OIDC 미채택 |
| 환경변수 | `KAKAO_APP_KEY`(앱 키) → `KAKAO_APP_ID`(숫자 앱 ID) |
| 〃 | `GOOGLE_OAUTH_CLIENT_ID` → `GOOGLE_CLIENT_ID` |
| 이메일 동의항목 | "닉네임/이메일 등" → **사용 불가** 절 신규 |
| 발급 현황 | "미발급 2건" → 실제 상태(앱 ID·클라이언트 설정 완료, FE 구현 대기) |
| 연결성 | 20절 실측 결과 추가 |

### 22. 배포 반영 확인 — 소셜 변수가 컨테이너에 실제로 들어갔다 (11:3x)

17절 수정이 develop에 머지되어 배포됐다. **겉보기 정상으로 넘어가지 않도록 컨테이너 안에서
직접 확인했다** (compose에 줄이 있는 것과 컨테이너가 값을 받은 것은 별개다).

```
$ docker exec backend env | grep -E "KAKAO_APP_ID|GOOGLE_CLIENT_ID"
KAKAO_APP_ID=1573091
GOOGLE_CLIENT_ID=184017850372-1nhf...,184017850372-npns...
$ docker inspect -f "{{.State.StartedAt}} {{.State.Health.Status}}" backend
2026-09-16T02:33:49Z healthy
```

→ **구글 로그인 503(`SOCIAL_PROVIDER_UNAVAILABLE`) 조건이 해소됐다.** 서버 쪽 준비 완료.
이제 실패한다면 네트워크(20절 확인됨)도 설정 주입(이 절)도 아니고, 콘솔 등록이나 FE 구현이다.

두 구글 클라이언트의 정체도 확정됐다 — `...1nhf...`가 **웹**, `...npns...`가 **Android**.
`.env`에는 둘 다 쉼표로 넣어 두었으므로 어느 쪽으로 발급된 토큰이든 `aud` 검증을 통과한다.

### 23. 카카오 콘솔 설정 완료 (사용자 직접)

| 항목 | 설정값 |
| --- | --- |
| 플랫폼 → Android | 패키지명 `com.ssafy.d105.dodam` + 키 해시 등록 |
| 카카오 로그인 사용 | **ON** |
| OpenID Connect | **OFF** (우리는 access token 검증 방식이므로 정상) |
| 동의항목 — 닉네임 | **필수 동의** (선택 동의로 켰다가 같은 날 필수로 전환) |
| 동의항목 — 이메일 | 사용 불가 (21절) |

**이로써 콘솔·서버 쪽 준비가 모두 끝났다. 남은 것은 FE 구현뿐이다.**

**닉네임을 필수 동의로 전환한 이유**: 이메일을 받을 수 없는 상태(21절)에서 닉네임까지 비면
카카오 가입자가 `도담 회원` + 대체 이메일로만 식별되어 시연에서 구분이 안 된다.
필수 동의는 거부 시 로그인 자체가 불가해지지만, 이름 없이 가입되는 쪽이 더 나쁘다고 판단했다.
→ 회원 이름이 `도담 회원`으로 들어가는 경우는 이제 발생하지 않는다.

#### FE가 실패를 보고할 때의 1차 분류

| 증상 | 원인 |
| --- | --- |
| 로그인 화면은 뜨는데 **앱으로 복귀 못 함** | AndroidManifest의 `kakao{네이티브앱키}://oauth` intent-filter 누락 |
| 카카오가 **로그인 자체를 거부** | 키 해시 불일치 (그 사람 빌드의 SHA-1이 콘솔에 없음) |
| 서버가 **401 `SOCIAL_TOKEN_INVALID`** | 토큰 만료·위조, 또는 다른 앱의 토큰(`KAKAO_APP_ID` 대조) |
| 서버가 **503 `SOCIAL_PROVIDER_UNAVAILABLE`** | 카카오 API 장애·타임아웃 (네트워크는 20절에서 확인됨) |

### 24. `ddl-auto` 임시 조치 제거 — `validate` 복귀 (16:1x)

2026-09-08부터 compose에서 `SPRING_JPA_HIBERNATE_DDL_AUTO: none`으로 덮어쓰고 있던
임시 조치를 제거했다. 이제 `application-prod.yml`의 `ddl-auto: validate`가 그대로 적용된다.

**그냥 빼지 않고 3단계로 확인했다.** 이 설정이 어긋나면 백엔드가 기동 자체를 못 하고
재시작 루프 → 외부 502가 된다(09-08에 실제로 겪었다).

**1) 원인 해소 여부** — 임시 조치를 넣은 이유는 엔티티에 대응하는 Flyway 마이그레이션이
없었기 때문이다. 현재는 들어와 있다.

```
운영 DB flyway_schema_history : 총 25건, 실패 0건, 최신 V35 (2026-09-16 15:20 적용)
레포 db/migration             : 25개 (V1~V19, V30~V35)
```

**2) 운영 DB 대상 validate 드라이런** — 실행 중인 backend는 건드리지 않고 일회용 컨테이너로
스키마 대조만 했다. `SPRING_FLYWAY_ENABLED=false`로 두어 **DB를 변경하지 않는다.**

```bash
docker compose run --rm --no-deps \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
  -e SPRING_FLYWAY_ENABLED=false \
  backend
```

결과: `Schema-validation` / `SchemaManagementException` **0건**,
`Started DodamApplication in 16.271 seconds` · `Tomcat started on port 8080` 정상 기동.
(종료 코드 124는 `timeout`이 끊은 것으로, 앱이 계속 떠 있었다는 뜻이다)

**3) compose 수정 후 정적 확인** — PyYAML 파싱 OK, `SPRING_JPA_HIBERNATE_DDL_AUTO` 잔존 없음.

주석은 지우지 않고 **"해소됨"으로 바꿔 경위를 남겼다.** 같은 문제가 재발했을 때
"예전에 왜 이걸 껐었는지"를 다시 파헤치지 않도록.

> ⚠️ **적용 시점은 다음 배포다.** 지금부터 다음 배포 사이에 마이그레이션 없는 엔티티가
> 들어오면 그 배포에서 기동 실패가 난다. 드라이런은 지금 시점의 스키마에 대한 것이다.

#### 곁다리 확인 — `OPENAI_API_KEY`가 backend environment에 추가돼 있었다

develop 병합으로 들어온 변경이다. check-env의 필수 변수가 15개 → 16개가 됐고,
`.env`에 이미 설정돼 있어 11:33 배포가 정상 통과했다. 조치 불필요.

## 남은 것
- [ ] 이 수정 develop 머지 후 첫 배포에서 `infra-backend:deployed` 태그 생성 확인
- [ ] 롤백 경로 실제 검증 — 일부러 깨는 커밋으로 "코드+이미지가 함께 되돌아오는지" 확인
      (지금까지 검증된 것은 코드 롤백까지다)
- [ ] `docker-gc.sh` EC2 1회 수동 실행 + cron 등록 (스크립트 커밋 완료, **운영 미적용** — 위 수정이 배포된 뒤에 실행)
- [ ] 위 커밋 push 및 develop 머지 (머지 시 nginx reload로 레이트 리밋 수정도 함께 반영)
- [ ] 배포 후 로그인 레이트 리밋이 실제로 걸리는지 확인 (연속 요청으로 429 유도)
- [x] ~~`backup.sh` SIGPIPE 버그 수정~~ — 완주 표식 검증으로 교체, 11:53 백업 성공으로 공백 해소 (16절)
- [x] ~~`*.sql.gz.partial` 보관 정리 대상 포함~~ — 16절에서 함께 수정
- [ ] **cron 작업 실패를 알릴 경로가 없다** — 09-15 백업 실패가 `backup.log`에만 남고 아무도 몰랐다. `container-watch.sh`는 컨테이너만 본다
- [ ] 시연 비밀번호를 `backend/database/seeds/README.md`에 문서화 (**백엔드 담당자**)
- [ ] `DODAM_DEMO_PASSWORD_SEED.sql`을 레포에 포함 (**백엔드 담당자**)
- [ ] FE가 Expo 웹으로 붙을 계획이면 `CORS_ALLOWED_ORIGINS` 추가 (현재 `http://localhost:8081` 하나)
- [x] ~~develop 빌드 실패(#75·#76)~~ — #77(10:31)·#78(10:39) SUCCESS로 복구됨
- [x] ~~V18·V19 미적용~~ — #78 배포 기동 시 V18·V19·V31 적용, 현재 v31
- [ ] 시드 데이터 적재 — 1번(금융상품)은 선행 조건 충족, 즉시 가능 (**백엔드 담당자 진행**)
- [x] ~~17절 커밋 push·develop 머지·배포~~ — 22절에서 컨테이너 환경변수 주입까지 확인
- [x] ~~`KAKAO_CLIENT_ID` 값 정체 확인~~ — REST API 키였다(불필요, 19절에서 제거)
- [ ] **카카오 네이티브 앱 키 확보 후 FE 전달** — 앱 설정 → 앱 키. 서버·`.env`와 무관
- [x] ~~운영 `.env`의 미사용 3줄 정리~~ — 19절에서 제거, check-env 통과
- [ ] `check-env.sh` 3-1 대조에 `application.yml`도 포함 (17절 발견 1의 사각지대)
- [x] ~~`external-integrations.md` 4절 갱신~~ — 21절에서 처리
- [x] ~~카카오 콘솔 설정(Android 플랫폼·로그인 ON·닉네임 동의)~~ — 23절, 사용자 직접 완료
- [x] ~~구글 동의 화면 **테스트 사용자** 등록~~ — 2026-09-16 완료(사용자 직접). 이후 합류하는 팀원은 그때마다 추가 필요
- [x] ~~EC2 → `kapi.kakao.com` / `www.googleapis.com` 아웃바운드 연결성 확인~~ — 20절, 호스트·컨테이너 모두 도달 확인
- [ ] 카카오·구글 콘솔 앱 등록 후 `.env`에 `KAKAO_APP_ID`·`GOOGLE_CLIENT_ID` 채우고 backend 재기동
- [x] ~~`SPRING_JPA_HIBERNATE_DDL_AUTO: none` 제거하고 `validate` 복귀~~ — 24절, 드라이런 통과 후 제거. **다음 배포에서 기동 확인 필요**
- [ ] 다음 배포에서 backend가 `validate`로 정상 기동하는지 확인 (실패 시 즉시 롤백 대상)
