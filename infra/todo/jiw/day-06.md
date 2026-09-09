# Day 6 — 실제 진행 기록

계획: `infra/todo/day-06.md`, 보안 방침: `infra/todo/security.md`

## 2026-09-09

### 착수 방침 — 대시보드보다 알림
계획서에 이미 "대시보드보다 알림이 우선"이라고 적혀 있었고, Day 3~5에 겪은 장애가 그 판단을 뒷받침했다.

| 시점 | 장애 | 인지 경로 |
| --- | --- | --- |
| 09-07 | 마이그레이션 누락 → 502 | 다른 작업 중 우연히 발견 |
| 09-08 ① | compose 환경변수 유실 → 502 | 우연히 발견 |
| 09-08 ② | 같은 원인 재발 → 502 | 우연히 발견 |
| 09-08 ③ | `NUMBER_HASH_KEY` 누락 | `check-env.sh`가 차단 (장애 아님) |

**세 번의 장애 모두 "빌드는 빨간불이었지만 아무도 안 봤다"** 였다. 관측 수단이 없어서가 아니라 **통보 수단이 없어서** 늦었다. 그래서 순서를 알림 → 수집 → 대시보드로 잡았다.

### 1. 알림 채널 결정
사용자와 협의해 **Mattermost**(SSAFY 제공)로 결정. Incoming Webhook은 사용자가 생성.

`.env`에 값을 넣는 과정에서 문제가 하나 있었다.
```
38:MATTERMOST_WEBHOOK_URL=
39:https://meeting.ssafy.com/hooks/...
```
키와 값이 **다른 줄로 분리**되어 들어가서, `.env`를 source할 때 셸이 39행을 명령어로 해석해 `No such file or directory` 오류가 났다. 값은 빈 상태였다.
→ 두 줄을 합치고 `chmod 600` 복원. 전송 테스트 HTTP 200, 응답 `ok` 확인.

> 참고: 이 과정에서 오류 메시지에 webhook URL이 노출됐다. 채널에 메시지를 보낼 수 있는 값이므로, 신경 쓰인다면 재발급을 권고했다.

### 2. 수집 스택 구축
`prometheus / node-exporter / cadvisor / loki / promtail / grafana` 6개를 compose에 추가.

**보안 원칙은 Jenkins와 동일하게 적용했다** — 노출면 최소화.
- 전부 컨테이너 네트워크 안에서만 통신, **호스트 포트를 바인딩하지 않음**
- 외부에 열리는 것은 nginx의 `/grafana/` 하나뿐
- 각 서비스에 `security_opt: no-new-privileges`, `mem_limit` 적용
- prometheus는 `nobody(65534)`, loki는 `10001`로 비루트 실행
- 검증: 9090/3000/3100/9100 포트가 외부에서 전부 접근 불가함을 확인

**Grafana만 공개한 판단 근거**: 자체 로그인이 있고 `docker.sock` 접근권이 없다. 익명 접근과 회원가입은 컨테이너 환경변수로 차단했다. Jenkins는 docker.sock 때문에 뚫리면 호스트가 넘어가므로 성격이 다르다.

**데이터소스·대시보드는 파일로 프로비저닝**했다. UI에서 만들면 컨테이너를 다시 만들 때 사라진다.

### 3. 백엔드 메트릭 — 선행조건이 계획서보다 하나 더 많았다
계획서에는 백엔드팀 요청사항이 2개로 적혀 있었다(micrometer 의존성, actuator 노출). 실제로 확인해보니 `/actuator/prometheus`가 **404가 아니라 401**을 반환했다.

```java
// SecurityConfig.java
.requestMatchers(HttpMethod.GET, "/api/health", "/actuator/health").permitAll()
// 그 외 authenticated() → /actuator/prometheus 는 401
```

Spring Security가 막고 있어서, **의존성과 노출 설정을 추가해도 여전히 수집이 안 된다.** 요청사항을 3개로 정정했다.
1. `build.gradle`에 `micrometer-registry-prometheus`
2. `application.yml`의 `exposure.include`에 `prometheus`
3. **`SecurityConfig`에서 `/actuator/prometheus` permitAll**

3번이 보안상 괜찮은 이유: nginx가 `/actuator/`를 외부에서 404로 차단하므로 Prometheus는 컨테이너 네트워크로만 접근한다.

### 4. 알림 규칙과 채널
**규칙 선정 기준을 먼저 정했다**: *"이게 울리면 사람이 반드시 뭔가 해야 하는가?"*
그렇지 않은 지표는 대시보드로만 보고 알림으로 만들지 않았다. 울려도 아무도 행동하지 않는 알림이 쌓이면 진짜 알림까지 무시하게 되기 때문이다.

| 규칙 | 조건 | 유예 |
| --- | --- | --- |
| 수집 대상 다운 | `up == 0` | 3분 (재시작 중 깜빡임 제외) |
| 디스크 여유 부족 | 15% 미만 | 10분 |
| 호스트 메모리 부족 | 가용 10% 미만 | 10분 |

