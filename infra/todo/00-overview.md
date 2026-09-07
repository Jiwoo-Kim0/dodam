# 인프라 8일 로드맵 - 개요

담당: 김지우 (혼자 진행)
기준일: 2026-09-03 ~ 2026-09-10 (8일)

## 0. 전제 조건 / 확정된 사실

- **서버**: EC2 1대. AWS 웹 콘솔 접근 불가, SSH(MobaXterm)만 가능. `ubuntu` 계정 사용.
  - 보안그룹(인바운드 포트) 수정 권한이 없다고 가정한다. 즉 **기본 개방 포트(22, 80, 443 추정) 밖으로 새 포트를 열 수 없을 가능성이 높다.**
  - Day 1에 서버 안에서 열려 있는 포트를 확인하고, 외부에서 실제로 접근되는 포트를 검증한다. 안 되면 SSAFY 담당 교육장/운영 조교에게 포트 개방을 요청해야 한다 (사용자 액션 필요 항목으로 별도 표기).
- **리포지토리**: 모노레포 (`S15P21D105` 하나에 FE/BE/infra 전부).
- **Git 호스팅**: GitLab (SSAFY Lab, `lab.ssafy.com`).
- **아키텍처**(첨부 이미지 기준): EC2 안에 Docker로 MySQL, Spring Boot, Redis, Nginx(리버스 프록시), CI/CD, Prometheus, Grafana, Loki를 전부 구동. FE는 React Native+Expo(EC2 밖, 별도 빌드/배포), FCM은 Firebase, 소셜로그인은 Kakao/Google, 외부 연동은 OpenAI와 SSAFY 교육용 금융망.

## 1. 아직 확인되지 않은 것 (진행하면서 반드시 채워야 할 빈칸)

이 항목들은 계획에 영향이 크므로, 각 Day 문서에 "🔲 확인 필요"로 표시해뒀다. 확인되는 대로 해당 Day 문서를 갱신할 것.

