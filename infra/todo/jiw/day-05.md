# Day 5 — 실제 진행 기록

계획: `infra/todo/day-05.md`, 보안 방침: `infra/todo/security.md`

## 2026-09-08

### 착수 배경 — 이틀간 세 차례의 동일 패턴 장애
Day 5의 원래 우선순위는 "시크릿 관리 정식화"였으나, 실제로 겪은 장애가 우선순위를 바꿨다.

| 시점 | 원인 | 증상 |
| --- | --- | --- |
| 09-07 | 상품 API MR이 엔티티만 병합하고 마이그레이션 누락 | `validate` 실패 → 재시작 루프 → 502 |
| 09-08 ① | Auth MR 병합 중 compose 환경변수 4줄 유실 | `jwt.secret must be at least 32 bytes` → 502 |
| 09-08 ② | ①을 고쳤으나 develop에 머지되기 전 다른 MR이 배포를 유발 | 4줄 다시 유실 → 502 |

**세 번 모두 같은 구조**였다.
1. 배포가 컨테이너를 교체한다
2. 새 컨테이너가 기동에 실패한다
3. **깨진 컨테이너가 그대로 남는다** — 이전의 정상 컨테이너는 이미 사라진 뒤다
4. 파이프라인은 실패를 감지하지만(빌드 FAILURE) 서비스를 되살리지는 못한다
5. 알림이 없어 인지가 늦는다

즉 **"배포 실패 = 서비스 중단"** 이 되는 구조였다. 이걸 먼저 끊는 것이 Day 5의 최우선이라 판단했다.

### 1. 배포 실패 시 자동 롤백
`deploy.sh`에 롤백을 추가했다.

**보존 대상 (동기화 전에 확보)**
- `PREV_COMMIT` — 소스 동기화 **전** 커밋
- `infra-backend:rollback` — 현재 떠 있는 backend 이미지 태그

이미지까지 보존하는 이유: 코드만 되돌리고 다시 빌드하면 시간이 걸리고, **빌드 자체가 깨져 있는 경우 롤백조차 못 한다.**

**동작**
```
헬스체크 실패
  → git reset --hard $PREV_COMMIT     (코드 + compose 설정 복원)
  → docker tag infra-backend:rollback infra-backend:latest
  → docker compose up -d --no-deps --force-recreate   (--build 없이 보존 이미지 사용)
  → nginx reload
  → 롤백 후 헬스체크
  → 결과와 무관하게 exit 1
```

**설계 판단: 서비스는 살리되 빌드는 실패로 끝낸다**
- 서비스가 죽은 채 방치되는 것이 가장 나쁘다 (세 번 다 이 상태였다)
- 그렇다고 롤백 성공을 이유로 빌드를 성공 처리하면 **develop이 깨진 사실을 아무도 모르게 된다**
- 그래서 종료코드는 항상 1. Jenkins는 빨간불, 서비스는 정상.

**한계도 명시했다**
- 롤백은 "직전 배포된 **git 상태**"로 되돌린다 → git에 없는 수동 수정은 보존되지 않는다
- 롤백이 develop을 고쳐주지는 않으므로, 원인을 수정하기 전까지 이후 배포도 계속 실패한다

### 2. `check-env.sh` 사각지대 보완
09-08 장애가 기존 검사를 통과해버린 이유가 있었다.

기존 3)번 검사는 필수 변수 목록을 **`docker-compose.yml`의 `${VAR}` 참조에서 자동 추출**했다. 백엔드가 새 변수를 요구할 때 검사가 자동으로 따라오게 하려는 설계였는데, **compose가 참조 자체를 잃어버리면 "요구하는 변수가 없다"고 판단해 통과시킨다.** 정작 앱은 그 변수를 필요로 하는데도.

→ **출처를 하나 더 둔다.** `backend/src/main/resources/application-prod.yml`의 `${VAR}` 참조를 읽어, compose가 그 이름의 환경변수 키를 실제로 전달하는지 대조한다.

구현 시 주의점 하나: 처음엔 compose에서 `${VAR}` 참조를 찾는 방식으로 짰다가 `DB_URL`, `REDIS_HOST` 등에서 오탐이 났다. 이들은 compose에서 값이 리터럴이거나 다른 변수의 조합이기 때문이다.
```yaml
DB_URL: jdbc:mysql://mysql:3306/${MYSQL_DATABASE}?...   # 키는 DB_URL, 참조는 MYSQL_DATABASE
REDIS_HOST: redis                                        # 리터럴
```
중요한 건 "값을 어떻게 만드는지"가 아니라 **"그 이름의 키를 전달하는지"** 이므로, 키 존재 여부로 판정하도록 고쳤다.