`repeat_interval`은 4시간. 너무 잦으면 무시하게 되고 너무 뜸하면 잊어버린다.

**검증 중 규칙 오류 2건을 잡았다.**

1. **디스크 규칙이 `nodata`** — node-exporter의 실제 mountpoint가 `/host`가 아니라 `/` 였다.
   문서만 보고 넘어갔다면 **평생 안 울리는 알림**이 될 뻔했다. 알림은 "설정했다"가 아니라 "실제로 값이 나오는가"까지 확인해야 한다.
2. **"수집 대상 다운"이 상시 `pending`** — `backend` job이 401로 계속 down이라 **영구적으로 켜져 있는 알림**이 된다.
   늘 울리는 알림은 사람들이 무시하게 되어 진짜 알림까지 묻히므로, 백엔드 선행작업이 끝날 때까지 `up{job!="backend"}`로 제외했다. 제외를 풀어야 할 시점을 주석에 명시했다.

**알림 실동작 검증** — 서비스에 영향 없는 `cadvisor`를 대상으로 전체 사이클을 돌렸다.
```
13:44:47  cadvisor 정지
13:45~47  pending (for: 3m 유예 중)
13:48:44  firing  → 로그: "Sending alerts to local notifier count=1"
13:48:53  cadvisor 재기동
13:49:59  inactive → 해제 알림 발송
```

### 5. Jenkins 배포 실패 알림
`Jenkinsfile`의 `post` 블록에서 Mattermost로 통보한다.

**알림 정책에 두 가지 판단을 넣었다.**
- **성공은 "복구됐을 때만" 알린다.** 매번 성공을 알리면 노이즈가 되어 정작 실패 알림을 안 읽게 된다. `currentBuild.previousBuild.result`가 실패였을 때만 보낸다.
- **실패 알림에 서비스 상태를 함께 조회해 붙인다.** 롤백이 동작하면 배포는 실패해도 서비스는 정상이므로 대응 긴급도가 다르다.
  - 🟢 "서비스는 정상입니다 (롤백 동작 또는 배포 전 차단)"
  - 🔴 "**서비스도 응답하지 않습니다 — 즉시 확인 필요**"

webhook URL은 `.env`에서 읽고 해당 구간은 `set +x`로 명령 에코를 껐다. Jenkins 로그에 남으면 안 되기 때문이다.
검증: Jenkins 컨테이너 안에서 동일 로직을 실행해 HTTP 200 확인.

### 6. cAdvisor 제거 — 데이터를 못 내놓는 수집기
대시보드를 만든 뒤 **각 패널의 쿼리가 실제로 데이터를 반환하는지 확인**하다가 문제를 발견했다. 컨테이너 관련 패널이 전부 비어 있었다.

```
container_memory_usage_bytes{id="/"}          ← id 라벨만 있고 name 이 없음
전체 49개 시계열 / name 라벨 있음 0개
```

로그를 보니 원인이 나왔다.
```
Failed to create existing container: /system.slice/docker-<id>.scope:
  failed to identify the read-write layer ID
  open /rootfs/var/lib/docker/image/overlayfs/layerdb/mounts/<id>/mount-id: no such file or directory
```

**Docker 29의 새 `overlayfs` 스토리지 드라이버**를 cAdvisor가 모른다. 예전 `overlay2` 경로를 찾다가 실패해 컨테이너 객체 생성 자체가 안 된다.

**4가지를 시도했다.**
1. `/sys/fs/cgroup` 마운트 추가 (cgroup v2) → 컨테이너 cgroup은 찾았으나 여전히 생성 실패
2. `--docker_only=true` → 동일
3. `--disable_metrics=disk,diskIO` (layerdb 조회 회피 기대) → 동일
4. v0.49.1 → **v0.52.1 업그레이드** → 동일

전부 실패. **제거를 택했다.**
데이터를 내놓지 못하는 수집기를 남겨두면 대시보드가 빈 채로 유지되고 **"모니터링되고 있다"는 착각만** 남는다. 그게 모니터링이 아예 없는 것보다 나쁘다. `prometheus.yml`에 제거 사유를 기록했다.

### 7. cAdvisor 대체 — `container-watch.sh`
컨테이너 이상 감지라는 **목적은 살리되 수단을 바꿨다.** docker에 직접 물어본다.

감지 대상
- 실행 중이어야 하는데 멈춘 컨테이너
- health check가 `unhealthy`
- **재시작 횟수가 5분간 임계치 이상 증가** (크래시 루프 — 우리가 세 번 겪은 그 패턴)

재시작 횟수를 상태 파일에 기록해 직전 실행과 비교한다. cron으로 5분마다 실행.

**이게 필요한 또 다른 이유**: Prometheus의 `up` 알림은 **스크레이프 대상만** 본다. `mysql`, `redis`, `backend`는 수집 대상이 아니므로 그쪽 이상은 잡지 못한다. 이 스크립트가 그 공백을 메운다.