| 항목 | 왜 중요한가 | 잠정 대응 |
| --- | --- | --- |
| ~~EC2 사양(vCPU/RAM/Disk)~~ | ~~메모리가 빠듯할 가능성~~ | **확인 완료 (2026-09-06)**: 4vCPU / RAM 16GB(가용 14GB) / Disk 307GB 여유. 여유 충분 → 스왑 불필요, 모니터링 스택 축소 없이 진행 가능. 상세: [[jiw/day-01.md]] |
| 실제로 열려있는 외부 포트 | 웹 콘솔이 없어 보안그룹을 못 바꿈 | Day 1에 확인, 80/443 외에는 전부 Nginx 서브패스+Basic Auth로 우회 노출 |
| ~~도메인 유무~~ | ~~HTTPS, 소셜로그인 리다이렉트 URI, 앱 API BaseURL에 필요~~ | **확인 완료 (2026-09-06)**: 도메인 있음 — `j15D105.p.ssafy.io` (→ `43.203.198.18`, SSAFY 측에서 이미 연결해줌). Day 3 SSL 발급(Let's Encrypt) 이 도메인으로 진행 가능. 상세: [[jiw/day-01.md]] |
| GitLab Runner를 EC2에 등록 가능한지 (네트워크 정책상) | CI/CD 방식(Jenkins vs GitLab CI) 결정 | Day 2에 Jenkins 먼저 시도, 막히면 GitLab CI+Runner로 전환 (아래 3번 참고) |
| SSAFY 교육용 금융망이 아웃바운드 IP 화이트리스트를 요구하는지 | 요구하면 EC2 고정 IP를 금융망 쪽에 등록해야 함 | Day 7에 백엔드팀·API 문서로 확인 |
| 백엔드 코드/Dockerfile 존재 여부 | 아직 코드가 없음(레포에 docs만 있음) | 인프라는 "표준 Dockerfile 템플릿 + docker-compose"를 먼저 제공하고, 팀 코드가 붙는 대로 연결 |

## 2. 우선순위 (8일 안에 다 못 끝날 경우 이 순서로 쳐낸다)

- **P0 (없으면 서비스 자체가 안 됨)**: 서버 기초 설정, Docker/Compose, CI/CD 파이프라인 존재(더미로라도), MySQL, Redis, Nginx 리버스 프록시, 백엔드 배포, 시크릿 관리
- **P1 (안정적 운영에 필요)**: HTTPS, DB 백업, 컨테이너 재시작 정책, 헬스체크
- **P2 (있으면 좋음, 팀 발표/운영 품질용)**: Prometheus/Grafana/Loki 모니터링 스택, 로그 대시보드, 알림 연동

일정이 밀리면 **Day 6(모니터링)을 가장 먼저 축소**하고, 최소 컨테이너 리소스/디스크 사용량 알림 정도만 남긴다.

### CI/CD를 Day 2로 앞당긴 이유

원래 CI/CD는 인프라가 다 갖춰진 뒤(구 Day 5)에 붙이는 순서였지만, **팀원들이 코드를 push하기 시작하기 전에 파이프라인 배관(webhook→build→deploy) 자체를 먼저 검증**해두면, 이후 실제 코드가 들어올 때 대기/디버깅 시간이 줄어든다는 조언에 따라 Day 2로 이동했다. Day 2에서는 더미 앱으로 파이프라인만 검증하고, Day 5(구 Day 4)에서 실제 백엔드로 교체한다. 상세 근거는 각 Day 문서의 "왜 이 시점에 가능한가" 참고.

## 3. CI/CD 도구에 대한 메모

첨부 아키텍처 이미지에도 Jenkins 아이콘이 있고, 이전 다른 프로젝트에서 인프라 담당자가 GitLab(SSAFY Lab) + Jenkins 조합을 실제로 써본 전례가 있다고 확인됨 → 웹훅/네트워크 이슈로 막힐 리스크가 낮다고 판단, **Jenkins를 1순위로 진행**한다.

1. **Jenkins(Docker) + GitLab Webhook** — 아키텍처 이미지와 일치, 전례 있음. **1순위로 진행.**
2. **GitLab CI + 자체 GitLab Runner(EC2에 설치)** — Jenkins가 이번 EC2/네트워크 환경에서 예상외로 막히면(웹훅 미수신, 리소스 부족 등) 대안으로 전환.

아키텍처 이미지의 "CI/CD" 박스는 Jenkins로 확정. 실제 진행 중 문제가 생기면 이 섹션과 Day 2 문서를 갱신한다.

## 4. 디렉터리 구조(제안)

```
infra/
  todo/                # 이 계획 문서들
  docker-compose.yml   # 전체 스택 (또는 base + override)
  docker-compose.prod.yml
  nginx/
    conf.d/
  mysql/
    init/
  monitoring/
    prometheus/
    grafana/
    loki/
  .env.example
  scripts/
    deploy.sh
    backup.sh
```

## 5. 파일 목록

- `jiw/` — **실제 작업 로그**. Claude와 김지우가 각각 무엇을 했는지 매 작업마다 시간순으로 기록 (이 폴더의 파일들은 "계획", `jiw/`는 "실제로 한 일")
- `day-01.md` 서버 기초 설정 & 리포 구조
- `day-02.md` CI/CD 파이프라인 조기 구축 (더미 앱, Jenkins)
- `day-03.md` Nginx 리버스 프록시 정식화 & 도메인/SSL
- `day-04.md` MySQL & Redis
- `day-05.md` 백엔드 컨테이너화 & 시크릿 관리 (파이프라인을 실제 앱으로 교체)
- `day-06.md` 모니터링 스택 (Prometheus/Grafana/Loki)
- `day-07.md` 외부 연동(FCM/소셜로그인/OpenAI/금융망) & FE 배포 파이프라인
- `day-08.md` 통합 리허설, 장애 대응, 문서화
