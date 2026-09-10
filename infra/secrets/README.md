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
   ssh ssafy-ec2 'chmod 600 ~/app/infra/secrets/fcm-service-account.json'
   ```
4. 백엔드 재기동
   ```bash
   ssh ssafy-ec2 'cd ~/app/infra && docker compose up -d --no-deps backend'
   ```

컨테이너 안 경로는 `/run/secrets/fcm-service-account.json` 으로 고정되어 있고,
백엔드는 `FCM_SERVICE_ACCOUNT_PATH` 환경변수로 이 경로를 받는다.

**주의**: 이 키는 푸시 발송 권한을 가진다. 유출되면 임의의 알림을 보낼 수 있으므로
`backup-secrets.sh`와 함께 서버 밖 안전한 곳에 백업하고, 레포나 채팅에 올리지 않는다.
