# Day 2 — 실제 진행 기록

계획: `infra/todo/day-02.md` 참고

## 2026-09-07

### Day 1 마무리 & push
- 로컬 작업물(`docker-compose.yml`, `.gitignore`, `infra/{nginx,mysql,scripts,monitoring}`, `infra/.env.example`, `infra/README.md`, `infra/todo/jiw/`) 커밋
  - 커밋 메시지: `S15P21D105-217 chore: EC2 서버 기초 세팅 및 레포 스켈레톤 구성 #done`
- `origin`(GitLab Lab, `s15-fintech-finance-sub1/S15P21D105`)으로 push
- push 과정에서 `develop` 브랜치가 `infra`로 머지됨 (FE 초기 환경/구조 설정 커밋 포함) — 별도 충돌 없음

### Jenkins 서비스 추가 착수
- 목표: `docker-compose.yml`에 jenkins 서비스 추가, nginx에서 `/jenkins/` 서브패스로 웹 UI 접근 가능하게 최소 라우팅 구성
- Docker Pipeline(빌드/배포 단계에서 `docker` 명령 실행)을 위해 Jenkins 컨테이너 안에 docker CLI가 필요 → 공식 `jenkins/jenkins` 이미지엔 없어서 커스텀 Dockerfile로 docker-cli 설치 + `/var/run/docker.sock` 마운트(DooD 방식) 채택
- 호스트 docker 그룹 GID를 컨테이너 안 jenkins 유저에 맞춰줘야 소켓 권한 문제가 안 생김 → `.env`의 `DOCKER_GID`로 주입하는 방식 채택 (EC2에서 `getent group docker`로 실측 필요)

### 로컬 작업 완료: Jenkins 서비스 구성

- `infra/jenkins/Dockerfile` 작성 — `jenkins/jenkins:lts-jdk17` 베이스에 docker-ce-cli, docker-compose-plugin 설치(DooD 방식), `jenkins-plugin-cli`로 git/gitlab-plugin/docker-workflow/credentials-binding/workflow-aggregator 플러그인 선설치
- `docker-compose.yml`에 `jenkins` 서비스 추가
  - `./jenkins`에서 빌드, `jenkins_home` 볼륨 영속화, `/var/run/docker.sock` 마운트
  - `JENKINS_OPTS=--prefix=/jenkins`로 서브패스 운영, 8080 포트는 외부에 직접 노출하지 않고 nginx를 통해서만 접근하도록 함
  - `group_add: ["${DOCKER_GID}"]`로 호스트 docker 그룹 GID를 주입받게 함 (소켓 권한 문제 방지) — `nginx` 서비스에 `depends_on: jenkins` 추가
- `infra/nginx/conf.d/default.conf` 작성 — `/jenkins/`는 `jenkins:8080`으로 프록시(공식 Jenkins 리버스 프록시 권장 헤더 포함), `/`는 임시로 200 텍스트 응답 (Day 3에서 실제 백엔드 프록시로 교체 예정)
- `infra/.env.example`에 `DOCKER_GID` 항목 추가 (EC2에서 `getent group docker | cut -d: -f3`로 실측해서 채울 것)

### 커밋 누락 트러블슈팅
- EC2에서 `git pull` 후 `docker compose up -d --build`를 돌렸는데 nginx만 뜨고 `no such service: jenkins` 발생
- 원인: 위 로컬 작업물을 **커밋/push하지 않은 상태**에서 EC2에 pull을 지시함 (Claude 실수). EC2는 Day 1 시점의 compose 파일을 그대로 보고 있었음
- 대응: `S15P21D105-469 chore: Day2 Jenkins 서비스 및 nginx 서브패스 라우팅 추가 #in-progress`로 커밋 → 사용자가 push → EC2에서 재pull

### 커밋 메시지 규칙 정리
- Jira 키 + 상태 태그 형식 사용: `JIRA-KEY type: 작업 내용 #상태` (예: `#done`, `#in-progress`)
- Day 1 작업은 `S15P21D105-217`, Day 2 Jenkins 작업은 `S15P21D105-469`
- 커밋 본문은 `.gitlab/merge_request_templates/template.md`의 MR 템플릿 형식(작업 목적/작업 내용/리뷰 포인트/테스트/참고)을 따르기로 함
- `Co-Authored-By`, `Claude-Session` 트레일러는 넣지 않기로 함

