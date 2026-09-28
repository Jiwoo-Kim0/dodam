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
| Redis 비밀번호 | 캐시 접속 | 인프라 자체 생성 | ✅ 생성됨 | EC2 `infra/.env` |
| JWT Secret | Auth 세션 | 인프라 자체 생성 | ✅ 생성됨(62자) | EC2 `infra/.env` |
| **DATA_ENCRYPTION_KEY** | 계좌번호·카드번호·FCM 토큰 등 `VARBINARY` 컬럼 암복호화 (AES-256) | 인프라 자체 생성 | ✅ 생성됨(base64 32바이트) | EC2 `.env` + **서버 밖 암호화 백업** |
| **DATA_HASH_KEY** | 암호화 컬럼의 조회용 해시 (HMAC-SHA256) | 인프라 자체 생성 | ✅ 생성됨(base64 32바이트) | EC2 `.env` + **서버 밖 암호화 백업** |
| **NUMBER_HASH_KEY** | 계좌·카드번호 해시 (`app.security.number-hash-key`) | 인프라 자체 생성 | ✅ 생성됨(64자) | EC2 `.env` |
| GitLab PAT | Jenkins 소스 체크아웃, EC2 git pull | GitLab Access Tokens | ✅ 발급·등록됨 | Jenkins Credentials(`gitlab-https`, `ec2-jenkins-deploy`) + EC2 `~/.git-credentials`(600) |
| Webhook secret | GitLab → Jenkins 웹훅 인증 | 인프라 자체 생성 | ✅ 생성됨 (Day 3에 로테이션) | Jenkins job config + EC2 `~/.jenkins-webhook-secret`(600) |
| Jenkins admin 계정 | Jenkins 로그인 | setup wizard | ✅ 생성됨 (Day 3에 비밀번호 변경) | 개인 보관 |
| SSAFY 금융망 키 | 금융 데이터 연동 | SSAFY 금융망 포털 | ✅ 발급됨(32자) | EC2 `infra/.env` |
| OpenAI API Key | AI 코치 | OpenAI 콘솔 | ✅ 발급됨(47자) | EC2 `infra/.env` |
| Firebase(FCM) 서비스 계정 키 | 푸시 알림 | Firebase 콘솔 | ❌ 미발급 | **파일 볼륨 마운트** (Git 커밋 금지) |
| Kakao/Google OAuth | 소셜 로그인 | 각 개발자 콘솔 | ❌ 미발급 | `.env` |

> 상태는 2026-09-09 EC2 `.env` 실측 기준. 값은 출력하지 않고 설정 여부와 길이만 확인했다.
>
> **로테이션 성격이 다른 키가 있다.** `NUMBER_HASH_KEY`, `DATA_HASH_KEY`는 기존에 저장된 `*_hash` 값과 매칭에 쓰이므로 **바꾸면 기존 데이터를 찾을 수 없게 된다.** `DATA_ENCRYPTION_KEY`도 바꾸면 기존 암호문을 복호화할 수 없다. 즉 이 셋은 **유출되지 않는 한 고정**이며, 유출 시에는 키 교체와 함께 데이터 재암호화/재해시가 필요하다.

- [x] **데이터 암호화 키 신설** — Day 4에 생성 완료
  - 형식은 백엔드 구현에 맞춰 확정: `DATA_*`는 base64 32바이트, `NUMBER_HASH_KEY`는 문자열 그대로 사용(`getBytes(UTF_8)`)
  - **키 백업 절차** — `infra/scripts/backup-secrets.sh` (패스프레이즈 암호화 후 서버 밖 반출)
  - 키와 DB 덤프를 다른 디렉터리로 분리하고, 시크릿 백업은 **의도적으로 cron에 넣지 않음**
- [x] `.env.example`에 신규 키 이름 추가 — compose가 요구하는 12개 변수 전부 포함 확인 (2026-09-09)
- [ ] 위 표를 실제 값 없이 팀 문서(Notion 등)에 옮기고 발급 담당자 지정 (**사용자 액션**)

