# infra

도담 서비스 인프라. EC2 1대 위에 Docker Compose로 전체 스택을 구동한다.

| 문서 | 내용 |
| --- | --- |
| [docs/ci-cd.md](docs/ci-cd.md) | **배포 파이프라인 전체 흐름**, 실패 시 롤백, 알림, 트러블슈팅 |
| [todo/00-overview.md](todo/00-overview.md) | 인프라 전체 계획과 확정 사항 |
| [todo/security.md](todo/security.md) | 보안 방침, 식별된 위험과 대응 |
| [todo/jiw/](todo/jiw/) | 일자별 실제 작업 로그 (판단 근거와 실패 사례 포함) |

## 접속 정보

| 대상 | 주소 | 비고 |
| --- | --- | --- |
| 서비스 API | `https://j15D105.p.ssafy.io/api/...` | HTTP로 오면 HTTPS로 301 |
| 헬스체크 | `https://j15D105.p.ssafy.io/api/health` | 내부 점검용으로 HTTP도 허용 |
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

## 스택 띄우기

```bash
cd ~/app/infra
cp .env.example .env   # 값 채워넣기 (절대 커밋 금지)
docker compose up -d
```

⚠️ **`docker compose down -v`는 절대 실행하지 말 것** — `mysql_data`(DB 전체)와 `jenkins_home`(job·자격증명)이 삭제된다.

## 배포

`develop`에 머지되면 GitLab 웹훅이 Jenkins를 깨우고, 자동으로 빌드 → 테스트 → 배포 → 헬스체크까지 진행된다.

- 파이프라인 정의: 저장소 루트 `Jenkinsfile`
- 배포 스크립트: `scripts/deploy.sh` (소스 동기화 → 이미지 빌드 → 컨테이너 기동 → `/api/health` 폴링)
- 배포 대상 서비스: `mysql redis backend nginx certbot` — **jenkins는 제외**한다.
  배포가 Jenkins 컨테이너 안에서 실행되므로, jenkins까지 재시작하면 자기 빌드를 죽인다.
- **jenkins 이미지를 바꿨을 때만** 호스트에서 수동으로:
  ```bash
  cd ~/app/infra && docker compose up -d --build jenkins
  ```

## TLS 인증서

Let's Encrypt를 certbot 컨테이너로 발급/갱신한다.

- 인증서 위치: `nginx/certbot/conf/live/j15d105.p.ssafy.io/` (**gitignore됨**)
- 갱신: certbot 컨테이너가 12시간마다 `certbot renew` 시도, nginx는 6시간마다 자체 reload로 새 인증서를 반영
  (certbot에 docker 권한을 주지 않기 위해 이 방식을 씀)
- 수동 확인:
  ```bash
  docker compose run --rm --entrypoint certbot certbot renew --dry-run
  ```

## 진행 상황

- [x] Day 1: 서버 기초 설정 & 리포 구조
- [x] Day 2: CI/CD 파이프라인 (Jenkins + GitLab webhook, 실제 백엔드 자동 배포까지)
- [x] Day 3: HTTPS(자동 갱신 검증 완료), Jenkins 노출 축소, 레이트 리밋·보안 헤더·fail2ban
- [ ] Day 4: MySQL/Redis 고도화 (백업·복구, 운영 설정)
- [ ] Day 5: 시크릿 관리 & 배포 안정화
- [ ] Day 6: 모니터링 스택
- [ ] Day 7: 외부 연동 & FE 배포
- [ ] Day 8: 통합 리허설 & 문서화

## 구조

```
infra/
  docker-compose.yml     # nginx, jenkins, certbot, mysql, redis, backend
  .env.example           # 키 이름만 (실제 값은 EC2의 .env에만)
  jenkins/Dockerfile     # docker CLI + 플러그인 포함 Jenkins 이미지
  nginx/conf.d/          # 리버스 프록시, TLS, 레이트 리밋, 보안 헤더
  nginx/certbot/         # 인증서 (gitignore)
  mysql/init/
  monitoring/{prometheus,grafana,loki}/
  scripts/deploy.sh
  todo/                  # 일자별 계획 + security.md + jiw/(실제 작업 로그)
```

## 시크릿 위치 (값은 여기에 적지 않는다)

| 값 | 위치 |
| --- | --- |
| DB/Redis 비밀번호, JWT 시크릿 | EC2 `~/app/infra/.env` (600) |
| GitLab 접근 토큰 | Jenkins Credentials(`gitlab-https`, `ec2-jenkins-deploy`), EC2 `~/.git-credentials` |
| 웹훅 secret | EC2 `~/.jenkins-webhook-secret` (600), Jenkins job 설정 |
| TLS 개인키 | EC2 `~/app/infra/nginx/certbot/conf/` |
