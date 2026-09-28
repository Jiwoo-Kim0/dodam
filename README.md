# 도담 (Dodam)

> 목표를 향해 한 걸음 더 — 청년을 위한 AI 금융 코치

도담은 사용자의 자산과 소비 흐름을 분석하고, 목표 달성을 방해하는 금융 비효율을 발견해 다음 행동을 제안하는 청년 금융 코치 앱입니다.
SSAFY 금융망의 금융 데이터를 바탕으로 **보여 주고 → 찾아내고 → 상담하고 → 행동하는** 흐름을 한 앱에서 제공합니다.

## 주요 기능

| 기능 | 내용 |
| --- | --- |
| 홈과 목표 | 목표 중심 홈, 모으기·줄이기 목표, 예상 달성일, 데일리 브리핑 |
| 세이프박스 | 도담 전용 계좌 하나에 목표별 금액을 나눠 담고 꺼냄. 금융망 실제 이체 |
| 가계부와 자산 | 달력형 거래 내역, 카테고리 소비 분석, 계좌·카드·대출·예적금 조회와 이체 |
| 리포트 | 금융 효율 점수, K-Means 소비 유형과 또래 비교, 시계열 소비 예측과 근거 |
| 발견 | 소비 증가·고금리 대출·목표 지연 진단, 대환·상품 추천, 시뮬레이션 |
| 토리 (AI 코치) | 서버가 계산한 진단·추천·예측 수치로만 답하는 금융 상담 |
| 알림 | 결제·상환 예정, 현금흐름 위험, 목표, 리포트 푸시 알림 |

## 시스템 구성

```text
Android 앱 (Expo / React Native)
        │  HTTPS
        ▼
Nginx (TLS, Let's Encrypt) ──▶ Spring Boot API ──▶ MySQL 8.0
                                   │          └──▶ Redis 7
                                   ├──▶ SSAFY 금융망
                                   ├──▶ SSAFY GMS (OpenAI 중계)
                                   ├──▶ Google·Kakao 토큰 검증
                                   └──▶ Firebase Cloud Messaging

EC2 1대 · Docker Compose
GitLab ──webhook──▶ Jenkins ──▶ 빌드·테스트·배포·헬스체크
Prometheus · Grafana · Loki ──▶ 지표·로그 수집, Mattermost 알림
```

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Frontend | Expo SDK 57, React Native 0.86, React 19, TypeScript 6, Expo Router, EAS Build |
| Backend | Java 21, Spring Boot 3.5.16, Spring Security, Spring Data JPA, Flyway, JJWT, Firebase Admin |
| Data | MySQL 8.0, Redis 7 |
| AI / ML | SSAFY GMS(`gpt-5.4-mini`) 코치, scikit-learn K-Means 소비 군집, 시계열 소비 예측 |
| Infra | AWS EC2, Docker Compose, Nginx 1.27, Let's Encrypt, Jenkins |
| Monitoring | Prometheus, Grafana, Loki, Promtail, cAdvisor, Mattermost Webhook |

## 저장소 구조

```text
S15P21D105/
├─ frontend/        Android 앱 (Expo)                   → frontend/README.md
├─ backend/         Spring Boot API 서버                → backend/README.md
│  ├─ docs/         도메인별 구현 기준 문서
│  └─ database/     시연용 seed SQL
├─ infra/           Docker Compose, Nginx, Jenkins, 모니터링, 운영 스크립트 → infra/README.md
├─ docs/            기획안, API 명세서, ERD
├─ exec/            산출물 (포팅 매뉴얼, 빌드 가이드, 외부 서비스 정보, DB 덤프, 시연 시나리오)
├─ qa_report/       QA 기록
└─ Jenkinsfile      배포 파이프라인
```

## 시작하기

영역별 상세 절차는 각 README를 따릅니다.

| 영역 | 요약 | 문서 |
| --- | --- | --- |
| Backend | JDK 21, MySQL 8.0, Redis 준비 → 환경변수 설정 → `./gradlew bootRun --args='--spring.profiles.active=local'` | [backend/README.md](backend/README.md) |
| Frontend | `npm ci` → `.env` 설정 → `npm start`, 시연용 APK는 `eas build --profile preview` | [frontend/README.md](frontend/README.md) |
| Infra | EC2에서 `infra/.env` 작성 → `docker compose up -d`. `develop` 병합 시 Jenkins 자동 배포 | [infra/README.md](infra/README.md) |

- 서비스 주소: `https://j15D105.p.ssafy.io/api` (헬스체크 `/api/health`)
- DB는 [exec/dodam_dump.sql.gz](exec/dodam_dump.sql.gz)로 마이그레이션과 시연 데이터가 적재된 상태에서 시작할 수 있습니다.
- 실제 키와 비밀번호는 저장소에 두지 않습니다. 키 이름은 `infra/.env.example`, `frontend/.env.example`에 있습니다.

## 산출물 (exec)

| 파일 | 내용 |
| --- | --- |
| [exec/포팅매뉴얼.md](exec/포팅매뉴얼.md) | 클론 이후 빌드·배포 절차, 제품 버전과 설정값, 환경 변수, 배포 특이사항, 주요 계정 |
| [exec/안드로이드빌드가이드.md](exec/안드로이드빌드가이드.md) | 안드로이드 APK 빌드·설치 절차 (EAS, 로컬) |
| [exec/외부서비스.md](exec/외부서비스.md) | 외부 서비스 가입·발급 절차와 설정 키 |
| [exec/dodam_dump.sql.gz](exec/dodam_dump.sql.gz) | DB 덤프 (MySQL 8.0, 합성 시연 데이터) |
| [exec/시연시나리오.md](exec/시연시나리오.md) | 화면별·동작별 시연 순서 |

