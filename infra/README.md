# infra

도담 서비스 인프라. EC2 1대 위에 Docker Compose로 전체 스택을 구동한다. 전체 계획은 `todo/00-overview.md` 참고.

## 로컬/서버에서 스택 띄우기

```bash
cd infra
cp .env.example .env   # 값 채워넣기 (절대 커밋 금지)
docker compose up -d
```

## 진행 상황

- [x] Day 1: 서버 기초 설정 & 리포 구조
- [ ] Day 2: CI/CD 파이프라인 (Jenkins, 더미 앱)
- [ ] Day 3: Nginx 리버스 프록시 정식화 & 도메인/SSL
- [ ] Day 4: MySQL & Redis
- [ ] Day 5: 백엔드 컨테이너화 & 시크릿 관리
- [ ] Day 6: 모니터링 스택
- [ ] Day 7: 외부 연동 & FE 배포 파이프라인
- [ ] Day 8: 통합 리허설 & 문서화

## 구조

```
infra/
  docker-compose.yml
  .env.example
  nginx/conf.d/
  mysql/init/
  monitoring/{prometheus,grafana,loki}/
  scripts/
  todo/            # 일자별 계획
```
