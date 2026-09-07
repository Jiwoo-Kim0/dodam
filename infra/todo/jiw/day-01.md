# Day 1 — 실제 진행 기록

계획: `infra/todo/day-01.md` 참고

## 2026-09-04

### 로컬 레포 스켈레톤 생성
- 저장소 루트에 `.gitignore` 생성
  - `infra/.env`, `infra/**/.env`, `*.pem`, `*.key`, `infra/jenkins_home/`, OS/IDE/로그 파일 패턴 포함
- `infra/` 아래 디렉터리 생성: `nginx/conf.d`, `mysql/init`, `scripts`, `monitoring/{prometheus,grafana,loki}` (각 `.gitkeep`로 빈 폴더 유지)
- `infra/docker-compose.yml` 최초 작성
  - 현재는 `nginx` 서비스만 정의 (80/443 바인딩, `nginx/conf.d`를 read-only 마운트)
  - `jenkins_home` 볼륨 미리 선언 (Day 2에서 사용 예정)
  - 파일 하단에 Day 2/4/5/6에서 추가될 서비스 주석으로 명시
- `infra/.env.example` 작성 — MySQL/Redis/JWT/OpenAI/SSAFY 금융망/FCM/Kakao/Google 키 이름만, 값은 비움
- `infra/README.md` 작성 — 스택 기동 방법(`docker compose up -d`), Day1~8 진행 체크리스트, 디렉터리 구조 안내

### 요청
- "매번 무언가를 할때마다, 너가 한 거랑 내가 한 거를 전부 다 infra/todo/jiw 안에 상세히 기록해. 위에 했던 것들도 당연히 기록하고"

### 대응
- `infra/todo/jiw/` 폴더 생성, `README.md`(로그 규칙 설명), `day-00-planning.md`(계획 수립 단계 소급 기록), 이 파일(`day-01.md`) 작성
- 앞으로 매 작업마다 실시간으로 이 폴더에 기록하기로 함

### 로그 형식 변경
- "굳이 claude가 한것이랑 김지우가 한 것을 구분하지는 않아도 돼" → `README.md`, `day-00-planning.md`, 이 파일에서 `[Claude]`/`[김지우]` 태그 제거, 구분 없이 시간순 기록으로 통일

### SSH 접속 트러블슈팅
- pem 키를 `~/.ssh/J15D105T.pem`으로 옮기고 `~/.ssh/config`에 `Host ssafy-ec2` 항목 작성 완료 (`HostName j15D105.p.ssafy.io`, `User ubuntu`, `IdentityFile ~/.ssh/J15D105T.pem`)
- 직접 `ssh -i J15D105T.pem ubuntu@j15D105.p.ssafy.io` 실행 → `Identity file J15D105T.pem not accessible: No such file or directory` + `Permission denied (publickey)`
  - 원인: 홈 디렉터리에서 실행해서 상대경로 `J15D105T.pem`을 못 찾음 (실제 파일은 `~/.ssh/`에 있음)
- Claude가 `~/.ssh/` 상태 확인 (`ls -la`, `cat config`) → 키/config 자체는 문제없음 확인
- Claude가 `ssh ssafy-ec2` (alias)로 접속 테스트 → **성공** (`ubuntu@ip-172-26-3-12`)
- 해결: `-i` 없이 `ssh ssafy-ec2`로 접속하거나, `-i` 쓸 거면 전체 경로(`~/.ssh/J15D105T.pem`)로 지정해야 함을 안내

### SSH pem 키 보관 위치 질문
- "ssh private 인증 키 pem 파일을 어디에 넣어야할까?"
- 안내: 레포 안에는 절대 넣지 않고 로컬 홈 디렉터리에 보관
  - Windows(MobaXterm): `C:\Users\<사용자명>\.ssh\S15P21D105-ec2.pem`
  - 이 WSL 환경: `~/.ssh/S15P21D105-ec2.pem` (`chmod 400`), `~/.ssh/config`에 `Host ssafy-ec2` 항목 추가하는 방법 안내

### Day 1 EC2 작업용 명령어 전달 (사용자 실행 대기)
아래 구간을 사용자에게 안내함. **아직 사용자가 실행한 결과는 받지 않음 — 실행 후 결과를 이 파일에 추가로 기록할 것.**

**① 서버 실측**
```bash
nproc
free -h
df -h
sudo ss -tlnp
```
로컬 PC에서:
```bash
curl -v http://<EC2_공인IP>
curl -v https://<EC2_공인IP>
```

**② OS 기본 세팅**
```bash
sudo apt update && sudo apt upgrade -y
sudo timedatectl set-timezone Asia/Seoul
timedatectl
```
스왑(필요 시):
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```
```bash
sudo apt install -y ufw
sudo ufw status
```

**③ Docker 설치**
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker ubuntu
```
(SSH 재접속 필요)
```bash
docker compose version
docker run hello-world
```
로그 로테이션:
```bash
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
EOF
sudo systemctl restart docker
```