## 문서

| 문서 | 위치 |
| --- | --- |
| 기획안 | [docs/기획안/](docs/기획안/) |
| API 명세서 | [docs/API명세서/](docs/API명세서/) |
| ERD | [docs/ERD/](docs/ERD/) |
| 백엔드 구현 문서 | [backend/docs/](backend/docs/) |
| 인프라 문서 | [infra/docs/](infra/docs/) |
| QA 기록 | [qa_report/](qa_report/) |

---

## 협업 규칙

### MR 메시지

#### MR 제목 규칙

MR 제목은 **담당 영역 + 작업 유형 + 작업 내용**을 기준으로 작성합니다.

##### 기본 형식

```text
[영역] 작업 유형: 작업 내용
```

예시

- `[FE] Chore: 프론트엔드 초기 환경 및 구조 설정`
- `[BE] Feature: 결과물 포팅 메뉴얼 추가`
- `[FE] Fix: 프로젝트 삭제 시 화면이 갱신되지 않는 문제 수정`
- `[BE] Refactor: 프로젝트 조회 로직 개선`

Jira Issue와 연결된 작업의 경우 제목 앞에 **Issue Key**를 추가합니다.

##### Jira Issue가 있는 경우

```text
[Issue Key] [영역] 작업 유형: 작업 내용
```

예시

- `[S15P11D205-94] [FE] Feature: 프로젝트 설정 및 삭제·나가기 API 연동`
- `[S15P11D205-103] [BE] Fix: 프로젝트 삭제 API 예외 처리 수정`

##### 영역

- `[FE]`: Frontend
- `[BE]`: Backend
- `[AI]`: AI
- `[Infra]`: Infra
- 필요한 경우 프로젝트에서 사용하는 영역을 추가합니다.

##### 작업 유형

| 유형 | 설명 |
| --- | --- |
| `Feature` | 새로운 기능 추가 |
| `Fix` | 버그 및 오류 수정 |
| `Chore` | 환경 설정, 패키지 관리 등 기능과 직접적인 관련이 없는 작업 |
| `Refactor` | 기능 변경 없이 코드 구조 개선 |
| `Docs` | 문서 작성 및 수정 |
| `Test` | 테스트 코드 추가 및 수정 |

##### 작성 기준

- 제목만 보고 **어느 영역에서 어떤 작업을 했는지 파악할 수 있도록** 작성합니다.
- `수정`, `작업`, `개발`과 같이 범위가 불명확한 표현만 단독으로 사용하지 않습니다.
- 여러 작업이 포함되어 있더라도 MR의 핵심 변경 사항을 기준으로 제목을 작성합니다.
- Jira Issue가 존재하는 경우 Issue Key를 제목 가장 앞에 작성합니다.

##### 권장 형태

- ✅ `[FE] Feature: 프로젝트 생성 API 연동`
- ✅ `[BE] Fix: 프로젝트 삭제 권한 검증 오류 수정`
- ✅ `[S15P11D205-94] [FE] Feature: 프로젝트 설정 및 삭제·나가기 API 연동`

##### 지양하는 형태

- ❌ `프로젝트 수정`
- ❌ `[FE] 작업`
- ❌ `Feature 개발`
- ❌ `[FE] Feature: 수정`

### 커밋 규칙

커밋 메시지는 `<type>: <작업 내용>` 형식으로 작성합니다.

```text
feat: 월급 내역 조회 기능 구현
fix: 로그인 리다이렉트 오류 수정
```

| 타입 | 사용 기준 | 예시 |
| --- | --- | --- |
| `feat` | 새로운 기능 추가 | 월급 내역 조회 기능 구현 |
| `fix` | 버그 수정 | 로그인 리다이렉트 오류 수정 |
| `refactor` | 기능 변화 없는 코드 개선 | API 호출 로직 분리 |
| `style` | 코드 동작과 무관한 스타일 및 포맷 변경 | 들여쓰기 및 코드 포맷 정리 |
| `design` | UI 디자인 변경 | 학생 대시보드 레이아웃 변경 |
| `docs` | 문서 작성 및 수정 | API 명세서 업데이트 |
| `test` | 테스트 코드 추가 및 수정 | 거래 생성 테스트 추가 |
| `chore` | 설정, 패키지 등 기타 작업 | ESLint 설정 추가 |
| `perf` | 성능 개선 | 거래 내역 조회 성능 개선 |
| `build` | 빌드 및 의존성 변경 | React Query 패키지 추가 |
| `ci` | CI/CD 설정 변경 | GitLab CI 파이프라인 수정 |
| `revert` | 이전 커밋 되돌리기 | 로그인 로직 변경 사항 복구 |

### 브랜치명 규칙

- 모든 작업은 Jira 이슈를 생성한 후 시작합니다.
- 브랜치는 Jira 이슈의 **Create branch** 기능을 통해 생성합니다.
- Jira에서 자동 생성된 브랜치명을 기본으로 사용합니다.
- 브랜치명에는 반드시 Jira 이슈 키가 포함되어야 합니다.
- 브랜치를 생성할 때 기준 브랜치가 `develop`인지 확인합니다.
- 작업 완료 및 병합 후 브랜치는 삭제합니다.

#### 브랜치명 형식

```text
JIRA-이슈키-간단한-작업명
```

작업명은 영문 소문자로 작성하고, 단어 사이는 하이픈(`-`)으로 구분합니다.

```text
main
└── develop
    ├── S15P21D105-101-student-asset
    ├── S15P21D105-102-login
    ├── S15P21D105-103-token-refresh
    ├── S15P21D105-104-dashboard
    └── S15P21D105-105-auth-service
```
