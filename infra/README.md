# infra

도담(Dodam) 서비스 인프라. **EC2 1대(4 vCPU / 16GB) 위에 Docker Compose로 컨테이너 14개**를 띄워
애플리케이션·데이터·CI/CD·모니터링을 전부 구동한다.

```
         인터넷
            │  80 / 443 만 열림
        ┌───▼────┐
        │ nginx  │  TLS 종료 · 리버스 프록시 · 레이트 리밋 · 보안 헤더
        └─┬──┬──┬┘
          │  │  └──────────────┐
   /api/  │  │ /grafana/       │ /jenkins/project/ (웹훅만)
      ┌───▼──┴──┐   ┌──────────▼──┐   ┌─────────┐
      │ backend │   │  grafana    │   │ jenkins │─ docker.sock (빌드·배포)
      └──┬───┬──┘   └──┬───┬──────┘   └─────────┘
         │   │         │   │
    ┌────▼┐ ┌▼─────┐ ┌─▼──────────┐ ┌▼─────┐
    │mysql│ │redis │ │ prometheus │ │ loki │← promtail
    └─────┘ └──────┘ └─────▲──────┘ └──────┘
                           │ node-exporter · cadvisor
                           │ mysqld-exporter · redis-exporter
                    certbot ─ 인증서 발급·갱신
```

**외부에 열려 있는 것은 nginx의 80/443뿐이다.** MySQL·Redis·Prometheus·Loki·exporter는
호스트 포트조차 바인딩하지 않고 컨테이너 네트워크(`app-net`) 안에서만 통신한다.
Jenkins UI는 루프백(`127.0.0.1:8080`)에만 바인딩한다. 근거: [todo/security.md](todo/security.md) R1

---

## 문서 지도

| 문서 | 내용 |
| --- | --- |
| [docs/ci-cd.md](docs/ci-cd.md) | **배포 파이프라인 전체 흐름**, 롤백, 알림, 트러블슈팅 |
| [docs/monitoring.md](docs/monitoring.md) | 지표 수집 경로, 잡별 지표 정의와 임계, 측정 절차 |
| [docs/dashboard-guide.md](docs/dashboard-guide.md) | Grafana 부하 테스트 대시보드 **읽는 법**(입문용) |
| [docs/load-handling.md](docs/load-handling.md) | "서비스는 부하를 어떻게 견디나" 개념 정리(입문용) |
| [docs/load-test-records.md](docs/load-test-records.md) | 부하 측정 기록 — 값과 **배포 커밋**을 함께 남긴 원장 |
| [docs/load-test-summary.md](docs/load-test-summary.md) | 부하 테스트 결과 요약(발표 전달용) |
| [docs/profiling.md](docs/profiling.md) | JFR CPU 프로파일링 절차와 대안 검토 |
| [docs/external-integrations.md](docs/external-integrations.md) | 외부 연동(금융망/OpenAI/FCM/소셜로그인) 등록값과 발급 절차 |
| [secrets/README.md](secrets/README.md) | 파일 형태 시크릿(FCM 서비스 계정 키) 보관·교체 |
| [todo/00-overview.md](todo/00-overview.md) | 인프라 전체 계획과 확정 사항 |
| [todo/security.md](todo/security.md) | 보안 방침, 식별된 위험(R1~R6)과 대응 |
| [todo/jiw/](todo/jiw/) | **일자별 실제 작업 로그** (판단 근거·실패 사례 포함) |

> 어떤 설정이 "왜 그렇게 되어 있는지"는 대부분 `docker-compose.yml`의 주석과
> `todo/jiw/day-XX.md`에 사고 경위와 함께 남아 있다. 설정을 바꾸기 전에 먼저 확인할 것.

---

## 접속 정보

