# Day 3 — 실제 진행 기록

계획: `infra/todo/day-03.md`, 보안 방침: `infra/todo/security.md`

## 2026-09-07

### 443 개방 여부 확인 (SSL 작업의 선행 조건)
- Day 1에서 얻은 신호를 재활용: 443은 docker-proxy가 호스트에서 듣고 있지만 컨테이너 안엔 리스너가 없는 상태 → **보안그룹이 열려 있으면 즉시 refused, 막혀 있으면 timeout**
- 결과: `curl https://j15D105.p.ssafy.io/` → **58ms 만에 Connection refused**
- 판정: **443 인바운드 열려 있음.** 조교 요청 없이 SSL 진행 가능

### 작업 방식
- Day 3는 nginx 설정을 여러 번 고쳐가며 검증해야 해서, **EC2에서 직접 수정·검증하고 검증된 최종본만 커밋**하는 방식으로 진행 (매번 push를 기다리면 왕복이 너무 김)
- 로컬 저장소가 항상 정본이고, EC2에는 scp로 반영 → 최종적으로 커밋

### Let's Encrypt 인증서 발급
- `.gitignore`에 `infra/nginx/certbot/`, `*.crt`, `infra/.env.bak.*` 추가 (**인증서/키 커밋 방지**)
- nginx에 ACME 챌린지 경로 추가 (`location ^~ /.well-known/acme-challenge/` → `/var/www/certbot`)
- `docker-compose.yml`에 `certbot` 서비스 추가
  - 12시간마다 `certbot renew` 시도하는 루프
  - **certbot에는 docker 권한을 주지 않았다** — 대신 nginx가 6시간마다 스스로 reload해서 새 인증서를 집어가게 함 (`--deploy-hook`으로 docker를 부르면 certbot에 소켓 권한이 필요해져 R1과 같은 위험이 생김)
- 발급 전 검증
  1. 챌린지 경로에 테스트 파일을 두고 **외부에서 200으로 읽히는지 확인** → OK
  2. `certbot --dry-run` → `The dry run was successful.`
  3. 실제 발급 → `Successfully received certificate.`, 만료 **2026-12-06**
- 계정 이메일은 `jiwooclover@gmail.com` 사용 (외부 서비스로 나가는 값이라 사용자에게 먼저 확인함). 갱신이 조용히 실패해도 만료 경고 메일을 받기 위함 — Day 9~12에 인프라를 안 볼 예정이라 안전망이 필요

### HTTPS 및 보안 설정 적용
`infra/nginx/conf.d/default.conf` 전면 재작성:
- 80 포트: ACME 경로와 `/api/health`만 남기고 나머지는 **HTTPS로 301**
  - `/api/health`를 리다이렉트에서 제외한 이유: `deploy.sh`와 컨테이너 헬스체크가 내부 네트워크에서 `http://nginx/api/health`를 호출하는데, 301이 오면 **배포 검증이 실패**한다. 노출되는 정보는 `{"status":"UP"}` 뿐이라 위험 없음
