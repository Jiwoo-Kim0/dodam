# Day 1 — 서버 기초 설정 & 리포 구조 (P0)

## 목표
EC2에 Docker 기반 운영 환경을 세팅하고, 모노레포에 `infra/` 표준 구조를 만든다.

## 체크리스트

### 1. 서버 실측 (🔲 확인 필요 항목 채우기)
- [ ] SSH 접속 확인 (MobaXterm, `ubuntu` 계정)
- [ ] `nproc`, `free -h`, `df -h`로 CPU/RAM/Disk 확인 → `00-overview.md` 표에 결과 기록
- [ ] `sudo ss -tlnp` 로 현재 내부에서 열려 있는 포트 확인
- [ ] 로컬 PC에서 `curl -v http://<EC2_IP>` , `curl -v https://<EC2_IP>` 로 80/443이 외부에서 실제로 열려있는지 확인
- [ ] SSAFY가 공지한 EC2 인바운드 정책 문서/공지 있는지 확인 (없으면 조교에게 문의 — **사용자 액션 필요**)

### 2. OS 기본 세팅
- [ ] `sudo apt update && sudo apt upgrade -y`
- [ ] 타임존 `Asia/Seoul` 설정, `timedatectl` 확인 (로그/스케줄 시간 꼬임 방지)
- [ ] 스왑 파일 추가 (RAM이 4GB 이하일 경우 2~4GB 스왑) — 컨테이너 여러 개 뜰 때 OOM 방지
- [ ] `ufw` 설치 여부만 확인 (보안그룹을 못 바꾸는 상황이면 ufw로 로컬 방화벽까지 관리할지는 실측 후 결정)
- [ ] 불필요한 sudo 없이 docker 명령 쓸 수 있도록 `ubuntu`를 `docker` 그룹에 추가 (Day 1-3에서 진행)

### 3. Docker 설치
- [ ] Docker Engine 설치 (공식 스크립트 or apt repo)
- [ ] Docker Compose plugin 설치 (`docker compose version` 확인)
- [ ] `sudo usermod -aG docker ubuntu` 후 재로그인, `docker run hello-world` 확인
- [ ] Docker 로그 로테이션 설정 (`/etc/docker/daemon.json`에 `log-opts max-size` 지정) — 디스크 꽉 차는 사고 예방

### 4. 모노레포 infra 구조 세팅
- [ ] `infra/` 아래 `docker-compose.yml`, `.env.example`, `nginx/`, `mysql/init/`, `scripts/` 폴더 생성 (구조는 `00-overview.md` 참고)
- [ ] `.gitignore`에 `infra/.env`, `*.pem`, `*.key` 등 시크릿 패턴 추가
- [ ] GitLab 브랜치 전략 확정 및 문서화: `main`(배포) / `develop`(통합) / `feature/*` — 백엔드/프론트 팀원과 합의 필요 (**사용자 액션: 팀 컨벤션 채널에 공유**)
- [ ] `infra/README.md`에 "로컬에서 어떻게 이 스택을 띄우는지" 최소 안내 작성 (지금은 골격만, 이후 Day마다 갱신)

## 산출물
- EC2 스펙/포트 실측 결과가 `00-overview.md`에 채워짐
- Docker/Compose 설치 완료
- `infra/` 폴더 골격 커밋

## 다음 Day로 넘길 것
- 도메인 확보 여부는 여전히 미정이면 Day 2 시작 전에 재확인