**④ 레포 clone**
```bash
sudo apt install -y git
git clone <GitLab Lab 프로젝트 URL> ~/app
cd ~/app
git checkout infra
```
- GitLab Lab HTTPS clone 시 Personal Access Token 필요 여부 확인 필요 (미확인 상태로 안내함)

### ① 서버 실측 결과 (사용자 실행, EC2 내부)

```
$ nproc
4

$ free -h
               total        used        free      shared  buff/cache   available
Mem:            15Gi       638Mi        13Gi       2.7Mi       2.1Gi        14Gi
Swap:             0B          0B          0B

$ df -h
Filesystem       Size  Used Avail Use% Mounted on
/dev/root        309G  2.6G  307G   1% /
tmpfs            7.8G     0  7.8G   0% /dev/shm
tmpfs            3.1G  924K  3.1G   1% /run
tmpfs            5.0M     0  5.0M   0% /run/lock
efivarfs         128K  3.1K  120K   3% /sys/firmware/efi/efivars
/dev/nvme0n1p16  881M  183M  637M  23% /boot
/dev/nvme0n1p15  105M  6.2M   99M   6% /boot/efi
tmpfs            1.6G   12K  1.6G   1% /run/user/1000

$ sudo ss -tlnp
LISTEN 127.0.0.53%lo:53, 127.0.0.54:53   (systemd-resolved)
LISTEN 0.0.0.0:22, [::]:22               (sshd)
LISTEN 127.0.0.1:6010/6011, [::1]:6010/6011 (sshd, X11 forwarding용)
```

**해석**
- CPU 4코어, RAM 16GB(가용 14GB), 디스크 307GB 여유 — 애초에 걱정했던 "메모리 빠듯함" 문제 없음. **스왑 설정 불필요**, `00-overview.md`의 미확인 항목 중 "EC2 사양" 갱신 완료.
- 현재 리스닝 중인 외부 노출 포트는 **22(SSH)뿐**. 80/443은 아직 아무 것도 안 떠 있어서(nginx 미기동) 당연히 안 보임 — 정상.
- `sudo`가 요구한 걸로 보아 root 권한(sudo) 자체는 정상 동작.

**남은 확인**: 로컬 PC에서 `curl -v http://<EC2_공인IP>`, `curl -v https://<EC2_공인IP>` 테스트는 아직 미실행 — 보안그룹이 80/443을 실제로 열어놨는지는 nginx를 띄운 뒤에나 의미있게 검증 가능하므로, Docker+nginx 기동 이후로 미룸.

### ② OS 기본 세팅 결과 (사용자 실행, EC2 내부)

```
$ sudo apt update && sudo apt upgrade -y
(생략) ... No containers need to be restarted.
User sessions running outdated binaries:
 ubuntu @ session #1144: sshd[383390]
 ubuntu @ session #1146: sshd[383392]
No VM guests are running outdated hypervisor (qemu) binaries on this host.

$ sudo apt install -y ufw
ufw is already the newest version (0.36.2-6).

$ sudo ufw status
Status: active

To                         Action      From
--                         ------      ----
22                         ALLOW       Anywhere
22 (v6)                    ALLOW       Anywhere (v6)
```

**해석**
- `apt upgrade`로 일부 시스템 라이브러리가 업데이트되어 현재 SSH 세션(sshd)이 "outdated binary"로 표시됨 — 심각한 문제는 아니지만, 반영하려면 SSH 재접속 권장 (Docker 설치 후 그룹 재적용을 위해서도 어차피 재접속 필요하니 그때 같이 처리).
- **중요**: `ufw`가 이미 **active** 상태이고, 현재 **22번 포트만 허용**되어 있음. 즉 지금 상태에서 nginx로 80/443을 띄워도 **ufw가 먼저 막아서** 외부 접근이 안 될 것 → Day 1에 80/443 규칙을 미리 추가해야 함. (`00-overview.md`의 "실제로 열려있는 외부 포트" 미확인 항목과 직결되는 발견 — AWS 보안그룹뿐 아니라 OS 레벨 ufw도 별도로 열어야 한다는 뜻)
- `timedatectl set-timezone` / `timedatectl` 출력은 아직 전달받지 못함 — 미확인 상태로 남김.

### ufw 80/443 허용 + timedatectl + Docker 설치 결과 (사용자 실행, EC2 내부)

```
$ sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
Rule added / Rule added (v6)  x2

$ sudo ufw status
Status: active
22            ALLOW  Anywhere / (v6)
80/tcp        ALLOW  Anywhere / (v6)
443/tcp       ALLOW  Anywhere / (v6)

$ timedatectl
Local time: Sun 2026-09-06 23:12:57 KST
Time zone: Asia/Seoul (KST, +0900)
System clock synchronized: yes
NTP service: active

$ docker compose version
Docker Compose version v5.5.1

$ docker run hello-world
Hello from Docker!  (sudo 없이 성공 — usermod -aG docker 그룹 재적용 확인)
```