**검증 (3개 시나리오)**
| 시나리오 | 결과 |
| --- | --- |
| 정상 상태 | 통과 (필수 변수 11개) |
| compose에서 `JWT_SECRET` 줄 제거 (09-08 장애 재현) | `application-prod.yml이 요구하는데 전달하지 않는다` 검출 |
| `.env`의 `SSAFY_FINANCE_API_KEY` 비움 (09-07 상황 재현) | `비어 있음` 검출 |

### 3. 롤백 실제 검증
문법 확인만으로는 부족하다고 판단해 실제로 돌렸다. **검증되지 않은 롤백은 없느니만 못하다** — 있다고 믿고 방심하게 만들기 때문이다.

테스트를 위해 `ROLLBACK_HEALTH_URL`을 분리했다. 배포용 헬스체크는 실패시키고 롤백 검증은 정상 URL로 해야 "배포 실패 → 롤백 성공" 시나리오를 재현할 수 있다.

**1차 시도 — 테스트 설계 오류**
호스트에서 `HEALTH_URL=http://nginx/...`로 실행했는데 `nginx`는 도커 네트워크 내부 이름이라 호스트에서 해석되지 않았다(`http_code=000`). 롤백은 실행됐으나 검증 URL도 같은 이유로 실패해 결과를 판단할 수 없었다.

**2차 시도 — 더 중요한 사실이 드러남**
`localhost`로 고쳐 재실행했더니 **롤백은 정상 실행됐는데 서비스가 살아나지 않았다.** 원인은 롤백 버그가 아니라, 당시 **develop 자체가 깨져 있었기 때문**이다(compose 4줄이 유실된 상태). 롤백은 "직전 배포된 git 상태"로 되돌리는데 그 상태가 이미 고장나 있었던 것이다.

이건 롤백이 제 역할을 한 것이자, **"되돌아갈 곳이 성해야 롤백이 의미가 있다"**는 전제를 드러낸 사례다. 로그에도 `★ 롤백까지 실패했습니다`로 정확히 보고됐다.

**3차 시도 — develop 정상화 후**
환경변수 복구 커밋이 develop에 머지되어 빌드 #22가 SUCCESS로 끝난 뒤 다시 검증했다.
```
[deploy] 롤백 지점: e1650ea + 이미지 보존됨
[deploy] 헬스체크: http://localhost/api/does-not-exist
[deploy]   대기중... (1/2, http_code=301)
[deploy] ERROR: 헬스체크 실패
[deploy] 롤백 시작: e1650ea 상태로 되돌립니다
[deploy] 롤백 후 헬스체크: http://localhost/api/health
[deploy] 롤백 성공 — 서비스 정상 (7회차)
[deploy] 배포는 실패했지만 서비스는 직전 상태로 복구되었습니다.
```
- 종료코드 **1** 확인 (빌드는 실패로 표시)
- 롤백 후 backend `healthy`, 외부 헬스체크 **200**
- 커밋이 롤백 지점으로 유지됨 확인

### 반복해서 확인된 문제: 인프라 파일이 백엔드 MR과 충돌한다
`docker-compose.yml`의 backend 환경변수 블록이 **하루에 두 번** 병합 과정에서 유실됐다.
백엔드 MR이 이 파일을 편집하고, 인프라도 같은 파일을 편집하기 때문이다.

대응은 두 층위로 나눴다.
1. **탐지** (완료) — `check-env.sh`가 앱 요구사항과 대조해 배포 전에 차단
2. **예방** (팀 합의 필요) — 백엔드 MR이 인프라 파일을 직접 건드리지 않고, 필요한 환경변수를 인프라 담당에게 요청하는 방식

2번이 근본적이다. 1번은 이미 머지된 뒤에 배포를 막는 것이라 "서비스는 지키지만 develop은 깨진 상태"가 된다.

### 4. 실전 검증 — 만든 가드가 실제 장애를 사전에 막았다
롤백과 앱↔compose 대조 검사가 develop에 머지된 직후, **곧바로 실전 사례가 나왔다.**

빌드 #24, #25가 연속 실패했는데 **서비스는 계속 200으로 정상**이었다. 로그를 보니:
```
[deploy] 롤백 지점: 1635372 + 이미지 보존됨
[deploy] 환경변수 사전 검사
[check-env] ERROR: NUMBER_HASH_KEY 를 application-prod.yml이 요구하는데
                   docker-compose.yml이 전달하지 않는다 (병합 중 유실 의심)
[check-env] 검사 실패 (1건)
[deploy] ERROR: 환경변수 검사 실패
```

