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

## 다음 기록할 것 (TODO)
- [x] `infra/jenkins/Dockerfile` 작성 결과
- [x] `docker-compose.yml` jenkins 서비스 추가 결과
- [x] `infra/nginx/conf.d/default.conf` 작성 결과 (/jenkins/ 서브패스 프록시 + 기본 응답)
- [ ] EC2에서 `getent group docker`로 DOCKER_GID 실측 + `.env` 반영
- [ ] EC2에서 `git pull` + `docker compose up -d --build` 결과
- [ ] Jenkins 웹 UI(`http://j15D105.p.ssafy.io/jenkins/`) 접근 확인 + 초기 setup wizard 진행
