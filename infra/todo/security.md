# 인프라 보안 방침 (2026-09-07 수립)

핀테크 서비스인 만큼 보안을 짚고 가되, **범위를 명확히 한다.**

## 0. 전제와 적정선

- 이 프로젝트는 **SSAFY 교육용 금융망(모의 데이터)** 을 사용한다. 실제 자금 이체나 실 고객 PII를 다루지 않는다.
- 따라서 KMS/HSM, WAF, SIEM, 침투테스트, PCI-DSS 준수 같은 것은 **범위 밖**으로 둔다. 8일 스프린트에 1인 담당이라는 제약도 함께 고려한 판단이다.
- 대신 다음 두 가지는 반드시 만족시킨다.
  1. **EC2가 실제로 탈취되지 않을 것** — 공개 인터넷에 노출된 서버이므로 이건 실존하는 위험이다.
  2. **설계 의도를 설명할 수 있을 것** — 왜 이렇게 했고 무엇을 의도적으로 뺐는지 발표에서 말할 수 있어야 한다.

## 1. 현재 잘 되어 있는 것 (2026-09-07 실측)

| 항목 | 상태 |
| --- | --- |
| SSH 비밀번호 인증 | 비활성 (`passwordauthentication no`), 공개키 전용 |
| DB / Redis / backend 외부 노출 | 없음 — 호스트에 바인딩된 포트는 nginx의 80/443뿐 |
| DB 계정 분리 | root와 앱 전용 계정(`dodam`) 분리 |
| `.env` 권한 | `600 ubuntu:ubuntu` |
| 시크릿 생성 방식 | EC2에서 `openssl rand`로 생성, 대화/로그를 거치지 않음 |
| 시크릿 커밋 방지 | `.gitignore`에 `.env`, `*.pem`, `*.key` 포함 |
| 민감정보 저장 설계 | ERD가 `VARBINARY` 암호화 + `_hash` 조회 컬럼으로 설계됨 |
| 컨테이너 로그 | 로테이션 설정(10MB × 3)으로 디스크 고갈 방지 |

## 2. 식별된 위험과 대응 (심각도 순)

### R1. Jenkins 공개 노출 + docker.sock 접근권 ★최우선 — ✅ **해소 (2026-09-07, Day 3)**
> 적용 완료: nginx가 `/jenkins/project/`(웹훅)만 프록시하고 그 외 `/jenkins/*`는 403.
> Jenkins는 `127.0.0.1:8080`에만 바인딩되어 외부에서 접근 불가. 검증: 외부 403 / SSH 터널 200.

- **문제**: Jenkins 컨테이너가 `docker` 그룹(gid 988)에 속해 호스트 Docker 데몬을 조작할 수 있다. Jenkins가 뚫리면 `docker run -v /:/host` 한 줄로 호스트 루트를 장악할 수 있고, 그러면 DB 전체와 `.env`의 모든 시크릿, **데이터 암호화 키까지 함께 털린다.** DB 컬럼 암호화를 아무리 잘 해도 무력화된다.
- 게다가 현재 HTTP라 Jenkins admin 비밀번호가 평문으로 오간다.
- **대응 (Day 3)**: **웹훅 엔드포인트만 공개하고 UI는 닫는다.**
  - nginx에서 `/jenkins/project/`(웹훅 수신 경로)만 프록시하고, 그 외 `/jenkins/*`는 403으로 차단
  - Jenkins UI는 호스트 루프백에만 바인딩(`127.0.0.1:8080:8080`)하고 **SSH 터널로만 접근**
    ```bash
    ssh -L 8080:127.0.0.1:8080 ssafy-ec2
    # 브라우저에서 http://localhost:8080/jenkins/
    ```
  - 트레이드오프: 빌드 로그를 볼 때마다 터널을 열어야 한다. Jenkins UI를 보는 사람이 인프라 담당 1인이라 감수할 만하다고 판단.
- **왜 DooD를 유지하는가**: DinD로 바꿔도 특권 컨테이너가 필요해 위험이 크게 줄지 않고, 남은 일정에 빌드 파이프라인을 다시 만드는 비용이 크다. **대신 노출면을 없애는 쪽으로 방어**한다 — 이 판단 근거를 발표에서 설명할 것.