| 대상 | 주소 | 비고 |
| --- | --- | --- |
| 서비스 API | `https://j15D105.p.ssafy.io/api/...` | HTTP로 오면 HTTPS로 301 |
| 헬스체크 | `https://j15D105.p.ssafy.io/api/health` | 내부 점검용으로 HTTP도 허용 |
| Grafana | `https://j15D105.p.ssafy.io/grafana/` | 자체 로그인. 익명 접근·가입 차단 |
| Actuator | — | 외부에서 `/actuator/`는 **404**. 수집은 컨테이너 네트워크로만 |
| Jenkins UI | **SSH 터널 경유만** (아래 참고) | 외부에서 `/jenkins/`는 403 |
| Jenkins 웹훅 | `https://j15D105.p.ssafy.io/jenkins/project/dodam-backend-deploy` | GitLab만 호출 |
| EC2 SSH | `ssh ssafy-ec2` | 공개키 전용, 비밀번호 인증 비활성 |

### Jenkins UI 접속 방법

Jenkins 컨테이너는 호스트 Docker 데몬을 조작할 수 있어서(빌드에 필요), 공개하면 뚫렸을 때 서버 전체가 넘어간다.
그래서 **UI는 외부에 열지 않고 루프백(`127.0.0.1:8080`)에만 바인딩**한다. 근거: `todo/security.md` R1

```bash
ssh -L 8080:127.0.0.1:8080 ssafy-ec2
# 터널을 연 채로 브라우저에서 http://localhost:8080/jenkins/
```

### nginx 레이트 리밋

| 버킷 | 대상 | 한도 |
| --- | --- | --- |
| `api_auth` | `/api/v1/auth/login`, `/api/v1/auth/signup` | 20 req/분 |
| `api_general` | 그 외 `/api/` 전체 | 20 req/초 |

경로가 `/api/v1/auth/...`라는 점이 중요하다. 2026-09-15 이전에는 규칙이 `/api/auth/`를 가리켜
로그인에 무차별 대입 방어가 사실상 걸리지 않았다.

---

## 스택 띄우기

```bash
cd ~/app/infra
cp .env.example .env   # 값 채워넣기 (절대 커밋 금지)
docker compose up -d
```

⚠️ **`docker compose down -v`는 절대 실행하지 말 것** — `mysql_data`(DB 전체), `jenkins_home`(job·자격증명),
`grafana_data`·`prometheus_data`·`loki_data`(대시보드와 지표·로그 이력)가 전부 삭제된다.

자주 쓰는 명령:

```bash
docker compose ps                       # 상태·health 확인
docker compose logs -f backend          # 로그 추적
./scripts/check-env.sh                  # 배포 전 .env 사전 검사 (배포도 이걸 먼저 돌린다)
docker compose exec prometheus wget -qO- 'http://localhost:9090/-/reload'   # 설정 리로드
```

---

## 배포

`develop`에 머지되면 GitLab 웹훅이 Jenkins를 깨우고, 자동으로 빌드 → 테스트 → 배포 → 헬스체크까지 진행된다.
실패하면 직전 이미지로 롤백하고 Mattermost로 알린다. 상세 흐름과 트러블슈팅은 [docs/ci-cd.md](docs/ci-cd.md).

- 파이프라인 정의: 저장소 루트 `Jenkinsfile`
- 배포 스크립트: `scripts/deploy.sh`
  (`.env` 검사 → `git reset --hard` 동기화 → 이미지 빌드 → 컨테이너 기동 → 헬스체크 → 태그 갱신)
- **헬스체크는 두 단계다.**
  `/actuator/health`(의존성 포함 — DB·Redis에 못 붙은 배포를 잡는다) → `/api/health`(외부 경로).
  `/api/health`만 보면 "떴지만 DB에 못 붙은" 배포가 성공으로 처리된다.
- **이미지 태그 3종**: `infra-backend:latest`(방금 빌드) / `:deployed`(헬스체크를 통과한 마지막)
  / `:rollback`(롤백 지점). 롤백 지점은 **healthy일 때만** 갱신한다 —
  안 그러면 깨진 이미지가 롤백 지점을 덮어써 롤백해도 같은 이미지가 다시 뜬다(2026-09-14 실측).