### Jenkins 기동 성공
- EC2에서 `.env`에 `DOCKER_GID` 채우고 `docker compose up -d --build` → jenkins 컨테이너 기동 성공
- `docker compose ps`: `jenkins` (infra-jenkins 이미지, 8080/50000 컨테이너 내부 포트), `nginx` 모두 Up
- Jenkins 버전 2.541.3, `--prefix=/jenkins` 정상 적용 (`Started ... {Jenkins v2.541.3,/jenkins,...}`), `Jenkins is fully up and running` 확인
- 초기 admin 비밀번호는 컨테이너 로그 및 `/var/jenkins_home/secrets/initialAdminPassword`에 있음 (값은 시크릿이므로 레포에 기록하지 않음. 확인: `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`)

### nginx 502/refused 트러블슈팅
- 증상: jenkins가 떠 있는데도 로컬 PC에서 `curl http://j15D105.p.ssafy.io/` → 여전히 `Connection refused`
- EC2에서 직접 확인 (Claude가 `ssh ssafy-ec2`로 접속해 점검)
  - `curl -s ifconfig.me` → `43.203.198.18` (도메인이 가리키는 IP와 일치, 서버는 맞음)
  - `sudo ss -tlnp` → 80/443 모두 `docker-proxy`가 LISTEN 중
  - 그런데 **EC2 안에서 `curl localhost:80`도 실패(000)** → 외부 방화벽 문제가 아니라 nginx 자체 문제로 좁혀짐
- 원인: nginx 컨테이너는 Day 1에 기동됐는데, 그때 `nginx/conf.d`에는 `.gitkeep`만 있었음. 이 디렉터리를 read-only로 마운트하면서 이미지 기본 `default.conf`가 덮여 사라졌고, 결과적으로 **server 블록이 하나도 없는 채로 실행 중**이었음 → 컨테이너 안에서는 80을 아무도 안 듣고 있어서 docker-proxy가 RST 반환 → 외부에는 "Connection refused"로 보임
- 조치: `docker compose restart nginx` (git pull로 새로 받은 `default.conf`를 읽게 함)
- 결과
  - `docker exec nginx nginx -t` → syntax ok
  - EC2 내부/외부 모두 `http://j15D105.p.ssafy.io/` → **200** (`infra day2: nginx up, backend not deployed yet`)
  - `http://j15D105.p.ssafy.io/jenkins/` → **403 + `/jenkins/login`으로 리다이렉트**, `/jenkins/login` → **200**
  - 즉 리버스 프록시 정상 동작, 서브패스 prefix도 올바르게 처리됨(`--prefix=/jenkins`와 nginx `proxy_pass` 조합 검증 완료)
- 부수 확인: **AWS 보안그룹 80번 인바운드는 실제로 열려 있음이 확정됨** (Day 1의 추정이 맞았음)
- 교훈: `conf.d`를 통째로 마운트하는 구성에서는 설정 파일 추가/변경 후 반드시 nginx reload/restart가 필요. 이후 Day 3에서 SSL/라우팅 추가할 때도 동일하게 적용할 것

### Jenkins setup wizard 완료 및 DooD 동작 검증
- 사용자가 브라우저에서 초기 setup wizard 완료 (suggested plugins 설치 + admin 계정 생성)
  - 참고: Dockerfile에서 이미 설치한 플러그인은 중복 설치되지 않고 유지됨. EC2 사양(16GB RAM)이 여유로워 suggested 세트 설치해도 부담 없다고 판단
- Claude가 `ssh ssafy-ec2`로 접속해 검증
  - `/var/jenkins_home/`에 `config.xml`, `jenkins.install.InstallUtil.lastExecVersion`, `jenkins.install.UpgradeWizard.state` 생성 확인 → wizard 완료
  - `docker exec jenkins docker version` → client/server 모두 29.8.0 (호스트 데몬과 통신 성공)
  - `docker exec jenkins docker ps` → `jenkins`, `nginx` 조회됨 → **DooD + DOCKER_GID 그룹 권한 정상 동작 확인**
  - `docker exec jenkins docker compose version` → v5.5.1