백엔드 MR이 새 환경변수(`NUMBER_HASH_KEY`, 계좌·카드번호 HMAC 해시 키)를 요구하기 시작했는데 compose에는 추가되지 않은 상태였다.

**이전이었다면**: 배포가 진행되어 컨테이너가 교체되고, 새 컨테이너가 기동 실패하고, 깨진 컨테이너가 남아 502. 그리고 아무도 모른 채 방치. (09-07, 09-08에 실제로 세 번 겪은 경로)

**이번에는**: 컨테이너를 건드리기 **전에** 중단되어 기존 정상 컨테이너가 그대로 서비스 중. 빌드는 빨간불로 문제를 드러냄. **다운타임 0.**

조치
- 코드 확인: `SensitiveNumberHasher`가 `configuredKey.getBytes(UTF_8)`로 문자열을 그대로 키 재료로 사용 (base64 디코드 아님) → 기존 `.env`의 64자 값은 유효
- `docker-compose.yml`에 `NUMBER_HASH_KEY: ${NUMBER_HASH_KEY}` 추가
- 주석에 **"키가 바뀌면 기존 `*_hash` 값과 매칭이 깨지므로 고정"** 명시 → 이 키는 로테이션 대상이 아니라는 점이 다른 시크릿과 다르다
- 배포 후 확인: check-env 통과(필수 변수 12개), backend healthy, 비루트(`uid=10001`) 유지, 힙 옵션 적용, 외부 200

**의미**: "장애가 안 났다"는 사실 자체는 눈에 띄지 않지만, 이번 건은 **같은 패턴의 네 번째 장애가 될 뻔한 것을 막은 사례**다. 세 번의 장애를 겪고 만든 가드가 네 번째를 막았다는 점에서, Day 5의 우선순위 판단이 옳았음을 보여준다.

### 5. 백엔드 컨테이너 하드닝
- **비루트 실행** ([[security.md]] R3) — `backend/Dockerfile` 런타임 스테이지에 uid 10001 시스템 계정 생성 후 `USER` 전환. 8080은 1024 이상이라 비특권 사용자로도 바인딩 가능
- **JVM 힙 상한** — 기존에는 상한이 없어 JVM이 호스트 메모리의 약 1/4(약 3.8GB)을 최대 힙으로 잡고 있었다. `-XX:MaxHeapSize=1g -XX:InitialHeapSize=256m`로 고정
  - `MaxRAMPercentage` 대신 절대값을 쓴 이유: 컨테이너 메모리 제한을 아직 걸지 않아 JVM이 호스트 전체를 기준으로 계산하기 때문
  - MySQL 버퍼풀 2GB, Jenkins, Day 6 모니터링 스택과의 총량을 고려한 값
- **검증 과정에서의 오측정 기록**: 처음에 `docker exec backend java -XX:+PrintFlagsFinal -version`으로 힙을 재려다 3.8GB라는 값을 봤는데, 이건 **새로 뜬 JVM**이라 ENTRYPOINT 옵션이 붙지 않은 것이었다. `/proc/1/cmdline`로 실행 중인 프로세스의 실제 인자를 확인해야 한다
- 격리 환경(임시 MySQL/Redis)에서 먼저 검증한 뒤 운영에 반영 — 비루트 전환은 파일 권한 문제로 기동이 깨지기 쉬운 변경이라 바로 넣지 않았다

### 6. 헬스체크 엔드포인트 — 일원화하지 않고 역할을 나누기로 결정
계획서에는 "일원화"로 적혀 있었으나, 두 엔드포인트의 성격을 확인한 뒤 **나누는 것이 맞다고 판단**했다.

| 엔드포인트 | 응답 | 성격 |
| --- | --- | --- |
| `/api/health` | `{"success":true,"data":{"status":"UP"}}` | 앱 컨트롤러. **의존성을 검사하지 않음** |
| `/actuator/health` | `{"status":"UP"}` | Actuator. **DB·Redis 상태를 집계** |

- **컨테이너 HEALTHCHECK (liveness)** → `/api/health` 유지
  DB가 잠깐 끊겼다고 컨테이너를 재시작시키면 안 된다. 재시작해도 DB는 안 돌아온다
- **배포 검증 (readiness)** → `/actuator/health`가 더 적합
  "앱은 떴는데 DB에 못 붙는" 배포를 잡아낼 수 있다. 현재 `deploy.sh`는 `/api/health`를 쓰므로 이 경우를 통과시킨다

