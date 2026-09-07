# Day 3 — Nginx 리버스 프록시 정식화 & 도메인/SSL (P0/P1)

> **2026-09-07 재검토 반영.** Day 2가 계획을 초과 달성해서 이 문서의 상당 부분이 이미 끝났다.
> 아래 "Day 2에서 이미 끝난 것"을 먼저 확인하고, 남은 항목에 집중한다.

## Day 2에서 이미 끝난 것 (재작업 불필요)

- ✅ `/api/` → 백엔드 프록시 (더미가 아니라 **실제 Spring Boot에 연결된 상태**로 동작 중)
- ✅ `/jenkins/` 서브패스 프록시 (`--prefix=/jenkins` + 리버스 프록시 헤더)
- ✅ 80/443만 호스트 바인딩, mysql·redis·backend는 컨테이너 네트워크로만 통신 (외부 포트 미노출)
- ✅ ufw 22/80/443 허용

## 이번 Day의 핵심: HTTPS

지금 **Jenkins 로그인과 GitLab webhook이 전부 평문 HTTP로 오간다.** 자격증명과 webhook secret이 그대로 노출되는 상태라, HTTPS는 "있으면 좋은 것"이 아니라 이번 Day의 P0이다.

### 1. 443 실제 개방 여부 확인 (가장 먼저 — 🔲 확인 필요)
- [ ] 80은 확인됐지만 **443은 아직 외부에서 검증한 적이 없다.** SSL 작업을 시작하기 전에 먼저 확인할 것
  - nginx에 443 listen을 임시로 열고 자체서명 인증서로 응답시킨 뒤, 로컬 PC에서 `curl -k https://j15D105.p.ssafy.io` 로 확인
  - 막혀 있으면(타임아웃) → 조교에게 443 개방 요청 (**사용자 액션**). 그때까지는 HTTP 유지하고 아래 SSL 작업 보류

### 2. Let's Encrypt 인증서 발급
- 도메인은 확정: **`j15D105.p.ssafy.io`** (→ `43.203.198.18`)
- [ ] Certbot으로 발급 — nginx가 80을 물고 있으므로 **webroot 방식**(`--webroot`)이 컨테이너 재시작 없이 깔끔함
  - nginx에 `/.well-known/acme-challenge/` 정적 서빙 location 추가
  - certbot 컨테이너가 같은 볼륨에 challenge 파일을 쓰도록 구성
- [ ] 인증서/키를 named volume 또는 `infra/nginx/certs`에 보관 (**`.gitignore` 확인 — 절대 커밋 금지**)
- [ ] 자동 갱신: certbot 컨테이너를 주기 실행(`--deploy-hook`으로 nginx reload) 또는 호스트 cron
- [ ] HTTP → HTTPS 301 리다이렉트 (단, ACME challenge 경로는 리다이렉트 예외)

### 3. HTTPS 전환에 딸려오는 후속 작업 (놓치기 쉬움)
- [ ] **GitLab webhook URL을 `https://`로 변경**하고 **Enable SSL verification 다시 켜기** (**사용자 액션**)
      — Day 2에서 HTTP라 껐던 것. 안 바꾸면 계속 평문으로 secret이 전송됨
- [ ] Jenkins 시스템 설정의 Jenkins URL을 `https://j15D105.p.ssafy.io/jenkins/`로 변경
      — 현재 `ReverseProxySetupMonitor` 경고가 로그에 계속 찍히는 것도 이 설정 때문
- [ ] 백엔드 `CORS_ALLOWED_ORIGINS` 갱신 (현재 `http://localhost:8081`만 등록됨)
- [ ] Day 7 소셜 로그인 Redirect URI를 https 도메인 기준으로 등록하도록 메모

### 4. `/` 루트 라우팅 결정
현재 `/`는 `infra day2: nginx up` 임시 텍스트를 반환한다. FE가 Expo(`app.json`에 `web.output: "static"`)라서 **웹 정적 빌드를 nginx가 서빙하는 선택지가 있다.**
- [ ] FE팀과 확인 (**사용자 액션**): 웹 배포를 할 것인가, 앱(Expo Go / EAS 빌드)만 할 것인가
  - 웹도 한다 → `expo export --platform web` 산출물을 nginx가 서빙하도록 `/` 구성 (배포 파이프라인은 Day 7)
  - 앱만 한다 → `/`는 간단한 상태 페이지나 404로 정리