### 계획 변경: 더미 앱 → 실제 백엔드로 직행
- 상황 변화: Day 2 계획 수립 시점엔 백엔드 코드가 없어서 "더미 앱으로 파이프라인 배관만 검증"하기로 했으나, `develop` 머지로 **실제 백엔드 스켈레톤이 이미 레포에 들어옴**
  - Spring Boot 3.5.16 / Java 21 / Gradle, `com.ssafy.dodam`
  - `/api/health` 엔드포인트 존재 (`HealthController`) → 배포 검증 타겟으로 적합
  - 테스트는 H2 사용(`testRuntimeOnly com.h2database:h2`) → 외부 DB 없이 CI에서 실행 가능
  - `prod` 프로파일은 `DB_URL`/`REDIS_*`/`CORS_ALLOWED_ORIGINS` 요구, `flyway.enabled=true`, `ddl-auto=validate`
- 사용자에게 선택지 제시(실제 백엔드 직행 / 계획대로 더미 / 중간) → **"실제 백엔드로 바로"** 선택
- 결정: **Day 4의 MySQL·Redis 구성을 Day 2로 앞당김**. Day 4는 DB 고도화(초기화 스크립트/백업/튜닝)로, Day 5는 시크릿 정식화로 범위 조정

### 파이프라인 구성 작업 (로컬)
- `backend/Dockerfile` 작성 — 멀티스테이지
  - builder(`eclipse-temurin:21-jdk`): gradle wrapper/의존성 먼저 복사해 레이어 캐싱 후 `./gradlew clean build` (**테스트 포함** — 테스트 실패 시 이미지 빌드가 실패하며 파이프라인 중단)
  - runtime(`eclipse-temurin:21-jre`): jar만 복사, healthcheck용 curl 설치, `HEALTHCHECK`로 `/api/health` 폴링
  - 왜 이 구조인가: Jenkins 컨테이너엔 JDK21/gradle이 없고, **DooD 환경에서는 Jenkins 워크스페이스 경로(`/var/jenkins_home/...`, named volume 내부)가 호스트 경로와 달라 `docker.inside`식 워크스페이스 마운트가 깨진다.** 반면 `docker build`의 빌드 컨텍스트는 tar로 스트리밍되므로 문제없이 동작 → 빌드/테스트를 전부 이미지 빌드 안에서 처리
- `backend/.dockerignore` 작성 (build/, .gradle/, .env 등 제외)
- `docker-compose.yml` 확장
  - `mysql`(8.0): utf8mb4, `mysql_data` 볼륨, `./mysql/init` 마운트, `mysqladmin ping` 헬스체크, **외부 포트 미노출**
  - `redis`(7-alpine): `--requirepass` + AOF 영속화, `redis_data` 볼륨, 헬스체크, **외부 포트 미노출**
  - `backend`: `../backend` 빌드 컨텍스트, prod 프로파일, DB/Redis 접속정보 주입, `depends_on: service_healthy`로 기동 순서 보장, **외부 포트 미노출**(nginx 경유만)
  - `jenkins`에 `${APP_DIR}:${APP_DIR}` 마운트 추가 — **DooD에서 compose의 bind mount는 호스트 기준으로 해석되므로, Jenkins가 배포를 돌리려면 배포용 체크아웃이 호스트와 동일 경로로 보여야 함**
- `nginx/conf.d/default.conf`에 `/api/` → `backend:8080` 프록시 추가 (경로 그대로 전달)
- `infra/scripts/deploy.sh` 작성 — `APP_DIR`을 `origin/<branch>` 기준으로 `reset --hard` 동기화 → `compose up -d --build` → nginx reload → `/api/health` 200 될 때까지 최대 40회(5초 간격) 폴링, 실패 시 backend 로그 100줄 출력 후 exit 1
- 루트에 `Jenkinsfile` 작성 — Checkout → Build & Test(`compose build backend`) → Deploy(`deploy.sh`), 실패 시 컨테이너 상태/로그 출력. timeout 30분, 동시 빌드 금지, 최근 20개 보관
- `.env.example` 갱신 — `APP_DIR`, `MYSQL_*`, `REDIS_PASSWORD`, `CORS_ALLOWED_ORIGINS` 추가

### 검증
- 로컬 WSL엔 docker가 없어 검증 불가 → compose 파일을 EC2 `/tmp`로 scp해서 `docker compose config -q` 실행 → **문법 OK**
- `bash -n infra/scripts/deploy.sh` → 문법 OK

