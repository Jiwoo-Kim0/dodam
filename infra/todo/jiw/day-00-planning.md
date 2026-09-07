# Day 0 — 계획 수립 기록

## 2026-09-03

### docs 파악
- `docs/기획안/`, `docs/ERD/`, `docs/API명세서/` 전체 구조 및 핵심 문서 3개(기획안 작성.md, ERD 작성.md, API 명세서 작성.md) 읽음
- 파악한 내용: 서비스명 "도담"(목표 기반 금융코치), 사회초년생 대표 페르소나, 금융 데이터 통합→비효율 탐지→개선안 추천→실행 추적의 서비스 흐름, ERD 25개+ 엔티티(User/Account/Transaction/Loan/DepositSaving/Goal/Diagnosis/Recommendation/Simulation/Product 등), API 22개 도메인
- 이 시점 레포 상태: `docs/`만 존재, 코드 없음, `README.md` 비어있음

### 아키텍처 이미지 파악
- 사용자가 첨부한 아키텍처 이미지 분석: EC2 1대 안에 Docker로 MySQL, Spring Boot, Redis, Nginx(리버스 프록시), CI/CD, Prometheus, Grafana, Loki 구동. FE는 React Native+Expo(EC2 밖), FCM은 Firebase, 소셜로그인 Kakao/Google, 외부 연동 OpenAI + SSAFY 교육용 금융망

### 요청
- "docs 파악 후 infra/todo 폴더 만들어서 8일 인프라 계획을 구체적으로 짜달라"
- 기술스택은 아키텍처 이미지 참고
- 질문 있으면 하라고 함

### 클리어링 질문 (AskUserQuestion)
1. EC2는 몇 대/사양? → **답: 1대, 웹 콘솔 없음, SSH(MobaXterm)만 가능, `ubuntu` 계정**
2. 코드 저장소 구조? → **답: 모노레포**
3. CI/CD 트리거 방식(Git 호스팅)? → **답: GitLab (SSAFY Lab)**
4. 8일 작업 혼자 vs 팀 분담? → **답: 혼자 진행**

### 산출물 — infra/todo/ 8일 계획 최초 작성
- `00-overview.md`: 전제조건, 미확인 항목 표, 우선순위(P0/P1/P2), CI/CD 도구 메모, 디렉터리 구조안, 파일 목록
- `day-01.md` 서버 기초 & Docker & 레포 구조
- `day-02.md`(당시) Nginx 리버스 프록시 & 도메인/SSL
- `day-03.md`(당시) MySQL & Redis
- `day-04.md`(당시) 백엔드 컨테이너화 & 시크릿 관리
- `day-05.md`(당시) CI/CD 파이프라인 (GitLab CI+Runner를 1순위, Jenkins는 대안으로 최초 작성)
- `day-06.md` 모니터링 스택 (Prometheus/Grafana/Loki)
- `day-07.md` 외부 연동(FCM/소셜로그인/OpenAI/금융망) & FE 배포
- `day-08.md` 통합 리허설, 장애 대응, 문서화

---

## 2026-09-03 (계획 수정 #1 — CI/CD 도구)

### 피드백
- "전에 다른 인프라 담당 친구가 jenkins 쓴적 있어" → SSAFY Lab(GitLab) + Jenkins 조합이 실제로 동작했던 전례가 있음을 알려줌

### 반영
- `00-overview.md` 3번 섹션: GitLab CI+Runner 1순위 → **Jenkins 1순위, GitLab CI+Runner는 대안**으로 변경 (아키텍처 이미지와도 일치)
- `day-05.md`(당시): Jenkins를 1차 시도로, GitLab CI+Runner를 2차 옵션으로 재작성. "이전 팀원에게 당시 Webhook/플러그인 설정법 확인" 액션 아이템 추가

---

## 2026-09-03 (계획 수정 #2 — CI/CD 시점 이동)

### 피드백
- "CI/CD를 하기 위해선 day1~4가 다 prerequisite인거야? 다른 인프라 친구 조언으로는, 다들 개발 많이 들어가기 전에 CI/CD를 빨리 설정해두는 게 나중에 안 기다려도 돼서 편하다더라. Day5면 이미 다들 개발 많이 했을 것 같다"

### 판단 및 반영
- 실제 하드 prerequisite는 Day1(서버+Docker+레포 구조)뿐이고, Nginx/DB/백엔드 Dockerfile은 "완성된 배포"의 조건이지 "파이프라인 존재"의 조건은 아니라고 판단
- 더미(hello-world) 이미지로 먼저 webhook→build→deploy 배관을 검증하는 방식으로 CI/CD를 **Day 5 → Day 2로 이동**
- 파일 전체 재구성:
  - 신규 `day-02.md`: CI/CD 조기 구축 (더미 앱, Jenkins)
  - 신규 `day-03.md`: Nginx 리버스 프록시 정식화 & 도메인/SSL (구 day-02 내용을 확장, Day2에서 만든 최소 Jenkins 라우팅을 이어받음)
  - 신규 `day-04.md`: MySQL & Redis (구 day-03 내용, 큰 변경 없음)
  - 신규 `day-05.md`: 백엔드 컨테이너화 & 시크릿 관리 (구 day-04 내용 + "더미→실제 앱 교체" 단계 추가)
  - `day-06~08.md`: 변경 없음
- `00-overview.md`: 우선순위 섹션에 "CI/CD 파이프라인 존재(더미로라도)"를 P0에 포함, "CI/CD를 Day 2로 앞당긴 이유" 섹션 추가, 파일 목록/상호참조(Day 5 → Day 2) 갱신

---

## 2026-09-04

### 질문
- "지금 git branch 어디야?"

### 확인
- `git branch --show-current` → **`infra`** 브랜치
- 원격 브랜치 확인: `origin/main`, `origin/develop`, `origin/infra` 존재. 로컬에는 `infra`, `main`, `checkout` 브랜치 존재
- 커밋 로그: `1d51cf4 initial commit` → `02a9005 infra todo` (infra/todo 8일 계획 커밋된 상태)

### 요청
- "이제 Day 1부터 시작할거야. 내가 해야할 게 뭐가 있을까?"

### 대응
- Day1을 "EC2에서 직접 해야 하는 것(SSH 필요)" vs "로컬 레포에서 Claude가 지금 만들 수 있는 것"으로 분리해서 설명
- 진행 방식 질문(AskUserQuestion): Claude가 SSH로 직접 붙어서 진행 vs 명령어를 주면 사용자가 직접 입력 → **답: 명령어를 드리면 직접 입력**
- (→ 상세 진행 기록은 `day-01.md` 참고)