- 배포 대상 서비스: `mysql redis backend nginx certbot` + **모니터링 스택 전체** — **jenkins는 제외**한다.
  배포가 Jenkins 컨테이너 안에서 실행되므로, jenkins까지 재시작하면 자기 빌드를 죽인다.
  반대로 모니터링 서비스를 목록에서 빼면, 설정을 고쳐 머지해도 배포가 반영하지 않아
  수동 변경이 다음 배포의 `git reset --hard`에 조용히 밀려난다.
- **jenkins 이미지를 바꿨을 때만** 호스트에서 수동으로:
  ```bash
  cd ~/app/infra && docker compose up -d --build --no-deps jenkins
  ```

⚠️ `APP_DIR`(기본 `/home/ubuntu/app`)은 **배포 대상 사본**이다. `git reset --hard`로 origin에 강제로
맞추므로 이 디렉터리에서 직접 코드를 고치지 말 것 (`.env` 등 gitignore된 파일은 보존된다).

---

## TLS 인증서

Let's Encrypt를 certbot 컨테이너로 발급/갱신한다.

- 인증서 위치: `nginx/certbot/conf/live/j15d105.p.ssafy.io/` (**gitignore됨**)
- 갱신: certbot 컨테이너가 12시간마다 `certbot renew` 시도, nginx는 6시간마다 자체 reload로 새 인증서를 반영
  (certbot에 docker 권한을 주지 않기 위해 이 방식을 씀)
- 수동 확인:
  ```bash
  docker compose run --rm --entrypoint certbot certbot renew --dry-run
  ```

---

## 모니터링과 알림

| 대상 | 수집기 | 잡 | 보는 것 |
| --- | --- | --- | --- |
| 호스트 | node-exporter | `node` | CPU, 메모리, 디스크, 네트워크 |
| 애플리케이션 | backend `/actuator/prometheus` | `backend` | JVM heap·GC·스레드, HikariCP 풀, HTTP 지연·오류 |
| MySQL 서버 | mysqld-exporter | `mysql` | 슬로우 쿼리, 커넥션, InnoDB 락 대기·버퍼풀 |
| Redis 서버 | redis-exporter | `redis` | 메모리, eviction, hit/miss, 명령 처리량 |
| 컨테이너별 자원 | cAdvisor | `cadvisor` | 컨테이너 단위 CPU·메모리 |
| 로그 | promtail → Loki | — | 전 컨테이너 stdout/stderr |

- 보관 기간: Prometheus 15일(`--storage.tsdb.retention.time=15d`)
- 대시보드는 코드로 프로비저닝된다 → `monitoring/grafana/provisioning/dashboards/`
  (`overview` / `host` / `logs` / `loadtest`). **Grafana UI에서 고친 것은 재생성 시 사라진다.**
  바꾸려면 JSON을 고쳐 커밋할 것.
- 알림: Grafana → Mattermost (`수집 대상 다운`, `디스크 여유 부족`, `호스트 메모리 부족`)
  \+ `scripts/container-watch.sh`가 5분마다 컨테이너 중단·unhealthy·크래시 루프·cron 정지를 직접 감시한다.

> **cAdvisor에는 `docker.sock`을 일부러 마운트하지 않는다.** 이 호스트는 containerd 이미지 스토어라
> Docker 핸들러가 RW 레이어를 못 찾아 컨테이너를 전부 건너뛴다. 대가로 컨테이너 **이름을 모르고**
> 12자리 짧은 ID(`container_id` 라벨)만 남는다 — `docker ps`의 CONTAINER ID와 대조하면 된다.

부하 테스트 관련: k6 스크립트는 `backend/scripts/loadtest/`, 실행 중 서버 측 지표 샘플링은
`scripts/loadtest-sampler.py`, 무부하 기준선 스냅샷은 `scripts/metrics-snapshot.py`.