### EC2 `.env` 실값 작성
- 기존 `.env` 백업(`.env.bak.20260907132856`) 후 새 템플릿 기준으로 재작성, `chmod 600`
- `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `REDIS_PASSWORD`(각 24자), `JWT_SECRET`(64자)는 **EC2 서버에서 `openssl rand`로 직접 생성** — 값이 대화/로그를 거치지 않도록 함
- `MYSQL_DATABASE=dodam`, `MYSQL_USER=dodam`, `APP_DIR=/home/ubuntu/app`, `CORS_ALLOWED_ORIGINS=http://localhost:8081`
- 기존 `DOCKER_GID` 값은 그대로 승계

### GitLab 인증 문제 발견
- EC2에서 `git pull` 시 `could not read Username for 'https://lab.ssafy.com'` — **EC2에 GitLab 자격증명이 저장돼 있지 않음** (`credential.helper` 미설정, `~/.git-credentials` 없음)
- 로컬 WSL에서도 동일 — 이 세션에서는 push 불가, 사용자가 직접 push 중
- 영향: 단순 pull 문제가 아니라 **`deploy.sh`의 `git fetch`와 Jenkins의 SCM 체크아웃이 모두 비대화형이라 반드시 해결 필요**
- 사용자 선택: **Personal Access Token 방식** (Deploy Key(SSH) 대비 HTTPS 그대로 쓸 수 있어 간단, 사내망 SSH 아웃바운드 차단 리스크 없음)
- 사용자 액션으로 안내 (토큰 값은 대화에 남기지 않도록 사용자가 직접 입력):
  1. GitLab → Edit profile → Access Tokens에서 `read_repository`(+ 필요시 `api`) 토큰 발급
  2. EC2에서 `git config --global credential.helper store` 후 `git pull` 1회 대화형 인증 → `~/.git-credentials`에 저장
  3. Jenkins → Credentials에 `Username with password`로 등록 (ID: `gitlab-token`)

### 스택 전체 기동 및 end-to-end 검증 (파이프라인 연결 전 선검증)
- git 인증이 아직 안 풀린 상태라, 변경 파일을 `scp`로 EC2에 직접 복사해 선검증 진행 (이후 git pull로 대체될 임시 조치)
- `docker compose up -d --build` 결과
  - `infra-backend` 이미지 빌드 성공 — **builder 스테이지의 `./gradlew clean build`가 통과했다는 것은 백엔드 테스트도 함께 통과했다는 의미**
  - `mysql` → Healthy, `redis` → Healthy 확인 후 `backend` 기동 (`depends_on: service_healthy` 정상 동작)
  - `backend` 컨테이너 자체 HEALTHCHECK도 `healthy`
  - jenkins 컨테이너는 compose 정의 변경(APP_DIR 마운트 추가)으로 recreate됨 — `jenkins_home`이 named volume이라 **setup wizard 결과/계정 유지됨**
- nginx가 예전 설정을 물고 있어 `/api/`가 안 잡히는 현상 → `docker exec nginx nginx -s reload`로 해결 (Day 1에서 겪은 것과 같은 패턴. `deploy.sh`에 reload 단계를 이미 넣어둬서 이후 배포부터는 자동 처리됨)
- **최종 검증 결과**
  ```
  $ curl http://j15D105.p.ssafy.io/api/health
  {"success":true,"data":{"status":"UP"},"message":null}
  HTTP 200
  ```
  - 외부 도메인 → nginx → backend → MySQL/Redis 경로가 전부 실제로 동작함을 확인
  - Day 2 시점에 **실제 백엔드가 배포되어 서비스되는 상태** 달성 (원래 계획의 Day 5 목표를 앞당겨 달성)

### 브랜치 전략 정리 및 EC2 체크아웃 정정
- `git pull origin develop` 시도 시 scp로 임시 복사해둔 파일들과 충돌 발생
- 확인 결과: **인프라 작업물이 `origin/infra`가 아니라 `origin/develop`에 올라가 있었음** (사용자가 MR로 develop에 머지)
  - `origin/infra`에는 `Jenkinsfile`, `backend/Dockerfile`, `deploy.sh`, `nginx/conf.d/default.conf` 전부 없음 (Day 1 상태에서 멈춤)
  - `origin/develop`에는 전부 존재
- 삭제 전 안전 확인: 로컬 파일과 `origin/develop`의 blob 해시를 6개 파일 모두 비교 → **전부 동일**함을 확인한 뒤 scp 임시 파일 제거
- EC2 체크아웃을 `git checkout -B develop origin/develop`으로 전환 (`.env`는 gitignore라 보존됨)
- 이에 맞춰 `deploy.sh`의 `DEPLOY_BRANCH` 기본값을 `infra` → `develop`으로 변경