### R2. 전 구간 평문 HTTP — ⚠️ **부분 해소 (로테이션 1건 미완)**
- **문제**: Jenkins 로그인 자격증명과 GitLab webhook secret이 이미 여러 차례 평문으로 전송됐다.
- **대응 (Day 3)**: HTTPS 적용 후 **이미 노출된 값들을 로테이션**한다.
  - [ ] **Jenkins admin 비밀번호 변경 — 미완 (사용자 액션)**
        접속: `ssh -L 8080:127.0.0.1:8080 ssafy-ec2` → `http://localhost:8080/jenkins/`
  - [x] webhook secret 재생성 → Jenkins job config + GitLab webhook 양쪽 갱신 (새 secret 200 / 옛 secret 401 검증)
  - [ ] GitLab PAT 재발급 검토 (HTTP로 오간 적 있으면)
- [x] HTTPS 적용, HTTP → HTTPS 301 리다이렉트, HSTS 헤더 적용
  - 단 **HSTS `max-age`는 1일로 낮게 설정**해둔 상태 — 443 장애 시 HTTP 폴백을 막지 않기 위함. 운영 안정화 후 상향 필요

### R3. backend 컨테이너가 root로 실행
- **문제**: `docker exec backend id` → `uid=0(root)`. 컨테이너 탈출 취약점이 터지면 피해가 커진다.
- **대응 (Day 5)**: Dockerfile 런타임 스테이지에 비루트 유저 추가
  ```dockerfile
  RUN useradd -r -u 10001 -g root dodam
  USER 10001
  ```
  - 추가로 compose에 `security_opt: [no-new-privileges:true]`, `cap_drop: [ALL]` 적용 검토

### R4. develop push = 즉시 프로덕션 배포 (공급망)
- **문제**: 승인 게이트가 없다. 팀원 계정 하나가 탈취되면 임의 코드가 서버에서 실행된다.
- **대응 (Day 5, 사용자 액션)**: GitLab에서 `develop`을 **Protected branch**로 설정
  - 직접 push 금지, MR 머지만 허용 → 최소 1인 리뷰가 배포 전 게이트가 됨
  - 이미 팀이 MR 흐름을 쓰고 있으므로 추가 부담이 거의 없다

### R5. 백업 부재
- **문제**: 실수든 공격이든 복구 수단이 없다.
- **대응 (Day 4)**: 일일 `mysqldump` + 7일 보관 + **복구 리허설**
  - 덤프 파일은 `600` 권한, 가능하면 gzip + 암호화
  - **암호화 키를 덤프와 다른 위치에 백업** — 같이 두면 한 번에 유출되고, 없으면 복구가 불가능하다

### R6. 저비용 방어 미적용
- **대응 (Day 3 / Day 5)**
  - [ ] nginx `limit_req`로 `/api/` 레이트 리밋 (특히 로그인/회원가입 경로를 더 엄격히)
  - [ ] 보안 헤더: `HSTS`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`
  - [ ] `server_tokens off` (nginx 버전 숨김)
  - [ ] `/actuator/`가 외부로 새어나가지 않도록 nginx에서 차단 (Prometheus는 컨테이너 네트워크로만)
  - [ ] fail2ban으로 SSH 무차별 대입 차단
  - [ ] 감사 로그: Jenkins 로그인·배포 이력, nginx 접근 로그 보존 (Day 6 Loki와 연결)

## 3. 의도적으로 하지 않는 것 (발표용 근거)

| 항목 | 안 하는 이유 |
| --- | --- |
| KMS/HSM 기반 키 관리 | AWS 콘솔 접근 권한이 없고, 교육용 모의 데이터 범위에 과함 |
| WAF | 트래픽이 팀 내부 수준, nginx 레이트 리밋으로 대체 |
| SIEM / 침투테스트 | 1인 담당 8일 일정 밖 |
| 서비스 간 mTLS | 전부 단일 호스트의 컨테이너 네트워크 내부 통신 |
| DinD 전환 | 특권 컨테이너가 필요해 위험이 크게 줄지 않음 → 노출면 제거로 대응 (R1 참고) |
| DB 저장 시 TDE | 애플리케이션 레벨 컬럼 암호화(ERD 설계)로 이미 커버 |

## 4. 반영 위치

| 위험 | 반영된 Day |
| --- | --- |
| R1 Jenkins 노출 | Day 3 |
| R2 HTTPS + 시크릿 로테이션 | Day 3 |
| R3 컨테이너 비루트 | Day 5 |
| R4 배포 승인 게이트 | Day 5 |
| R5 백업 | Day 4 |
| R6 저비용 방어 | Day 3 / Day 5 |
| 최종 점검 | Day 8 |