---

## 백업과 복구

| 대상 | 방법 | 주기 |
| --- | --- | --- |
| MySQL 전체 | `scripts/backup.sh` → `~/backups/mysql/*.sql.gz` (7일 보관) | 매일 04:00 (cron) |
| 시크릿(`.env`) | `scripts/backup-secrets.sh` → 암호화 단일 파일 | **수동** |

**시크릿 백업을 자동화하지 않는 것은 의도다.** DB 덤프와 같은 디렉터리·같은 주기로 쌓이면
한 번의 유출로 둘 다 털린다. 특히 `DATA_ENCRYPTION_KEY`/`DATA_HASH_KEY`는 암호화 컬럼의 키라서
키와 덤프가 한곳에 있으면 컬럼 암호화가 무의미해진다 (`todo/security.md` R5).

복구는 기본이 **임시 DB로의 리허설**이다. 운영 DB를 덮어쓰려면 명시적 플래그와 확인 문구가 필요하다.

```bash
./scripts/restore.sh <덤프파일>                     # dodam_restore_test 로 복구 (안전)
./scripts/restore.sh <덤프파일> --into-production   # 운영 DB 덮어쓰기 (확인 문구 입력)
```

---

## 자동화 (cron, `ubuntu` 계정)

| 시각 | 작업 | 스크립트 |
| --- | --- | --- |
| 매일 04:00 | MySQL 백업 | `cron-notify.sh backup → backup.sh` |
| 매일 05:00 | Docker 디스크 정리 | `cron-notify.sh docker-gc → docker-gc.sh` |
| 5분마다 | 컨테이너·cron 이상 감시 | `container-watch.sh` |

cron 작업은 전부 `cron-notify.sh` 래퍼를 거친다. 래퍼는 **실패하면 Mattermost로 알리고,
성공하면 heartbeat를 남긴다.** 실패 알림만으로는 "cron 자체가 멈춰 아예 실행되지 않은 경우"를
잡을 수 없기 때문이며, 그 감시는 `container-watch.sh`가 heartbeat 시각으로 한다
(2026-09-15에 백업이 실패했는데 아무도 몰랐던 일이 계기다).

---

## 스크립트

| 파일 | 하는 일 |
| --- | --- |
| `deploy.sh` | 배포 (Jenkins가 호출, 수동 실행도 가능) |
| `check-env.sh` | 배포 전 `.env` 검사 — 중복 정의·주석에 잘린 값·compose↔앱 참조 대조 |
| `backup.sh` / `restore.sh` | MySQL 백업 / 복구 |
| `backup-secrets.sh` | `.env` 암호화 백업 (수동) |
| `docker-gc.sh` | dangling 이미지·오래된 빌드 캐시 정리 |
| `container-watch.sh` | 컨테이너·cron 이상 감지 → Mattermost |
| `cron-notify.sh` | cron 래퍼 (실패 알림 + 성공 heartbeat) |
| `seed-load.sh` | 시연 서버 seed 적재 (bootstrap / load / check) |
| `metrics-snapshot.py` | 무부하 기준선 지표 스냅샷 |
| `loadtest-sampler.py` | 부하 중 서버 측 지표 5초 간격 샘플링 |

---

## 시크릿 위치 (값은 여기에 적지 않는다)

| 값 | 위치 |
| --- | --- |
| DB/Redis 비밀번호, JWT·암호화 키, 외부 API 키 | EC2 `~/app/infra/.env` (600) |
| FCM 서비스 계정 JSON | EC2 `~/app/infra/secrets/fcm-service-account.json` (gitignore) |
| GitLab 접근 토큰 | Jenkins Credentials(`gitlab-https`, `ec2-jenkins-deploy`), EC2 `~/.git-credentials` |
| 웹훅 secret | EC2 `~/.jenkins-webhook-secret` (600), Jenkins job 설정 |
| Grafana 관리자 계정, Mattermost 웹훅 URL | EC2 `~/app/infra/.env` |
| TLS 개인키 | EC2 `~/app/infra/nginx/certbot/conf/` |

