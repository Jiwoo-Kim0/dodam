# CPU 프로파일링 절차 (S15P21D105-693)

부하 테스트로 **병목이 "컨트롤러 내부 CPU"까지 좁혀졌지만**, 그 안에서 어느 메서드가
CPU를 쓰는지는 지표로 답할 수 없다. 이 문서는 그것을 재는 방법이다.

> 배경: `load-test-records.md` `#5` — DB 풀·톰캣 스레드·특정 API·필터·응답 크기·GC·디스크를
> 전부 후보에서 제외했고, 지연의 99.6%가 컨트롤러 안에서 발생한다는 것까지 확인했다.

---

## 0. 왜 JFR인가 — 검토한 방법

| 방법 | 장점 | 단점 | 채택 |
| --- | --- | --- | --- |
| **JFR (환경변수)** | JDK 21 내장, **추가 설치 없음**, 오버헤드 1~2%, 이미지 그대로 | 임의 시점 시작/중지 불가. `duration` 안에 부하를 넣어야 한다 | **채택** |
| JFR (jcmd 제어) | 원할 때 시작·덤프. 가장 유연 | **JRE 이미지에는 `jdk.jcmd` 모듈이 아예 없다.** 실행파일만 복사해도 `Module jdk.jcmd not found`로 실패한다(2026-09-23 실증). 런타임을 JDK로 바꿔야 하는데 이미지가 267MB 커지고 디버깅 도구가 상주한다 | 기각 |
| async-profiler | flame graph, safepoint bias 없음, 가장 정확 | `.so`를 이미지에 넣고 `-agentpath`로 로드해야 한다. JFR로 답이 안 나올 때 다음 수단 | 보류 |
| 코드에 타이머 삽입 | 준비 없음 | 어디에 넣을지 모르는 상태라 **찾으려는 것을 미리 알아야 한다.** 순환 논리 | 기각 |
| APM 도입(Pinpoint 등) | 상시 관측 | 에이전트·수집 서버가 필요하고 남은 기간에 비해 과하다 | 기각 |

**JFR이 `duration` 제약을 갖는 대신 아무것도 설치하지 않는다**는 점이 결정적이었다.
프로파일링은 지금 몇 번 하면 되는 일이지, 상시 운영할 기능이 아니다.

---

## 1. 켜기

`infra/.env`에 한 줄 추가한다.

```bash
JFR_OPTS=-XX:StartFlightRecording=name=load,settings=profile,duration=15m,filename=/tmp/load.jfr
```

| 인자 | 뜻 |
| --- | --- |
| `settings=profile` | CPU 샘플링 주기를 촘촘히 한다(기본 `default`는 성기다). 오버헤드 약 2% |
| `duration=15m` | **JVM 시작 후 15분간** 기록하고 파일을 쓴다 |
| `filename=/tmp/load.jfr` | 비루트(uid 10001)도 쓸 수 있는 경로 |

배포한다.

```bash
cd ~/app && ./infra/scripts/deploy.sh    # 또는 평소 배포 절차
```

기동 로그에 `Picked up JAVA_TOOL_OPTIONS`가 찍히면 적용된 것이다.

```bash
docker compose logs backend | grep -i "JAVA_TOOL_OPTIONS\|Flight Recorder"
```

---

## 2. 15분 안에 부하를 건다

**타이머는 JVM 시작과 함께 돌기 시작한다.** 기동 1~2분 + 준비 1분 + probe 4분 20초 ≈ 7분이라
여유는 있지만, 배포하고 딴 일을 하면 빈 기록만 남는다.

```bash
cd ~/app
# 지표 샘플러(선택)
nohup python3 infra/scripts/loadtest-sampler.py 320 > /tmp/jfr-run-samples.txt 2>&1 &

docker run --rm -i --name k6-jfr --cpus=1 --user "$(id -u):$(id -g)" --network infra_app-net \
  -e BASE_URL=http://backend:8080/api -e PROFILE=probe -e PROBE_VUS=120 \
  -v "$PWD/backend/scripts/loadtest:/work" -w /work grafana/k6:latest run user-journey.js \
  2>&1 | tee /tmp/k6-jfr.txt
```

> `tee`로 전문을 남긴다. `#5`에서 출력을 `head`로 잘라 엔드포인트별 지연을 놓친 적이 있다.

---

## 3. 꺼내기

`duration`이 끝나면 파일이 생성된다. **부하가 끝나도 15분을 채워야 나온다.**

```bash
docker compose exec -T backend ls -l /tmp/load.jfr        # 생겼는지 확인
docker cp $(docker compose ps -q backend):/tmp/load.jfr /tmp/load.jfr

# 분석용으로 다시 넣는다(원본을 덮어쓰지 않도록 이름을 바꾼다)
docker cp /tmp/load.jfr "$(docker compose ps -q backend)":/tmp/analyze.jfr
```

---

## 4. 읽기

컨테이너에 `jfr` 도구가 이미 있다(JRE에 포함된다).

> **★ 반드시 `-e JAVA_TOOL_OPTIONS=`로 비워서 실행한다.**
> `jfr` 도구도 JVM이라 `JAVA_TOOL_OPTIONS`를 물려받아 **새 기록을 시작하면서
> `filename=`이 가리키는 원본을 비워버린다**(2026-09-24에 실제로 당했다).
> 그래서 3번에서 `docker cp`로 호스트에 사본을 먼저 뜬다.

```bash
# CPU를 가장 많이 쓴 메서드 상위 (가장 먼저 볼 것)
docker compose exec -T -e JAVA_TOOL_OPTIONS= backend jfr summary /tmp/analyze.jfr

# 실행 샘플 원본 — 스택의 최상단 프레임을 세면 곧 CPU 프로파일이다
docker compose exec -T -e JAVA_TOOL_OPTIONS= backend jfr print --events jdk.ExecutionSample /tmp/load.jfr | head -200

# 쿼리 실행 이벤트
docker compose exec -T -e JAVA_TOOL_OPTIONS= backend jfr print --events jdk.JavaMonitorEnter,jdk.SocketRead /tmp/load.jfr | head -100
```

로컬에서 GUI로 보려면 파일을 받아 **JDK Mission Control(JMC)**로 연다.
`Method Profiling` 탭이 곧 답이다.

---

## 5. 끄기 (반드시)

프로파일링이 끝나면 `.env`에서 `JFR_OPTS` 줄을 지우고 재배포한다.
켜둔 채로 두면 매 배포마다 15분씩 기록하고 오버헤드 2%가 상시로 붙는다.

```bash
sed -i '/^JFR_OPTS=/d' infra/.env
```

---

## 6. 해석할 때 주의

- **`settings=profile`은 측정 대상에 약 2% 오버헤드를 준다.** 이 실행의 처리량 수치를
  개선 전후 비교의 "전"으로 쓰지 않는다. 기준선은 `load-test-records.md` `#5`를 쓴다.
- JFR의 CPU 샘플링은 **safepoint bias**가 있다. 인라인된 짧은 메서드는 과소평가될 수 있다.
  상위 몇 개가 애매하게 나오면 async-profiler로 교차 확인한다.
- 부하 도구(k6)가 같은 호스트에 있다. JFR은 백엔드 JVM 안만 보므로 이 영향은 받지 않지만,
  **CPU 경쟁으로 샘플 수가 줄 수는 있다.**
