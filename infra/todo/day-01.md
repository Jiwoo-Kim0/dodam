# Day 1 — 서버 기초 설정 & 리포 구조 (P0)

## 목표
EC2에 Docker 기반 운영 환경을 세팅하고, 모노레포에 `infra/` 표준 구조를 만든다.

> **완료 상태 (2026-09-18 대조)** — 실제 진행 기록은 [`jiw/day-01.md`](jiw/day-01.md).
> 체크박스가 계속 비어 있어 2026-09-18에 jiw 로그와 서버 실측으로 대조해 채웠다.
> 각 항목 뒤 `—` 뒤가 근거다.

## 체크리스트

### 1. 서버 실측 (🔲 확인 필요 항목 채우기)
- [x] SSH 접속 확인 (MobaXterm, `ubuntu` 계정) — jiw/day-01 `SSH 접속 트러블슈팅` 이후 정상 사용 중
- [x] `nproc`, `free -h`, `df -h`로 CPU/RAM/Disk 확인 → `00-overview.md` 표에 결과 기록 — jiw/day-01 ①. 4vCPU/16GB/307GB
- [x] `sudo ss -tlnp` 로 현재 내부에서 열려 있는 포트 확인 — jiw/day-01 ①
- [x] 로컬 PC에서 `curl -v http://<EC2_IP>` , `curl -v https://<EC2_IP>` 로 80/443이 외부에서 실제로 열려있는지 확인 — 80은 Day 1에 확인(refused=SG 개방), 443은 Day 3 `443 개방 여부 확인`에서 확인
- [ ] SSAFY가 공지한 EC2 인바운드 정책 문서/공지 있는지 확인 (없으면 조교에게 문의 — **사용자 액션 필요**)
      → **미확인인 채로 남았다.** 공지를 찾는 대신 실측으로 80/443 개방을 확인하고 진행했다 (jiw/day-01 ①, Day 3 443 확인)

### 2. OS 기본 세팅
- [x] `sudo apt update && sudo apt upgrade -y` — jiw/day-01 ②
- [x] 타임존 `Asia/Seoul` 설정, `timedatectl` 확인 (로그/스케줄 시간 꼬임 방지) — jiw/day-01. 2026-09-18 재확인: `Asia/Seoul (KST, +0900)`
- [x] ~~스왑 파일 추가 (RAM이 4GB 이하일 경우)~~ — **해당 없음.** 조건이 'RAM 4GB 이하'인데 실측 15Gi (2026-09-18 재확인)
- [x] `ufw` 설치 여부만 확인 (보안그룹을 못 바꾸는 상황이면 ufw로 로컬 방화벽까지 관리할지는 실측 후 결정) — jiw/day-01 `ufw 80/443 허용`. 2026-09-18 재확인: active
- [x] 불필요한 sudo 없이 docker 명령 쓸 수 있도록 `ubuntu`를 `docker` 그룹에 추가 (Day 1-3에서 진행) — 2026-09-18 재확인: `ubuntu`가 docker 그룹에 속함

### 3. Docker 설치
- [x] Docker Engine 설치 (공식 스크립트 or apt repo) — jiw/day-01 ③
- [x] Docker Compose plugin 설치 (`docker compose version` 확인) — jiw/day-01 ③
- [x] `sudo usermod -aG docker ubuntu` 후 재로그인, `docker run hello-world` 확인 — jiw/day-01 ③. `hello-world` 확인 완료
- [x] Docker 로그 로테이션 설정 (`/etc/docker/daemon.json`에 `log-opts max-size` 지정) — 디스크 꽉 차는 사고 예방 — 2026-09-18 재확인: `/etc/docker/daemon.json` max-size 10m, max-file 3

### 4. 모노레포 infra 구조 세팅
- [x] `infra/` 아래 `docker-compose.yml`, `.env.example`, `nginx/`, `mysql/init/`, `scripts/` 폴더 생성 (구조는 `00-overview.md` 참고) — jiw/day-01 `로컬 레포 스켈레톤 생성`
- [x] `.gitignore`에 `infra/.env`, `*.pem`, `*.key` 등 시크릿 패턴 추가 — 2026-09-18 재확인: `infra/.env`, `*.pem`, `*.key` 등 포함
- [x] GitLab 브랜치 전략 확정 및 문서화: `main`(배포) / `develop`(통합) / `feature/*` — 백엔드/프론트 팀원과 합의 필요 (**사용자 액션: 팀 컨벤션 채널에 공유**) — jiw/day-02 `브랜치 전략 정리 및 EC2 체크아웃 정정`
- [x] `infra/README.md`에 "로컬에서 어떻게 이 스택을 띄우는지" 최소 안내 작성 (지금은 골격만, 이후 Day마다 갱신) — jiw/day-03 `문서 갱신`에서 전면 개편

## 산출물
- EC2 스펙/포트 실측 결과가 `00-overview.md`에 채워짐
- Docker/Compose 설치 완료
- `infra/` 폴더 골격 커밋

## 다음 Day로 넘길 것
- 도메인 확보 여부는 여전히 미정이면 Day 2 시작 전에 재확인