필요한 키 이름은 `.env.example`에 전부 나열되어 있다(값은 비어 있다).
`DATA_ENCRYPTION_KEY`·`DATA_HASH_KEY`·`NUMBER_HASH_KEY`는 **한번 정하면 바꾸지 말 것** —
바꾸면 기존 암호화 컬럼과 해시 매칭이 깨진다.

---

## 구조

```
infra/
  docker-compose.yml     # 서비스 14개. 설정 이유가 주석으로 함께 있다
  .env.example           # 키 이름만 (실제 값은 EC2의 .env에만)
  jenkins/Dockerfile     # docker CLI + 플러그인 포함 Jenkins 이미지
  nginx/conf.d/          # 리버스 프록시, TLS, 레이트 리밋, 보안 헤더
  nginx/certbot/         # 인증서 (gitignore)
  mysql/init/
  monitoring/
    prometheus/prometheus.yml
    loki/{loki-config.yml,promtail-config.yml}
    grafana/provisioning/{dashboards,datasources,alerting}/
  secrets/               # 파일 형태 시크릿 (gitignore, README만 커밋)
  scripts/               # 배포·백업·복구·감시·부하측정
  docs/                  # 운영·모니터링·부하 테스트 문서
  todo/                  # 일자별 계획 + security.md + jiw/(실제 작업 로그)
```

---

## 진행 상황

| Day | 내용 | 상태 |
| --- | --- | --- |
| 1 | 서버 기초 설정 & 리포 구조 | ✅ |
| 2 | CI/CD 파이프라인 (Jenkins + GitLab webhook, 자동 배포) | ✅ |
| 3 | HTTPS(자동 갱신 검증), Jenkins 노출 축소, 레이트 리밋·보안 헤더·fail2ban | ✅ |
| 4 | MySQL/Redis 고도화 (백업·복구, 운영 설정) | ✅ |
| 5 | 시크릿 관리 & 배포 안정화 (`check-env.sh`, 롤백) | ✅ |
| 6 | 모니터링 스택 (Prometheus·Grafana·Loki·알림) | ✅ |
| 7 | 외부 연동 (FCM·OpenAI·소셜 로그인·금융망) | ✅ |
| 8 | 통합 리허설 & 문서화, cron 자동화 | ✅ |
| — | 부하 테스트·프로파일링·성능 개선 (S15P21D105-693) | ✅ 요약: [docs/load-test-summary.md](docs/load-test-summary.md) |

---

## 사고를 부르는 것들 (하기 전에 읽을 것)

| 하지 말 것 | 이유 |
| --- | --- |
| `docker compose down -v` | 명명 볼륨 전체(=DB·Jenkins·지표·대시보드) 삭제 |
| 배포 대상에 `jenkins` 포함 | 배포가 Jenkins 안에서 돌기 때문에 자기 빌드를 죽인다 |
| 배포 대상에서 모니터링 서비스 제외 | 수동 변경이 다음 배포의 `git reset --hard`에 조용히 원복된다 |
| compose에 `depends_on: jenkins` 추가 | 배포 시 의존성으로 jenkins까지 재생성 → 위와 같은 사고 (2026-09-07 발생) |
| Prometheus 설정을 **단일 파일**로 마운트 | 컨테이너가 기동 시 inode를 잡아, 배포로 파일이 새로 생기면 옛 내용을 계속 본다 |
| Grafana UI에서 대시보드 수정 | 프로비저닝이 덮어쓴다. JSON을 고쳐 커밋할 것 |
| `APP_DIR`에서 코드 직접 수정 | 배포 때 `git reset --hard`로 날아간다 |
| 병합 중 compose의 `environment:` 블록 축약 | 2026-09-08에 JWT/암호화 키 4줄이 유실돼 백엔드가 기동 실패했다 |