검증: `certbot` 정지 → 알림 전송 확인 → 재기동 → 정상 복귀.

### 8. 대시보드
cAdvisor 제거에 맞춰 **실제 수집되는 메트릭 기준으로 다시 만들었다.**

| 대시보드 | 내용 |
| --- | --- |
| 서비스 개요 | 상태 요약 3종(수집 대상/디스크/메모리) + CPU·메모리 추이 + **최근 에러 로그** |
| 호스트 리소스 | CPU / 메모리 / 디스크 여유 / 디스크 I/O / 네트워크 |
| 로그 | 컨테이너 필터(변수) + 발생량 그래프 + 로그 뷰 |

모두 파일 프로비저닝이라 컨테이너를 다시 만들어도 유지된다.

### 9. 배포 대상 누락 발견 — 사용자 질문에서 나온 수확
사용자가 *"docker image가 바뀌면 어떤 식으로 진행되냐"* 고 물어서 확인하다가 버그를 찾았다.

**모니터링 5개를 compose에 추가하면서 `deploy.sh`의 `DEPLOY_SERVICES` 갱신을 빠뜨렸다.**

`docker compose up`은 인자로 준 서비스만 처리하므로, 목록에 없는 서비스는 배포가 **아예 건드리지 않는다.** 그 결과:
1. Grafana 설정을 고쳐 머지해도 배포가 무시한다
2. 수동으로 올린 변경은 다음 배포의 `git reset --hard`에 밀려 **조용히 원복**된다
3. 파일과 실제 컨테이너 상태가 어긋난 채 방치된다 — Day 3에서 겪은 것과 같은 패턴

→ 모니터링 5개를 목록에 추가하고, **재발 방지로 자동 감지**를 넣었다.
```
[deploy] WARN: 'grafana' 가 compose에 있으나 배포 대상이 아닙니다 — DEPLOY_SERVICES 확인 필요
```
compose의 전체 서비스 목록과 대조해 누락을 배포 로그에 드러낸다. 검증 시 누락 6건을 정확히 검출했다.

`jenkins`만 의도적으로 제외한다 — 배포가 Jenkins 안에서 돌기 때문에 목록에 넣으면 자기 자신을 죽인다(Day 3 실제 사고).

### 10. 최종 검증
머지 후 배포(빌드 #37)가 완주하며 지금까지 만든 것이 모두 동작함을 확인했다.
```
[deploy] 롤백 지점: 1247b18 + 이미지 보존됨            ← Day 5 롤백 준비
[check-env] 통과 (필수 변수 15개 확인)                 ← Day 4 사전 검사
[deploy] 기동 (... prometheus node-exporter loki promtail grafana)  ← Day 6 배포 목록
[deploy] ① readiness 검사 — {"status":"UP"}            ← Day 5 2단계 검증
[deploy] ② end-to-end 검사 통과
[deploy] 배포 성공
```
`WARN`이 없다는 것은 compose 서비스와 배포 목록이 일치한다는 뜻이다.

| 항목 | 결과 |
| --- | --- |
| 컨테이너 | 11개 정상 (cadvisor 완전 제거) |
| 외부 API / Grafana / Jenkins | 200 / 200 / 403(차단) |
| cron | 일일 백업 + 5분 컨테이너 감시 |
| Prometheus 수집 | node, prometheus UP / backend DOWN(선행작업 대기) |
| 알림 규칙 | 3종 inactive |

## 현재 알림 체계

| 경로 | 감지 대상 | 검증 |
| --- | --- | --- |
| Jenkins → Mattermost | 배포 실패 / 실패 후 복구 (+서비스 상태 동반 표시) | ✅ 전송 경로 |
| Grafana → Mattermost | 수집 대상 다운, 디스크 15%, 메모리 10% | ✅ 발생·해제 사이클 |
| cron → Mattermost | 컨테이너 정지 / unhealthy / 크래시 루프 | ✅ 실동작 |

## 알려진 공백
- **컨테이너별 CPU/메모리 시계열 없음** — cAdvisor가 Docker 29를 지원하지 않아 확보 실패.
  이상 감지는 `container-watch.sh`가 커버하지만, "어느 컨테이너가 메모리를 얼마나 쓰는지" 추이는 `docker stats`로 봐야 한다.
  향후 Docker 스토리지 드라이버 변경이나 대체 exporter 도입 시 재검토.
- **백엔드 JVM 메트릭 없음** — 백엔드팀 선행작업 3건 대기 중.

## 남은 것
- [ ] 백엔드팀 요청 3건 (micrometer / actuator 노출 / SecurityConfig) (**사용자 액션**)
- [ ] 반영 후 `prometheus.yml`의 backend job과 알림 규칙의 `job!="backend"` 제외 조건 해제
- [ ] Grafana 팀원용 Viewer 계정 필요 여부 결정 (**사용자 액션**)