### 7. 배포 검증을 2단계로 전환
6번 판단을 실제 검증으로 뒷받침한 뒤 `deploy.sh`에 반영했다.

**근거 실측** — MySQL을 `docker pause`로 잠시 멈추고 두 엔드포인트를 비교했다.

| | 평상시 | MySQL 정지 시 |
| --- | --- | --- |
| `/actuator/health` | `{"status":"UP"}` | **실패(타임아웃)** ← 의존성 이상을 감지 |
| `/api/health` | 200 | **200** ← DB가 죽었는데도 정상이라고 응답 |

즉 기존 검증(`/api/health`만 확인)은 **DB에 못 붙는 배포를 성공으로 처리**한다. 실제로 그런 배포가 나가면 헬스체크는 통과하고 API 호출 시점에야 터진다.

**변경**
```
① readiness  : docker exec backend curl .../actuator/health   (의존성 포함)
② end-to-end : curl http://localhost/api/health                (nginx 라우팅 포함)
```
- actuator는 nginx에서 외부 차단(404)돼 있으므로 컨테이너 안에서 직접 호출한다.
  `docker exec`를 쓰면 Jenkins 컨테이너에서든 호스트에서든 같은 방식으로 동작한다
- 실패 메시지도 구분했다 — readiness 실패면 "앱 기동 실패 또는 DB/Redis 접속 실패",
  readiness는 통과했는데 end-to-end가 실패하면 "라우팅 문제 의심"

**검증**: 두 명령을 개별 실행해 각각 `{"status":"UP"}`, `200` 확인.
전체 배포 경로 검증은 이 변경이 develop에 머지된 뒤에 가능하다 —
`deploy.sh`가 `git reset --hard`로 EC2를 develop 기준으로 되돌리기 때문에,
미머지 상태에서는 배포를 돌리는 순간 변경분이 사라진다.
(실제로 테스트 중 이 현상이 발생했고, `check-env.sh`가 `NUMBER_HASH_KEY` 누락을 잡아 배포를 막았다)


### 8. 컨테이너 하드닝 마무리 및 리소스 제한
계획서의 나머지 하드닝 항목을 적용했다.

- **`security_opt: no-new-privileges:true`** — 전 서비스(6개). setuid 바이너리로 권한을 올리는 경로를 차단한다
- **`cap_drop: ALL`** — **backend에만** 적용
  - 이미 비루트(uid 10001)이고 8080 바인딩에 특권이 필요 없어 안전하다
  - nginx(80/443 바인딩), mysql(파일 소유권 변경)은 필요한 capability가 있어 일괄 적용하면 기동이 깨지므로 제외했다. "전부 떨구기"가 항상 옳은 건 아니다
- **`mem_limit`** — 실사용 실측 후 여유를 둔 값

| 서비스 | 실사용 | 상한 | 근거 |
| --- | --- | --- | --- |
| backend | 398MB | 1536m | 힙 1GB + 메타스페이스/스택/네이티브 |
| mysql | 536MB | 3g | 버퍼풀 2GB + 커넥션/임시테이블 |
| jenkins | 1.37GB | 2g | 실사용 기준 여유 |
| redis | 3.7MB | 768m | maxmemory 512mb + 오버헤드 |
| nginx / certbot | 6MB / 2MB | 128m | 여유 충분 |

합계 약 7.5GB로 호스트 15GB 중 절반. Day 6 모니터링 스택 몫을 남겼다.
상한을 두는 이유는 **한 서비스가 폭주해도 다른 서비스를 굶기지 않게** 하기 위함이다. 한도를 넘으면 해당 컨테이너만 OOM으로 종료되고 restart 정책이 되살린다.

### 9. 계획서의 검증 항목 실제 수행
계획서 6번 검증 항목을 하나씩 실제로 돌렸다. **두 개는 내 테스트 방법이 틀려서 두 번 시도했다.**

**① `restart` 정책 자동 복구**
- 1차: `docker kill backend` → 컨테이너가 exited 상태로 남고 복구되지 않음
  - 원인은 정책 문제가 아니라 **`docker kill`이 "사용자가 명시적으로 중단시킨 것"으로 취급**되기 때문. `unless-stopped`는 이 경우 재시작하지 않는 것이 정의된 동작이다
- 2차: `docker exec backend kill -9 1` → 역시 안 죽음
  - 원인: **PID 1은 자기 PID 네임스페이스 안에서 보낸 SIGKILL을 커널이 무시**한다 (핸들러를 등록한 시그널만 받음)