### GitLab PAT 등록 (사용자 액션 완료)
- EC2에서 `git config --global credential.helper store` + 1회 대화형 `git pull`로 자격증명 저장 완료 → 이후 비대화형 fetch 정상 동작
- Jenkins Credentials 2건 등록
  | ID | Kind | 용도 |
  |---|---|---|
  | `ec2-jenkins-deploy` | GitLab API token | Jenkins → GitLab API |
  | `gitlab-https` | Username with password | 파이프라인 소스 체크아웃 |
- 처음엔 GitLab API token 타입 하나만 등록됐는데, **이 타입으로는 HTTPS git 체크아웃이 안 되므로** `Username with password` 타입을 추가로 등록하도록 안내함

### Jenkins Pipeline Job 생성 (UI 대신 config.xml 주입 방식)
- Jenkins 관리자 비밀번호 없이 진행하기 위해, `/var/jenkins_home/jobs/dodam-backend-deploy/config.xml`을 직접 작성하고 컨테이너를 재시작하는 방식 채택
- Job 구성: `CpsScmFlowDefinition`(SCM: GitLab HTTPS + `gitlab-https` 자격증명, 브랜치 `*/develop`, `scriptPath=Jenkinsfile`), 트리거는 `GitLabPushTrigger`(push만, 브랜치 필터 `develop`, secretToken)
- webhook secret은 EC2에서 `openssl rand -hex 20`으로 생성해 `~/.jenkins-webhook-secret`(600)에 저장 — 값이 대화/로그를 거치지 않도록 함
- 로드 검증
  - `nextBuildNumber`가 안 생겨서 처음엔 로드 실패로 오해했으나, 웹훅 엔드포인트에 POST 시 **401**(=job은 찾았고 secret 검증에서 거부) 응답 → job 정상 로드 확인. (GET은 404가 정상)
  - secret 포함 POST 시 트리거는 됐으나 `sourceRepoSshUrl must not be null` (테스트 페이로드에 `repository.git_ssh_url` 누락) → 페이로드 보완 후 **HTTP 200, 빌드 #1 생성**

### 빌드 #1 실패 → 원인 분석 및 수정
- 결과: `Finished: FAILURE`. 체크아웃/자격증명은 정상(`using credential gitlab-https`, develop `5cc59b5` 체크아웃 성공)
- 실패 지점: `Build & Test` 스테이지의 이미지 빌드
  ```
  Step 12/16 : COPY --from=builder /workspace/build/libs/*.jar app.jar
  When using COPY with more than one source file, the destination must be a directory and end with a /
  ```
- 원인: gradle이 jar를 2개 생성함 (`dodam-backend-0.0.1-SNAPSHOT.jar` + `-plain.jar`) → 와일드카드가 2개 매칭
- **왜 수동 빌드 때는 통과했는가**: 호스트에는 buildx가 설치돼 있어 BuildKit으로 빌드됐지만, **Jenkins 컨테이너엔 buildx가 없어 classic builder로 폴백**(로그에 `buildx Docker CLI plugin not found: falling back to the classic builder` 경고)되었고, classic builder는 이 규칙을 엄격히 적용함
- 수정
  - `backend/Dockerfile`: builder 스테이지에서 `-plain.jar`를 제외하고 실행 가능한 jar만 골라 `/workspace/app.jar`로 정규화 → 런타임 스테이지는 고정 경로를 COPY (버전 문자열이 바뀌어도 안전)
  - `infra/jenkins/Dockerfile`: `docker-buildx-plugin` 추가 설치 → Jenkins에서도 BuildKit 사용, 호스트와 동작 일치
- 커밋 전 검증: 백엔드 소스+Dockerfile을 EC2 `/tmp/bt`에 복사해 **`DOCKER_BUILDKIT=0`(classic builder)로 직접 빌드 → 성공** 확인 후 테스트 아티팩트 정리
- 부수 확인: 실패 로그의 `docker compose ps`상 backend는 `Up (healthy)` — 앞서 수동 배포한 컨테이너가 계속 정상 서비스 중이었음 (파이프라인 실패가 서비스 중단으로 이어지지 않음)

