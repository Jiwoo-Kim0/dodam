# Day 5 — 백엔드 컨테이너화 & 시크릿 관리 (P0)

Day 2에서 만든 Jenkins 파이프라인은 지금까지 더미 이미지를 빌드/배포해왔다. 이 Day의 핵심은 **더미를 실제 Spring Boot 백엔드로 교체**하고, MySQL/Redis(Day 4)에 실제로 연결하는 것이다. 파이프라인 배관 자체는 이미 검증되어 있으므로 여기서는 "무엇을 빌드/배포할지"만 바꾼다.

## 체크리스트

### 1. 백엔드 Dockerfile
- [ ] Spring Boot 기준 multi-stage Dockerfile 작성 (build stage: Gradle/Maven → run stage: JRE 슬림 이미지)
- [ ] 백엔드 레포 경로(모노레포 내 `backend/` 등, 실제 경로는 백엔드팀과 확정 — **사용자 액션**)에 배치, Day 2의 더미 앱 자리를 대체
- [ ] JVM 메모리 옵션(`-Xmx`)을 EC2 실측 RAM에 맞게 제한 (Day 1 실측값 기준, 다른 컨테이너 몫 남겨두기)

### 2. Jenkins 파이프라인 교체
- [ ] `Jenkinsfile`의 build 스테이지가 더미 대신 실제 백엔드 경로를 빌드하도록 수정
- [ ] `docker-compose.yml`의 `backend` 서비스가 `mysql`, `redis` 뒤에 뜨도록 `depends_on` + healthcheck 설정
- [ ] 컨테이너 재시작 정책 `restart: unless-stopped` (또는 `always`)
- [ ] Spring Boot Actuator `/actuator/health` 엔드포인트를 healthcheck에서 사용하기로 백엔드팀에 요청 (Day 6 모니터링과도 연결됨)
- [ ] Day 3에서 만든 Nginx `/api/` 프록시가 이제 실제 백엔드로 연결되는지 확인

### 3. 시크릿/환경변수 관리
아키텍처상 필요한 시크릿 목록을 정리하고 각각 어디서 발급받는지, 누가 갖고 있는지 표로 관리한다.

| 시크릿 | 용도 | 발급처 | 보관 방식 |
| --- | --- | --- | --- |
| DB 계정 | MySQL 접속 | 인프라(Day 4에서 생성) | `.env` (Git 미포함) |
| Redis 비밀번호 | 캐시 접속 | 인프라 | `.env` |
| OpenAI API Key | AI 코치 | OpenAI 콘솔 | `.env` / Jenkins Credentials |
| SSAFY 교육용 금융망 키(userKey 등) | 금융 데이터 연동 | SSAFY 금융망 포털 | `.env` / Jenkins Credentials |
| Firebase(FCM) 서비스 계정 키 | 푸시 알림 | Firebase 콘솔 | 파일로 보관, 컨테이너에 볼륨 마운트 (Git에는 절대 커밋 금지) |
| Kakao/Google OAuth Client ID/Secret | 소셜 로그인 | 각 개발자 콘솔 | `.env` / Jenkins Credentials |
| JWT Secret | Auth 세션 | 자체 생성 | `.env` |

- [ ] 위 표를 실제 값 없이 팀 문서(Notion 등)에 옮기고, 발급 담당자 지정 (**사용자 액션: 팀에 공유 후 담당 배정**)
- [ ] `.env.example`에 키 이름만 채운 템플릿 커밋
- [ ] Jenkins Credentials(또는 Credentials Binding 플러그인)에 실제 값 등록 → 배포 시 `.env`를 생성하도록 `Jenkinsfile`/`deploy.sh` 수정

### 4. 검증
- [ ] `feature` 브랜치에서 실제 백엔드 코드를 push → Jenkins가 감지 → 빌드 → MySQL/Redis에 연결된 상태로 기동되는지 확인
- [ ] 컨테이너 강제 종료 후 `restart` 정책으로 자동 복구되는지 확인
- [ ] 파이프라인 실패 시(빌드 에러 등) 배포가 진행되지 않는지 확인

## 산출물
- 실제 백엔드가 Jenkins 파이프라인으로 자동 빌드/배포됨 (Day 2의 더미는 완전히 대체)
- 시크릿 목록/보관 방식 문서화 (실제 값은 문서에 넣지 않음)

## 리스크
- 백엔드 코드가 이 시점까지 팀에서 준비되지 않았다면, 계속 더미로 파이프라인만 유지하고 이 Day의 작업을 뒤로 미룬다 — 인프라 진행이 백엔드 개발 속도에 종속되는 유일한 지점