- **실증 근거는 이미 확보되어 있었다**: 09-08 장애 당시 백엔드가 크래시 루프에 빠지며 `RestartCount=604`까지 올라갔다. 프로세스가 스스로 죽을 때 Docker가 계속 되살렸다는 뜻이므로 정책은 정상 동작한다
- 전 서비스 `restart: unless-stopped` 설정 확인 완료

**② 테스트 실패가 배포를 막는지**
격리 복사본에 의도적으로 실패하는 테스트를 추가하고 이미지 빌드를 시도했다.
```
DeliberatelyFailingTest > thisTestMustFail() FAILED
80 tests completed, 1 failed
Execution failed for task ':test'.
→ 빌드 실패
```
빌드가 Dockerfile builder 스테이지에서 `./gradlew clean build`를 돌리므로, **테스트가 깨지면 이미지가 만들어지지 않고 배포 단계로 진행하지 않는다.** Day 2에서 구조만 만들고 확인한 적 없던 항목을 실제로 검증했다.

**③ EC2 재부팅 후 전체 스택 자동 기동**
`sudo systemctl reboot`으로 실제 재부팅했다.

| 항목 | 결과 |
| --- | --- |
| 컨테이너 6개 자동 기동 | ✅ 부팅 후 **26초** 내 전부 Up, backend healthy |
| DB 데이터 | ✅ `user`, `auth_session`, `user_setting`, `financial_profile`, `finance_connection` 등 보존 |
| ufw 규칙 | ✅ 유지 |
| cron (일일 백업) | ✅ 유지 |
| 하드닝 설정 | ✅ `mem_limit`, `cap_drop` 유지 |
| HTTPS | ✅ 200, 인증서 유지(만료 2026-12-06) |
| HTTP → HTTPS | ✅ 301 |
| Jenkins UI 외부 차단 | ✅ 403, 루프백 바인딩 유지 |
| `/actuator/` 차단 | ✅ 404 |
| fail2ban | ✅ active |

**수동 개입 없이 전 구간이 복구**되었다. 서버가 재부팅돼도 서비스가 알아서 돌아온다는 것이 확인됐다.

### 10. `.env` 관리 방식 결정
계획서 2번 항목. 두 선택지를 두고 **현행(EC2에 수동 보관) 유지**로 결정했다.

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| **EC2 수동 보관 (채택)** | 단순함. 배포 경로에 시크릿 생성 로직이 없어 실패 지점이 적다 | EC2가 날아가면 시크릿도 사라짐 |
| Jenkins Credentials에서 배포 시 생성 | 복구력 좋음 | **Jenkins가 단일 실패점**이 된다. 이미 Jenkins는 docker.sock 접근권 때문에 최대 위험 요소(R1)인데, 모든 시크릿까지 쥐게 하면 침해 시 피해가 커진다 |

채택 이유: Jenkins에 시크릿을 몰아주는 것은 **보안 관점에서 오히려 후퇴**다. 대신 단점(EC2 소실 시 시크릿 소실)은 **`backup-secrets.sh`로 서버 밖 백업**을 만들어 보완했다.

## 다음 기록할 것 (TODO)
- [x] 배포 실패 시 자동 롤백
- [x] `check-env.sh` 앱↔compose 대조 검사
- [x] 롤백 실제 동작 검증
- [x] backend 컨테이너 비루트 실행 ([[security.md]] R3)
- [x] JVM 힙 상한 설정 (1GB)
- [x] 헬스체크 엔드포인트 방침 결정 — 일원화 대신 liveness/readiness 역할 분리
- [x] 배포 검증을 2단계(readiness + end-to-end)로 전환
- [x] `security_opt: no-new-privileges` 전 서비스 적용, `cap_drop: ALL` backend 적용
- [x] 컨테이너 리소스 제한(`mem_limit`) 적용
- [x] restart 정책 동작 확인
- [x] 테스트 실패가 배포를 막는지 검증
- [x] EC2 재부팅 후 전체 스택 자동 기동 검증
- [x] `.env` 관리 방식 결정 (현행 유지 + 서버 밖 백업으로 보완)
- [ ] `develop` Protected branch 설정 ([[security.md]] R4, **사용자 액션**)
- [ ] 시크릿 목록 표를 팀 문서로 이관하고 발급 담당자 지정 (**사용자 액션**)
- [ ] 인프라 파일 편집 주체에 대한 팀 합의 (**사용자 액션**)