- [ ] Day 6에서 붙일 `/grafana/` 서브패스 자리 예약 (설정 스켈레톤만)

### 5. Jenkins 노출 축소 ★보안 최우선 (상세: [[security.md]] R1)
Jenkins 컨테이너는 `docker` 그룹에 속해 호스트 Docker 데몬을 조작할 수 있다. **Jenkins가 뚫리면 호스트 루트가 넘어가고 DB·`.env`·암호화 키까지 전부 털린다.** 공개 인터넷에 UI를 열어둘 이유가 없다.

- [ ] nginx에서 **웹훅 경로만 공개**하고 나머지 UI는 차단
  - `/jenkins/project/` → jenkins 프록시 유지 (GitLab webhook 수신용)
  - 그 외 `/jenkins/*` → `403` 차단
- [ ] Jenkins를 **호스트 루프백에만 바인딩** — compose에 `ports: ["127.0.0.1:8080:8080"]` 추가 (외부에서는 접근 불가)
- [ ] UI는 SSH 터널로 접근하고, 방법을 `infra/README.md`에 기록
  ```bash
  ssh -L 8080:127.0.0.1:8080 ssafy-ec2
  # 브라우저: http://localhost:8080/jenkins/
  ```
- [ ] 차단 후 **웹훅이 여전히 동작하는지 반드시 확인** (develop에 push해서 빌드가 도는지)

### 6. 시크릿 로테이션 (HTTPS 적용 직후 — [[security.md]] R2)
지금까지 평문 HTTP로 오간 값들은 노출된 것으로 간주한다.
- [ ] Jenkins admin 비밀번호 변경
- [ ] webhook secret 재생성 → Jenkins job config와 GitLab webhook **양쪽** 갱신
- [ ] GitLab PAT 재발급 검토

### 7. 저비용 방어 ([[security.md]] R6)
- [ ] 보안 헤더: `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`
- [ ] `server_tokens off;` (nginx 버전 숨김)
- [ ] `limit_req`로 `/api/` 레이트 리밋 — 로그인/회원가입 경로는 더 엄격하게
- [ ] `/actuator/`가 외부로 노출되지 않도록 nginx에서 차단 (Day 6 Prometheus는 컨테이너 네트워크로만 접근)
- [ ] fail2ban 설치해 SSH 무차별 대입 차단

### 8. 검증
- [ ] `https://j15D105.p.ssafy.io/api/health` → 200 (실제 백엔드 응답)
- [ ] `http://` 접근이 `https://`로 리다이렉트되는지
- [ ] 인증서 만료일 확인, 갱신 dry-run (`certbot renew --dry-run`)
- [ ] **외부에서 `https://.../jenkins/`가 403인지** (UI 차단 확인)
- [ ] **SSH 터널로는 Jenkins UI에 접속되는지**
- [ ] **HTTPS 전환 + Jenkins 차단 후에도 CI/CD가 여전히 도는지** — develop에 커밋 하나 push해서 빌드 성공까지 확인
      (webhook URL/SSL 검증/경로 차단을 한꺼번에 바꾸므로 여기서 깨지기 가장 쉬움. 하나씩 바꾸고 그때마다 확인할 것)

## 산출물
- `j15D105.p.ssafy.io`가 HTTPS로 서비스되고, 인증서 자동 갱신이 걸려 있음
- Jenkins/webhook이 HTTPS 경로로 전환되어 자격증명이 평문으로 오가지 않음
- `/` 라우팅 방침 확정 (FE 웹 서빙 여부)

## 리스크
- **443이 안 열려 있으면 HTTPS 자체가 막힘** → 조교 요청이 필요하고, 회신까지 시간이 걸릴 수 있으므로 Day 3 시작하자마자 확인할 것
- HTTPS 전환 중 webhook 설정을 바꾸다 CI/CD가 조용히 멈출 수 있음 → 전환 직후 반드시 push 테스트 (Day 2에서 secret 불일치 시 **401만 반환하고 경고 로그가 없어** 진단이 어려웠던 전례 있음)
