# Day 5 — 시크릿 관리 정식화 & 배포 안정화 (P0)

> **2026-09-07 재검토 반영.** 원래 이 Day의 핵심이던 "더미 → 실제 백엔드 교체"는 **Day 2에서 이미 완료**됐다.
> 남은 것은 **시크릿 관리 체계**와 **배포 안정성(롤백/리소스 제한)**이다.

## Day 2에서 이미 끝난 것 (재작업 불필요)

- ✅ `backend/Dockerfile` 멀티스테이지 (temurin 21-jdk 빌드 → 21-jre 런타임), 빌드 단계에서 테스트 수행
- ✅ Jenkins 파이프라인이 실제 백엔드를 빌드/배포 (더미 단계는 아예 거치지 않음)
- ✅ `depends_on: service_healthy`로 mysql/redis 기동 후 backend 시작
- ✅ `restart: unless-stopped` 전 서비스 적용
- ✅ 컨테이너 HEALTHCHECK (`/api/health`)
- ✅ nginx `/api/` → 실제 백엔드 연결 확인 (외부 200)

## 체크리스트

### 1. 시크릿 목록 (2026-09-07 갱신 — 신규 항목 ★)

| 시크릿 | 용도 | 발급처 | 현재 상태 | 보관 방식 |
| --- | --- | --- | --- | --- |
| MySQL 계정 4종 | DB 접속 | 인프라 자체 생성 | ✅ 생성됨 | EC2 `infra/.env` (600) |
| Redis 비밀번호 | 캐시/세션 접속 | 인프라 자체 생성 | ✅ 생성됨 | EC2 `infra/.env` |
| JWT Secret | Auth 세션 | 인프라 자체 생성 | ✅ 생성됨(64자) | EC2 `infra/.env` |
| ★ **데이터 암호화 키** | 계좌번호·카드번호·FCM 토큰 등 `VARBINARY` 컬럼 암복호화 | 인프라 자체 생성 | ❌ **미생성 — 이번 Day에 신설** | EC2 `.env` + **별도 안전한 곳에 백업본** |
| ★ GitLab PAT | Jenkins 소스 체크아웃, EC2 git pull | GitLab Access Tokens | ✅ 발급·등록됨 | Jenkins Credentials(`gitlab-https`, `ec2-jenkins-deploy`) + EC2 `~/.git-credentials` |
| ★ Webhook secret | GitLab → Jenkins 웹훅 인증 | 인프라 자체 생성 | ✅ 생성됨 | Jenkins job config + EC2 `~/.jenkins-webhook-secret` |
| ★ Jenkins admin 계정 | Jenkins 로그인 | setup wizard | ✅ 생성됨 | 개인 보관 (**HTTPS 전까지 평문 전송됨 — Day 3 참고**) |
| OpenAI API Key | AI 코치 | OpenAI 콘솔 | ❌ 미발급 | `.env` |
| SSAFY 금융망 키 | 금융 데이터 연동 | SSAFY 금융망 포털 | ❌ 미발급 | `.env` |
| Firebase(FCM) 서비스 계정 키 | 푸시 알림 | Firebase 콘솔 | ❌ 미발급 | **파일 볼륨 마운트** (Git 커밋 금지) |
| Kakao/Google OAuth | 소셜 로그인 | 각 개발자 콘솔 | ❌ 미발급 | `.env` |

- [ ] **데이터 암호화 키 신설** — 갱신된 ERD가 `VARBINARY` 암호화 컬럼을 쓰므로 반드시 필요
  - 키 형식/길이(AES-256 기준 32바이트 등)를 백엔드팀과 합의 (**사용자 액션**) — 애플리케이션이 어떻게 읽을지(`.env` 변수명)까지 함께
  - **키 백업 절차 문서화**: 키를 잃으면 DB 백업이 있어도 복구 불가 (Day 4 백업 항목과 연결)
  - 키와 DB 덤프를 같은 위치에 두지 않기
- [ ] 위 표를 실제 값 없이 팀 문서(Notion 등)에 옮기고 발급 담당자 지정 (**사용자 액션**)
- [ ] `.env.example`에 신규 키 이름 추가

### 2. `.env` 관리 방식 결정
현재 `.env`는 **EC2에 수동으로만 존재**한다. 레포에는 없고 백업도 없다.
- [ ] 이 방식을 유지할지, Jenkins Credentials에서 배포 시 생성하도록 바꿀지 결정
  - 유지 → 단순하지만 **EC2가 날아가면 시크릿도 같이 날아감** → 최소한 오프라인 백업 필요
  - Jenkins 생성 → 복구력은 좋지만 Jenkins가 단일 실패점이 되고 설정이 복잡해짐
- [ ] 어느 쪽이든 **`.env` 백업 절차**는 반드시 만들 것 (Day 4의 DB 백업과 함께)

### 3. 배포 안정화
- [ ] **JVM 메모리 옵션** — 현재 제한 없음. 16GB 중 MySQL/Redis/Jenkins/모니터링 몫을 남기고 `-Xmx` 지정 (예: 2~3GB)
      Day 6 모니터링 스택이 올라오면 메모리가 빠듯해질 수 있으므로 이번 Day에 미리 잡아둘 것
- [ ] **배포 실패 시 롤백** — 현재는 헬스체크 실패 시 로그만 남기고 끝나며, **깨진 컨테이너가 그대로 떠 있는다**
      직전 이미지 태그를 남겨뒀다가 실패 시 되돌리는 방식 검토 (`infra-backend:previous` 태깅 등)
- [ ] **헬스체크 엔드포인트 일원화** — 지금 컨테이너 HEALTHCHECK와 `deploy.sh`는 `/api/health`를 쓰고, `/actuator/health`도 별도로 열려 있다. 모니터링(Day 6)은 actuator를 쓸 예정이므로 역할을 정리해 문서화
- [ ] 컨테이너 리소스 제한(`mem_limit`) 검토 — 한 서비스가 폭주해도 다른 서비스를 굶기지 않도록

### 4. 검증
- [ ] 컨테이너 강제 종료(`docker kill backend`) → `restart` 정책으로 자동 복구되는지
- [ ] 일부러 테스트를 깨뜨린 커밋을 push → **빌드에서 멈추고 배포되지 않는지** (Day 2에서 구조는 만들었으나 실제로 확인한 적 없음)
- [ ] EC2 재부팅 후 전체 스택이 자동으로 올라오는지 (`restart: unless-stopped` 검증)

## 산출물
- 시크릿 목록/보관/백업 방식 문서화 (실제 값 제외)
- 데이터 암호화 키 생성 및 백엔드 연동
- 배포 실패가 서비스 중단으로 이어지지 않는 구조

## 리스크
- 암호화 키를 나중에 바꾸면 이미 저장된 데이터를 복호화할 수 없다 → **실데이터가 쌓이기 전에 확정**해야 함. FE 연동이 Day 4에 시작되므로 시간이 촉박함