**해석**
- ufw에 22/80/443 (v4+v6) 모두 허용 완료. AWS 보안그룹도 80/443이 열려있다는 전제하에 이제 OS 레벨 방화벽은 준비됨.
- 타임존 Asia/Seoul 확정, NTP 동기화 정상 — `00-overview.md`에 별도 미확인 항목은 아니었지만 정상 확인.
- Docker + Compose 설치 및 그룹 권한 정상 동작 확인 (`sudo` 없이 `docker run` 성공).

### 로컬 → EC2 80번 포트 접근 테스트 (사용자 실행, WSL/로컬 PC에서)

```
$ curl -v http://j15D105.p.ssafy.io
* IPv4: 43.203.198.18
*   Trying 43.203.198.18:80...
* connect to 43.203.198.18 port 80 from ... failed: Connection refused
curl: (7) Failed to connect to j15D105.p.ssafy.io port 80 after 89 ms: Could not connect to server
```

**해석**
- 도메인 `j15D105.p.ssafy.io` → `43.203.198.18` DNS 해석 정상됨 (SSAFY 측에서 도메인을 이미 붙여준 상태 — `00-overview.md`의 "도메인 유무" 미확인 항목 해소: **도메인 있음**).
- 89ms 만에 **"Connection refused"** 응답 — 이건 패킷이 실제로 서버까지 도달해서 OS가 "그 포트엔 듣고 있는 프로세스가 없다"고 즉시 거절(RST)한 것. 만약 AWS 보안그룹이 80번을 막고 있었다면 응답 자체가 없어 **타임아웃**이 났을 것 (SG는 보통 조용히 DROP함).
- 즉 **AWS 보안그룹 80번 인바운드는 이미 열려 있는 것으로 추정됨** (443도 같은 방식으로 열려있을 가능성 높음, 별도 확인 권장). `00-overview.md`의 "실제로 열려있는 외부 포트" 미확인 항목에 긍정적 신호 — nginx만 띄우면 바로 접근될 것으로 예상.
- 아직 nginx 컨테이너를 안 띄웠으니 "refused"가 정상이고 기대한 결과임. Day 2~3에서 nginx를 EC2에서 기동한 뒤 다시 curl 테스트해서 200(또는 502 — 백엔드 없어서)이 뜨는지로 최종 검증 예정.

### Docker 로그 로테이션 + 레포 clone 결과 (사용자 실행, EC2 내부)

```
$ sudo tee /etc/docker/daemon.json <<'EOF'
{ "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
EOF
$ sudo systemctl restart docker
$ docker run hello-world
Hello from Docker!  (재시작 후에도 정상 동작 확인)
```

- `/etc/docker/daemon.json` 로그 로테이션 설정 적용 완료, Docker 재시작 후에도 정상 동작.
- 이어서 레포 clone 완료 — 프롬프트가 `ubuntu@ip-172-26-3-12:~/app$`로 바뀐 것으로 확인 (`git clone ... ~/app` 성공, `~/app`으로 이동됨). `git checkout infra` 여부는 별도 확인 안 됐으나 진행상 infra 브랜치로 전환했을 것으로 추정 — Day 2에서 실제로 이 clone을 사용할 때 브랜치 재확인 예정.

## Day 1 완료 처리
- P0 대상 서버 기초 세팅(사양 실측, 타임존, ufw 22/80/443, Docker+Compose, 로그 로테이션, 레포 clone) **전부 완료**.
- 확인된 사실: EC2 4vCPU/16GB/307GB, 도메인 `j15D105.p.ssafy.io` 있음, 보안그룹 80 열려있는 것으로 추정.
- 남은 미확인 항목(Day 2 이후로 이월): GitLab Runner 등록 가능 여부, 443 실제 접근 테스트, `git checkout infra` 최종 확인, `.env` 실제 값 채우기.

## 다음 기록할 것 (TODO)
- [x] 사용자가 ① 실행한 결과 (nproc/free/df/ss 출력)
- [x] `00-overview.md`의 "아직 확인되지 않은 것" 표 중 "EC2 사양" 갱신
- [x] ② `apt update/upgrade`, `ufw` 설치 상태 확인 결과 기록
- [x] `timedatectl` 결과 확인 — Asia/Seoul 정상
- [x] ufw에 80/443 허용 규칙 추가
- [x] ③ Docker 설치 + `docker compose version` + `hello-world` 확인
- [x] 로컬→EC2 80번 curl 테스트 (refused = SG 열려있음, nginx 미기동이라 정상)
- [x] `00-overview.md` "도메인 유무" 항목 갱신 (도메인 있음: `j15D105.p.ssafy.io`)
- [x] Docker 로그 로테이션(`/etc/docker/daemon.json`) 설정
- [x] ④ 레포 clone (EC2 서버 `~/app`)
- [x] **Day 1 완료 처리**
- [x] `git checkout infra` 최종 확인 — 사용자가 EC2 `~/app`에서 현재 `infra` 브랜치임을 확인함
- [ ] (Day 2로 이월) GitLab Runner 등록 가능 여부, 로컬 레포 커밋/push
