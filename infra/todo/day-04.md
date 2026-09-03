# Day 4 — MySQL & Redis (P0)

기획안/ERD 기준: 금융 DB(MySQL)에 User/Account/Transaction/Loan/DepositSaving/Goal/Diagnosis/Recommendation/Simulation/Product 등 25개 이상 엔티티가 들어간다. 초기부터 데이터 안정성(볼륨 영속화, 백업)을 잡아둔다.

## 체크리스트

### 1. MySQL 컨테이너
- [ ] `docker-compose.yml`에 `mysql` 서비스 추가 (버전은 백엔드 팀이 쓰는 JPA/드라이버와 맞는 8.x LTS로)
- [ ] 데이터 디렉터리를 named volume으로 영속화 (`mysql_data:/var/lib/mysql`)
- [ ] `infra/mysql/init/`에 최초 데이터베이스/유저 생성 스크립트 배치 (`docker-entrypoint-initdb.d`)
- [ ] root 계정과 앱 전용 계정 분리 (앱은 최소 권한 유저 사용)
- [ ] `.env`에 `MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD` 정의, `.env.example`에는 값 없이 키만

### 2. 스키마 마이그레이션 전략 (백엔드팀 협의 필요 — **사용자 액션**)
- [ ] Flyway/Liquibase 사용 여부를 백엔드 담당자(정현진/동재 구/채운 김)와 확정
- [ ] ERD 문서(`docs/ERD/`) 기준 초기 마이그레이션 파일 위치를 백엔드 레포 구조에 맞춰 안내 (`src/main/resources/db/migration` 등)
- [ ] 인프라 쪽에서는 "빈 DB + 유저"만 보장하고, 스키마 적용은 애플리케이션 부팅 시 자동 마이그레이션으로 위임하는 방향 제안

### 3. Redis 컨테이너
- [ ] `docker-compose.yml`에 `redis` 서비스 추가 (캐싱 용도 — 아키텍처 이미지의 "캐싱" 박스)
- [ ] 비밀번호(`requirepass`) 설정, `.env`로 관리
- [ ] AOF/RDB 영속화 여부 결정 (캐시 전용이면 영속화 없이 가볍게 가도 무방 — 백엔드팀이 세션/락 등 캐시 이상의 용도로 쓰는지 확인)

### 4. 백업
- [ ] `infra/scripts/backup.sh` 작성: `mysqldump`로 매일 1회 덤프 → 로컬 디스크 보관(+가능하면 S3, 단 자격 증명 필요하므로 우선 로컬)
- [ ] cron 등록 (`crontab -e` 또는 systemd timer)
- [ ] 덤프 파일 보관 주기(예: 최근 7일) 설정, 디스크 사용량 확인 루틴 추가

### 5. 검증
- [ ] 컨테이너 재시작 후에도 데이터가 유지되는지 (`docker compose down && up` 후 확인, `down -v`는 절대 하지 않기)
- [ ] 앱 전용 유저로 원격 접속 테스트 (같은 Docker 네트워크 안에서)
- [ ] 백업 스크립트 1회 수동 실행해서 덤프 파일 생성 확인

## 산출물
- MySQL/Redis가 컨테이너로 뜨고, 데이터 영속화·백업 스크립트 확보
- 마이그레이션 전략에 대한 백엔드팀과의 합의 내용 문서화

## Day 5로 넘길 것
- 백엔드 컨테이너가 이 MySQL/Redis에 실제로 연결되는지는 Day 5에서 검증