- 443 포트: TLSv1.2/1.3, 세션 캐시, 티켓 비활성
- 보안 헤더: HSTS, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`
  - **HSTS는 의도적으로 max-age를 1일로 짧게** 잡음 — 12일짜리 프로젝트에서 443이 막히는 사고가 나면 긴 max-age가 팀원 브라우저를 HTTP로 못 돌아가게 묶어버린다. 운영이 안정되면 1년으로 올릴 것
- `server_tokens off` (nginx 버전 숨김)
- 레이트 리밋: 일반 API 20r/s(버스트 40), `/api/auth/`는 10r/m(버스트 5) — 인증 엔드포인트는 아직 미구현이지만 미리 걸어둠
- `/actuator/` → 404로 외부 차단 (Day 6 Prometheus는 컨테이너 네트워크로 직접 접근할 예정)

### Jenkins 노출 축소 (security.md R1)
- `docker-compose.yml`: jenkins에 `ports: ["127.0.0.1:8080:8080"]` — **루프백에만 바인딩**
- nginx: `/jenkins/project/`(웹훅)만 프록시, 그 외 `/jenkins/*`는 **403**
- UI 접근은 SSH 터널로:
  ```bash
  ssh -L 8080:127.0.0.1:8080 ssafy-ec2   →   http://localhost:8080/jenkins/
  ```
- **터널 경유 접속 실제 검증**: 터널을 열고 `http://localhost:18080/jenkins/login` → **200**

### 검증 결과 (외부에서)
| 항목 | 결과 |
| --- | --- |
| `https://.../api/health` | **200** `{"success":true,"data":{"status":"UP"}}` |
| `http://.../` | **301** → `https://j15d105.p.ssafy.io/` |
| `https://.../jenkins/` | **403** (UI 차단됨) |
| `https://.../jenkins/project/...` (secret 없이) | **401** (웹훅 경로는 살아있음) |
| `https://.../actuator/health` | **404** (차단됨) |
| 보안 헤더 | HSTS / nosniff / DENY / Referrer-Policy 모두 적용, `server: nginx`로 버전 숨김 |

### 헤더 중복 정리
- 백엔드(Spring Security)도 `X-Frame-Options`, `X-Content-Type-Options`를 보내서 nginx 것과 **중복 출력**되고 있었음
- 프록시 location에 `proxy_hide_header`를 추가해 nginx 것만 남김 → 확인 결과 각 헤더 1개씩

### fail2ban
- 설치 및 활성화, `sshd` jail 동작 확인
- SSH는 이미 공개키 전용이라 실효는 제한적이지만, 스캐너 차단과 로그 노이즈 감소 효과

### 시크릿 로테이션 (security.md R2)
평문 HTTP로 오갔던 값은 노출된 것으로 간주하고 교체.
- **웹훅 secret 재생성** (`openssl rand -hex 20`), 기존 값은 `~/.jenkins-webhook-secret.old`로 보관
  - 처음엔 job `config.xml`의 평문 값을 sed로 치환하려 했으나 실패 → 확인해보니 **Jenkins가 job을 저장하면서 secretToken을 암호화**(`{AQ...}`)해둔 상태였음. 요소 전체를 새 평문 값으로 교체하고 재시작하는 방식으로 해결 (Jenkins가 로드 후 다시 암호화함)
  - 검증: 새 secret → **200**, 옛 secret → **401**
- **Jenkins URL 설정 변경** — `jenkins.model.JenkinsLocationConfiguration.xml`을 `http://localhost:8080/jenkins/`로 작성. UI를 터널로만 접근하게 됐으므로 실제 접근 주소와 일치시킴 (기존에 계속 뜨던 `ReverseProxySetupMonitor` 경고도 해소)

### 문서 갱신
- `infra/README.md` 전면 개편: 접속 정보 표, **Jenkins SSH 터널 접속법**, 배포 흐름(jenkins를 배포 대상에서 빼는 이유), TLS 갱신 구조, `down -v` 경고, 시크릿 위치 표
- `deploy.sh`의 `DEPLOY_SERVICES`에 `certbot` 추가

### 사고 — 배포가 Jenkins를 죽임 (depends_on 우회 경로)
Day 3 커밋이 develop에 머지되어 배포가 돌자 **Jenkins 컨테이너가 종료**됨 (`Exited (143)`), 절반만 생성된 `a553f8712024_jenkins` 컨테이너가 남음.

- **원인**: `nginx` 서비스에 `depends_on: - jenkins`가 걸려 있었음. 배포 스크립트가 `docker compose up -d --build mysql redis backend nginx certbot`을 실행하면, **compose가 nginx의 의존성으로 jenkins까지 끌고 들어와 재생성**한다. 그런데 그 배포는 바로 그 Jenkins 컨테이너 안에서 실행 중이므로 **자기 자신을 죽인다.**
- Day 2에서 `DEPLOY_SERVICES`로 jenkins를 제외해 막아뒀던 문제가, **`depends_on`이라는 다른 경로로 우회해서 되살아난 것**. 서비스 목록에서 빼는 것만으로는 부족했다.
- 다행히 nginx/backend/mysql/redis는 정상이라 **서비스 자체는 무중단**이었고, 죽은 것은 CI/CD뿐이었다.

**수정 (두 가지를 함께)**
1. `deploy.sh`의 compose up에 **`--no-deps`** 추가 — 의존성을 타고 비대상 서비스가 재생성되는 것을 차단
2. nginx에서 `depends_on: jenkins` 제거. **다만 그냥 지우면 안 됐다** — nginx는 `proxy_pass http://jenkins:8080` 의 호스트명을 **기동 시점에 해석**하기 때문에, jenkins(또는 backend)가 내려가 있으면 **nginx 자체가 기동에 실패**해서 컨테이너 하나 장애가 전체 장애로 번진다. 그래서 함께 변경:
   - `resolver 127.0.0.11 valid=10s ipv6=off;` (Docker 임베디드 DNS)
   - `proxy_pass`를 변수로 (`set $upstream_backend http://backend:8080; proxy_pass $upstream_backend;`) → **요청 시점 해석**으로 바뀌어, 업스트림이 없어도 nginx는 뜨고 해당 경로만 502를 반환

**검증**: jenkins를 중지한 상태에서 nginx를 재시작 → **nginx 정상 기동, `/api/health` 200 유지** 확인. 이전 구조였다면 여기서 nginx가 죽었다.

### 교훈 — EC2 직접 수정 방식의 위험
- Day 3는 속도를 위해 EC2에서 직접 수정하고 검증했는데, `deploy.sh`가 매 배포마다 `git reset --hard origin/develop`을 하므로 **커밋되지 않은 작업물은 다음 배포에 조용히 사라진다.**
- 실제로 중간에 "빌드 4~6이 내 설정을 덮어썼나?" 의심하는 상황이 있었고(확인 결과 빌드는 06:08, 작업은 06:35 이후라 겹치지 않았음), 만약 팀원이 그 사이 develop에 머지했다면 **HTTPS와 Jenkins 잠금이 조용히 풀렸을 것**이다.
- 앞으로는 EC2에서 실험하더라도 **검증 직후 즉시 커밋·머지**해서 정본과 서버 상태를 일치시킬 것.

## 남은 것 (사용자 액션 필요)
- [ ] **GitLab webhook 갱신** — 지금 `http://`를 가리켜서 301을 맞고 있고(GitLab 웹훅은 리다이렉트를 따라가지 않음), secret도 교체됨. URL을 https로 바꾸고 새 secret 입력, SSL verification 다시 켜기
- [ ] **Jenkins admin 비밀번호 변경** (평문 HTTP로 오갔음)
- [ ] GitLab PAT 재발급 검토
- [ ] 갱신 후 develop에 push해서 **파이프라인이 여전히 도는지 확인**

## 다음 기록할 것 (TODO)
- [ ] 웹훅 갱신 후 end-to-end 재검증 결과
- [ ] `certbot renew --dry-run` 실행 결과
- [ ] Day 3 완료 처리