### 파이프라인 구조적 결함 2건 수정
빌드 #1 수정사항을 반영하기 전에, 코드를 다시 보다가 두 가지 구조적 문제를 발견해 먼저 고침.
1. **자기 자신을 재시작하는 배포**: `deploy.sh`의 `compose up -d --build`가 서비스 지정 없이 전체를 대상으로 해서, **Jenkins 컨테이너 안에서 실행되는 배포가 jenkins 서비스까지 재빌드/재시작** → 배포 도중 자기 빌드를 죽이게 됨. 마침 `infra/jenkins/Dockerfile`(buildx 추가)을 바꾼 직후라 실제로 터질 상황이었음
   - 수정: `DEPLOY_SERVICES="mysql redis backend nginx"`를 도입해 up 대상에서 jenkins 제외. jenkins 이미지 변경은 호스트에서 수동 재빌드
2. **직전 배포 코드로 테스트**: `Build & Test`가 `$APP_DIR`의 compose로 빌드하는데, `$APP_DIR` 동기화는 그 다음 `Deploy` 단계의 deploy.sh에서 일어남 → 새로 push한 코드가 아니라 **이전 배포 코드로 테스트**하고 있었음
   - 수정: `Build & Test`를 `$WORKSPACE`의 compose 기준으로 변경 (방금 체크아웃한 코드로 테스트, 실패 시 배포 진입 안 함)
   - 추가로 `deploy.sh`를 `$APP_DIR`이 아닌 `$WORKSPACE` 사본으로 실행하도록 변경 — deploy.sh가 `$APP_DIR`을 `git reset --hard`로 덮어쓰기 때문에 **실행 중인 스크립트 파일이 도중에 교체되는** 위험을 회피
- Jenkins 이미지를 buildx 포함 버전으로 재빌드 (`docker compose up -d --build jenkins`) → `buildx v0.37.0` 확인, **job/자격증명 모두 보존**됨 (`jenkins_home`이 named volume)

### 빌드 #2: Build & Test 통과, Deploy에서 git 인증 실패
- `Finished: FAILURE`, 그러나 **Build & Test 단계는 통과** → jar COPY 수정이 실제로 동작함을 확인
- 실패 지점: `deploy.sh`의 소스 동기화
  ```
  [deploy] 소스 동기화
  fatal: could not read Username for 'https://lab.ssafy.com': No such device or address
  ```
- 원인: `git fetch`가 **Jenkins 컨테이너 안**에서 실행되는데, 자격증명은 호스트의 `~/.git-credentials`에만 저장돼 있어 컨테이너에서는 보이지 않음
- 수정
  - `Jenkinsfile`: Deploy 단계를 `withCredentials(gitlab-https)`로 감싸 `GIT_USER`/`GIT_TOKEN` 주입
  - `deploy.sh`: 두 변수가 있으면 인라인 git credential helper로 인증 (토큰을 디스크에 쓰지 않고 helper 표준출력으로만 전달, 기존 `credential.helper`는 빈 값으로 먼저 덮어 간섭 차단). 없으면 호스트 수동 실행으로 간주해 저장된 helper 사용
  - 커밋 전 `git credential fill`로 helper가 주입된 값을 올바르게 반환하는지 검증

### 웹훅이 수신은 되는데 트리거가 안 되는 문제
- 증상: GitLab에서 웹훅은 계속 도착(`WebHook called` 로그)하는데 `triggered for push` 로그가 없고 빌드도 안 생김. 반면 Claude가 만든 테스트 페이로드는 정상 트리거됨
- 진단: **잘못된 secret으로 일부러 POST해보니 로그 패턴이 GitLab 웹훅과 완전히 동일**(`WebHook called`만 찍히고 트리거 없음)했고 응답은 **401**
  - 즉 secret 불일치 시 플러그인이 조용히 401만 반환하고 별도 경고 로그를 남기지 않음 → 이 패턴이 곧 secret 불일치의 지문
- 사용자가 GitLab webhook의 Secret token을 `~/.jenkins-webhook-secret` 값으로 다시 정확히 입력 → 해결
- 참고: 사용자가 "webhook secret에 Jenkins initial admin password를 넣는 것이냐"고 확인 질문. 셋(초기 admin 비밀번호 / 웹훅 secret / GitLab PAT)이 서로 다른 값임을 표로 정리해 안내함

