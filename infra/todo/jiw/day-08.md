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

## 남은 것
- [ ] 이 수정 develop 머지 후 첫 배포에서 `infra-backend:deployed` 태그 생성 확인
- [ ] 롤백 경로 실제 검증 — 일부러 깨는 커밋으로 "코드+이미지가 함께 되돌아오는지" 확인
      (지금까지 검증된 것은 코드 롤백까지다)
- [ ] `docker-gc.sh` EC2 1회 수동 실행 + cron 등록 (스크립트 커밋 완료, **운영 미적용** — 위 수정이 배포된 뒤에 실행)
- [ ] 위 커밋 push 및 develop 머지
- [x] ~~develop 빌드 실패(#75·#76)~~ — #77(10:31)·#78(10:39) SUCCESS로 복구됨
- [x] ~~V18·V19 미적용~~ — #78 배포 기동 시 V18·V19·V31 적용, 현재 v31
- [ ] 시드 데이터 적재 — 1번(금융상품)은 선행 조건 충족, 즉시 가능 (**백엔드 담당자 진행**)
- [ ] `SPRING_JPA_HIBERNATE_DDL_AUTO: none` 제거하고 `validate` 복귀 — **선행 조건 충족됨**(V18·V19 적용 완료)