### 2. `.env` 관리 방식 결정
현재 `.env`는 **EC2에 수동으로만 존재**한다. 레포에는 없고 백업도 없다.
- [x] **결정: 현행(EC2 수동 보관) 유지** (2026-09-09)
  - Jenkins Credentials에서 생성하는 방식은 복구력은 좋지만, **docker.sock 접근권을 가진 Jenkins([[security.md]] R1의 최대 위험 요소)가 모든 시크릿까지 쥐게 되어 보안상 오히려 후퇴**한다고 판단
  - 단점(EC2 소실 시 시크릿 소실)은 아래 백업 절차로 보완
- [x] **`.env` 백업 절차** — `infra/scripts/backup-secrets.sh`
  - 패스프레이즈로 암호화(`openssl enc -aes-256-cbc -pbkdf2 -iter 200000`)한 단일 파일 생성
  - 출력 위치를 DB 덤프 디렉터리와 **의도적으로 분리**하고, **cron에 넣지 않음** — 자동화하면 같은 서버·같은 주기로 시크릿이 쌓여 한 번의 유출로 둘 다 털린다
  - 암호화 → 복호화 → 원본 일치 검증 완료

### 3. 컨테이너 하드닝 ★보안 (상세: [[security.md]] R3)
현재 `backend` 컨테이너가 **root(`uid=0`)로 실행 중**이다 (`docker exec backend id`로 확인).
- [x] `backend/Dockerfile` 런타임 스테이지에 비루트 유저 추가 (uid 10001, 검증 완료)
- [x] `security_opt: no-new-privileges:true` — 전 서비스 6개 적용
- [x] `cap_drop: ALL` — **backend에만** 적용. nginx(80/443 바인딩), mysql(파일 소유권)은 필요한 capability가 있어 제외
- [x] jenkins 하드닝 한계 문서화 — [[security.md]] R1의 "왜 DooD를 유지하는가"와 "의도적으로 하지 않는 것" 표에 근거 명시

### 4. 배포 승인 게이트 ★보안 (상세: [[security.md]] R4)
현재 `develop`에 push되면 그대로 프로덕션에 배포된다. 승인 단계가 없다.
- [ ] GitLab에서 `develop`을 **Protected branch**로 설정 (**사용자 액션**)
  - 직접 push 금지, MR 머지만 허용 → 최소 1인 리뷰가 배포 전 게이트가 됨
  - 팀이 이미 MR 흐름을 쓰고 있어 추가 부담이 거의 없음

### 5. 배포 안정화
- [x] **JVM 메모리 옵션** — `-XX:MaxHeapSize=1g -XX:InitialHeapSize=256m` 적용
- [x] **배포 실패 시 롤백** — 커밋+이미지 보존 후 실패 시 되돌림. 실제 동작 검증 완료
- [x] **헬스체크** — 일원화 대신 **역할 분리**로 결정: liveness는 `/api/health`, 배포 검증(readiness)은 `/actuator/health`
- [x] 컨테이너 리소스 제한(`mem_limit`) — 전 서비스 적용 (합계 약 7.5GB / 15GB)

### 6. 검증
- [x] `restart` 정책 자동 복구 — `docker kill`/컨테이너 내부 `kill -9 1`은 각각 "명시적 중단 취급", "PID 1의 SIGKILL 무시"로 검증에 부적합. 실증은 09-08 장애의 `RestartCount=604`
- [x] 테스트를 깨뜨린 상태로 빌드 → `80 tests completed, 1 failed`로 **빌드 실패, 배포 진행 안 됨** 확인
- [x] EC2 **실제 재부팅** → 26초 내 컨테이너 6개 자동 기동, 데이터·ufw·cron·하드닝 설정·HTTPS 전부 유지
- [x] 비루트 전환 후 정상 기동 확인 (`uid=10001(dodam)`)

## 산출물
- 시크릿 목록/보관/백업 방식 문서화 (실제 값 제외)
- 데이터 암호화 키 생성 및 백엔드 연동
- 배포 실패가 서비스 중단으로 이어지지 않는 구조

## 리스크
- 암호화 키를 나중에 바꾸면 이미 저장된 데이터를 복호화할 수 없다 → **실데이터가 쌓이기 전에 확정**해야 함. FE 연동이 Day 4에 시작되므로 시간이 촉박함
