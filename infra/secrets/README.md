# 파일 형태 시크릿 보관 위치

이 디렉터리의 파일은 **절대 커밋되지 않는다** (`.gitignore`에서 차단, `.gitkeep`과 이 README만 예외).

## fcm-service-account.json

Firebase 푸시 알림용 서비스 계정 키.

**왜 `.env`가 아니라 파일인가**
JSON 전체를 환경변수에 넣으면 줄바꿈과 따옴표 때문에 깨지기 쉽고,
프로세스 목록이나 로그에 통째로 노출될 위험이 크다. 파일 마운트가 안전하다.

**발급 방법**
1. Firebase 콘솔 → 프로젝트 설정 → 서비스 계정
2. "새 비공개 키 생성" → JSON 다운로드
3. EC2로 옮긴다 (레포 안에 두지 말 것)
   ```bash
   scp <다운로드한파일>.json ssafy-ec2:/home/ubuntu/app/infra/secrets/fcm-service-account.json
   ```

4. **소유권을 컨테이너 실행 유저에게 넘긴다** ← 빠뜨리기 쉬운 단계
   ```bash
   ssh ssafy-ec2 'sudo chown 10001:10001 ~/app/infra/secrets/fcm-service-account.json \
                  && sudo chmod 600 ~/app/infra/secrets/fcm-service-account.json'
   ```

   **왜 필요한가**: backend 컨테이너는 보안상 비루트(`uid 10001`)로 실행된다
   ([[../todo/security.md]] R3). `scp`로 올린 파일은 `ubuntu`(uid 1000) 소유에 권한 600이라
   **컨테이너에서 읽지 못한다**(`Permission denied`).
   마운트는 성공하고 컨테이너도 healthy로 뜨기 때문에, 이 단계를 빠뜨리면
   **나중에 푸시 발송 코드가 붙었을 때 런타임에서야 실패**한다.

   소유권을 넘긴 뒤에는 `ubuntu`로 파일을 읽으려면 `sudo`가 필요하다.

5. 백엔드 재기동
   ```bash
   ssh ssafy-ec2 'cd ~/app/infra && docker compose up -d --no-deps backend'
   ```

6. **컨테이너에서 실제로 읽히는지 확인**
   ```bash
   ssh ssafy-ec2 'docker exec backend head -c 25 /run/secrets/fcm-service-account.json'
   ```
   JSON 앞부분이 보이면 정상. `Permission denied`면 4번을 다시 확인할 것.

컨테이너 안 경로는 `/run/secrets/fcm-service-account.json` 으로 고정되어 있고,
백엔드는 `FCM_SERVICE_ACCOUNT_PATH` 환경변수로 이 경로를 받는다.

**주의**: 이 키는 푸시 발송 권한을 가진다. 유출되면 임의의 알림을 보낼 수 있으므로
`backup-secrets.sh`와 함께 서버 밖 안전한 곳에 백업하고, 레포나 채팅에 올리지 않는다.