### 빌드 #3: end-to-end 성공 (Day 2 목표 달성)
- GitLab에서 **실제 MR/push → 웹훅 → 자동 빌드** 로 트리거됨 (`05:49:22 triggered for push`)
- `Finished: SUCCESS`
  ```
  [deploy] APP_DIR=/home/ubuntu/app / branch=develop
  [deploy] 소스 동기화
  [deploy] 현재 커밋: f18512e [S15P21D105-570] [BE] Docs: ERD 및 데이터 무결성 정책 정비
  [deploy] 이미지 빌드 및 컨테이너 기동 (mysql redis backend nginx)
  [deploy] nginx 설정 리로드 (conf.d 변경 반영)
  [deploy] 헬스체크: http://nginx/api/health
  [deploy]   대기중... (1/40, http_code=502)
  [deploy]   대기중... (2/40, http_code=502)
  [deploy] 헬스체크 통과 (3회차)
  배포 성공: origin/develop (3)
  ```
  - 재기동 중 502가 두 번 뜬 뒤 통과 — 헬스체크 폴링(5초 간격 40회)이 의도대로 동작함을 확인
- 배포 직후 외부 검증
  ```
  $ curl http://j15D105.p.ssafy.io/api/health
  {"success":true,"data":{"status":"UP"},"message":null}   HTTP 200
  ```
  - 컨테이너: backend/mysql/redis 모두 `healthy`, 배포된 커밋 `f18512e`

## Day 2 완료 처리
- **GitLab push/MR → Jenkins 자동 빌드(테스트 포함) → EC2 배포 → 헬스체크** 전 구간 자동화 달성
- 원래 Day 2 목표(더미 앱으로 배관만 검증)를 넘어, **실제 백엔드 + MySQL + Redis가 배포되어 서비스되는 상태**까지 도달 (구 Day 4·5 범위 일부 선취)
- Day 4는 DB 고도화(초기화 스크립트/백업/튜닝), Day 5는 시크릿 정식화 중심으로 범위 조정 예정

## 다음 기록할 것 (TODO)
- [x] `infra/jenkins/Dockerfile` 작성 결과
- [x] `docker-compose.yml` jenkins 서비스 추가 결과
- [x] `infra/nginx/conf.d/default.conf` 작성 결과 (/jenkins/ 서브패스 프록시 + 기본 응답)
- [x] EC2에서 `getent group docker`로 DOCKER_GID 실측 + `.env` 반영
- [x] EC2에서 `git pull` + `docker compose up -d --build` 결과
- [x] Jenkins 웹 UI 외부 접근 경로 확인 (`/jenkins/login` 200)
- [x] 브라우저에서 초기 setup wizard 진행 (admin 계정 생성)
- [x] MySQL/Redis/backend 서비스 + Jenkinsfile + deploy.sh 작성
- [x] EC2 `.env`에 `MYSQL_*`, `REDIS_PASSWORD`, `CORS_ALLOWED_ORIGINS` 채우기
- [x] EC2에서 스택 기동 + `/api/health` 200 수동 확인 (파이프라인 붙이기 전 선검증)
- [x] GitLab PAT 발급 + EC2 `credential.helper store` 설정 (**사용자 액션**)
- [x] Jenkins Credentials에 GitLab 토큰 등록 (**사용자 액션**)
- [x] Jenkins Pipeline Job 생성 (SCM: GitLab, Jenkinsfile 경로 루트)
- [x] GitLab Webhook 등록 (Push 이벤트 → Jenkins) (**사용자 액션**)
- [x] push → 자동 빌드/배포 end-to-end 검증 (빌드 #3 SUCCESS)
- [x] **Day 2 완료 처리**

## Day 3로 이월할 것
- [ ] 도메인 HTTPS(Let's Encrypt) 적용 — 적용 후 GitLab webhook URL을 https로 바꾸고 SSL verification 다시 켜기
- [ ] nginx `/` 라우팅을 임시 200 응답에서 실제 구성으로 교체
- [ ] Jenkins 웹 UI도 HTTPS로 (현재 HTTP로 로그인 중 — 자격증명이 평문 전송됨)
- [ ] 팀 공지: push하면 자동 배포된다는 것과 Jenkins UI 위치 (**사용자 액션**)
- [ ] 파이프라인 개선 여지: 테스트 리포트를 Jenkins UI에 게시(현재는 이미지 빌드 로그로만 확인), 배포 실패 시 이전 이미지로 롤백
