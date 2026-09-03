# Day 2 — CI/CD 파이프라인 조기 구축 (P0, 더미 앱으로 먼저 검증)

원래는 CI/CD를 Day 5로 잡았지만, 팀원들이 코드를 push하기 시작하기 전에 **build→deploy 파이프라인 자체를 먼저 검증**해두는 게 이후 대기시간을 줄인다는 조언에 따라 앞당긴다. 실제 백엔드 코드/DB가 아직 없어도 **더미(hello-world) 컨테이너로 파이프라인 배관만 먼저 뚫는다.**

이 Day가 끝나면: `main`/`develop`에 push → Jenkins가 감지 → 더미 이미지 빌드 → EC2에서 컨테이너로 기동, 까지 자동화되어 있어야 한다. 팀원 코드가 들어오면 Day 5에서 더미 이미지를 실제 백엔드 Dockerfile로 "교체"만 하면 된다.

## 왜 이 시점에 가능한가 (의존성 정리)

- 필요한 건 Day 1의 **Docker/Compose 설치**와 **레포 구조/브랜치 전략**뿐이다.
- Nginx 정식 라우팅(도메인/SSL/`/api/` 프록시)은 필요 없다 — Jenkins만 외부에서 webhook을 받을 수 있으면 된다. 이건 최소 Nginx 설정(또는 포트 직접 노출)으로 충분하며, 정식 Nginx 구성은 Day 3에서 확장한다.
- MySQL/Redis/실제 백엔드 Dockerfile은 필요 없다 — 더미 이미지로 배관만 검증한다.

## 체크리스트

### 1. Jenkins 기동
- [ ] `docker-compose.yml`에 `jenkins` 서비스 추가 (Docker 컨테이너), `jenkins_home` 볼륨 영속화
- [ ] 필요한 플러그인만 최소 설치 (Git, GitLab, Docker Pipeline, Credentials Binding) — 리소스 아끼기
- [ ] 최소 Nginx 설정 또는 임시 포트로 Jenkins 웹 UI/웹훅 엔드포인트를 외부에서 접근 가능하게 함 (Day 1에서 실측한 열린 포트 기준)
  - 80/443만 열려있다면 Nginx에 `/jenkins/` 서브패스만 우선 추가 (SSL/도메인은 Day 3에서)

### 2. GitLab Webhook 연동
- [ ] SSAFY Lab 프로젝트에 Webhook 등록 (Push/Merge Request 이벤트 → Jenkins) — 이전에 같은 조합(GitLab Lab + Jenkins)을 써본 팀원에게 당시 설정법 확인 (**사용자 액션**)
- [ ] 안 되면 GitLab Poll SCM(주기적 폴링)으로 대체
- [ ] Jenkins Credentials에 GitLab 연동 토큰 등록

### 3. 더미 파이프라인
- [ ] 레포에 더미 앱 하나 준비 (예: `backend-placeholder/`에 "Hello" 응답하는 최소 Spring Boot 또는 nginx 정적 페이지 + `Dockerfile`) — 실제 백엔드 Dockerfile은 Day 5에서 이 자리를 대체
- [ ] `Jenkinsfile` 작성: `checkout` → `build`(더미 이미지 빌드) → `deploy`(`docker compose up -d --build`로 더미 컨테이너 기동)
- [ ] `infra/scripts/deploy.sh` 초안 작성 (더미 기준으로 먼저 만들고 Day 5에서 실제 앱 기준으로 갱신)
- [ ] `main`/`develop` push 시 자동으로 더미 컨테이너가 재배포되는지 end-to-end 확인

### 4. 팀 공유
- [ ] 파이프라인이 살아있다는 것과 사용법(브랜치 push하면 자동 배포됨, 로그는 Jenkins UI에서 확인)을 팀에 공지 (**사용자 액션**) — 각자 코드 작업 시작 전에 미리 알려주면 이후 Day 5 전환이 매끄러움

## 산출물
- Jenkins가 떠 있고, GitLab push → 자동 build/deploy가 더미 앱 기준으로 동작
- Day 5에서 실제 백엔드로 교체할 지점(Dockerfile 경로, Jenkinsfile의 build/deploy 스테이지)이 명확히 표시됨

## Day 5로 넘길 것
- 더미 이미지 → 실제 백엔드 Dockerfile 교체
- 시크릿(DB, API 키 등)을 Jenkins Credentials/`.env`로 정식 등록
