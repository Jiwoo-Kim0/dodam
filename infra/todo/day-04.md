# Day 4 — MySQL & Redis 고도화 (P0/P1)

> **2026-09-07 재검토 반영.** MySQL/Redis 컨테이너 구성은 Day 2에서 이미 끝났다.
> 이 Day는 "띄우기"가 아니라 **데이터 안정성(백업/복구)과 운영 설정 확정**으로 범위를 다시 잡는다.

## 0. Day 3에서 이월된 것 (먼저 처리)

Day 3(Nginx/도메인/SSL)의 핵심은 끝났고, 아래가 남았다. 상세 경위: [[jiw/day-03.md]]

- [ ] ★ **`CORS_ALLOWED_ORIGINS` 갱신** — 현재 EC2 `.env`에 `http://localhost:8081` 하나뿐
      **`docs/dev_plan.md` 기준 FE는 Day 4에 "로그인 / 금융데이터 연동"을 시작한다.** 지금 설정으로는 FE가 API를 호출하는 순간 CORS에 막히므로, **이 Day에서 가장 먼저 처리해야 할 항목**이다.
      FE팀에 실제 호출 origin(Expo 개발 서버 주소, 웹 배포 시 도메인)을 확인할 것 (**사용자 액션**)
- [ ] **Jenkins admin 비밀번호 변경** — 평문 HTTP로 오갔으므로 노출 간주 (**사용자 액션**, [[security.md]] R2)
      접속: `ssh -L 8080:127.0.0.1:8080 ssafy-ec2` → `http://localhost:8080/jenkins/`
- [ ] GitLab PAT 재발급 검토 (같은 이유)
- [ ] `/` 루트 라우팅 결정 — 현재 임시 200 텍스트. FE가 Expo 웹 정적 빌드를 할지에 달려 있어 Day 7과 함께 결정 (지금 막혀도 서비스 영향은 없음)
- [ ] HSTS `max-age` 상향 — 현재 1일. 443 장애 시 HTTP 폴백을 막지 않으려고 낮춰둔 것이므로, 며칠 안정적으로 돌면 1년으로 올릴 것
- ~~`/jenkins/`에 Basic Auth 추가~~ → **불필요 판정**. UI 자체를 외부에 열지 않았으므로 이중 인증의 의미가 없음

## Day 2에서 이미 끝난 것 (재작업 불필요)

- ✅ `mysql` 8.0 컨테이너 — utf8mb4, `mysql_data` named volume 영속화, `mysqladmin ping` 헬스체크, 외부 포트 미노출
- ✅ `redis` 7 컨테이너 — `requirepass`, AOF 영속화, 헬스체크, 외부 포트 미노출
- ✅ `.env`에 `MYSQL_ROOT_PASSWORD` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` / `REDIS_PASSWORD` (EC2에서 `openssl rand`로 생성, `chmod 600`)
- ✅ root 계정과 앱 전용 계정(`dodam`) 분리 — MySQL 이미지가 `MYSQL_USER`에게 해당 DB 권한만 부여
- ✅ 백엔드가 실제로 이 MySQL/Redis에 붙어서 기동됨 (Hikari 커넥션 성공, Flyway 실행 확인)

## 확정된 사실 (문서 재확인 결과)

- **마이그레이션 도구는 Flyway로 확정.** `backend/build.gradle`에 `flyway-core` + `flyway-mysql`, `application-prod.yml`에 `flyway.enabled: true`, `ddl-auto: validate`
  - 인프라는 **"빈 DB + 계정"까지만 보장**하고 스키마는 앱 부팅 시 Flyway가 적용하는 방향이 맞음 → 기존 계획의 제안이 그대로 유효
  - 현재 `backend/src/main/resources/db/migration/`에는 **마이그레이션 파일이 없다** (README만 존재, "엔티티/물리 스키마 확정 전에는 추가하지 않는다"는 방침)
  - 즉 지금 DB는 `flyway_schema_history` 테이블만 있는 빈 상태 — 정상
- **ERD 규모는 49개 테이블** (`docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`, 1250줄). 기존 계획에 적힌 "25개 이상"보다 큼
- **민감정보는 애플리케이션 레벨 암호화**로 설계됨 — `account_no_encrypted`, `card_no_encrypted`, `fcm_token_encrypted`, `provider_email_encrypted` 등 `VARBINARY` + 조회용 `_hash CHAR(64)` 조합
  - 인프라 관점의 함의: **암호화 키를 잃으면 DB 백업이 있어도 데이터를 복구할 수 없다** → 아래 백업 항목에 반드시 반영

## 체크리스트

### 1. 시드 데이터 적재 방안 확정 (신규 항목)
`backend/database/finance_products/`에 금융상품 시드 SQL이 들어와 있다 (누적본 591KB + 카드 127KB).
- [ ] 적재 경로를 백엔드팀과 확정 (**사용자 액션**)
  - (a) Flyway 마이그레이션(`V__seed_*.sql`)으로 편입 → 버전 관리·재현성 좋음. **권장**
  - (b) `mysql/init/`(docker-entrypoint-initdb.d)에 배치 → **최초 1회, 빈 볼륨일 때만 실행**됨. 이미 볼륨이 만들어진 지금은 동작하지 않으므로 주의
  - (c) 운영 중 수동 import
- [ ] `db/migration/README.md`의 무결성 규칙(계좌 선생성 후 상세행, goal_account 제약 등)을 시드 순서가 위반하지 않는지 확인은 백엔드팀 책임 — 인프라는 적재 수단만 제공
- [ ] 591KB SQL 적재 시 `max_allowed_packet` 등 MySQL 설정이 걸리지 않는지 1회 실측

### 2. 백업 (이번 Day의 최우선)
- [ ] `infra/scripts/backup.sh` 작성
  - `mysqldump --single-transaction --routines --triggers` 로 매일 1회 덤프
  - 보관 주기: 최근 7일 (그 이상은 삭제), 덤프는 gzip 압축
  - 보관 위치: 호스트 디스크 (307GB 여유이므로 충분). S3는 자격증명 필요해서 후순위
  - **덤프 파일 권한 `600`** — 암호화 컬럼이 있다 해도 덤프에는 평문 컬럼(이메일, 금액, 거래내역)이 그대로 들어간다 ([[security.md]] R5)
- [ ] **암호화 키를 DB 백업과 함께 백업하는 절차 문서화** — 키가 없으면 덤프가 무용지물
  - 키 자체는 백업 파일과 **다른 곳**에 보관해야 의미가 있음 (같이 두면 한 번에 유출)
- [ ] cron 등록 (호스트 crontab). 컨테이너가 아니라 호스트에서 도는 게 단순함
- [ ] **복구 리허설 1회** — 덤프를 새 컨테이너/새 DB에 복원해서 실제로 살아나는지 확인 (Day 8에 또 하지 말고 여기서 먼저)
- [ ] 백업 실패 시 조용히 넘어가지 않도록 종료 코드/로그 남기기

### 3. Redis 사용 범위 확정 (🔲 확인 필요)
`backend/docs/init_settings_prompt.md`에 "캐시 / 세션 / 분산 락 중 실제 적용 범위 미확정"으로 명시되어 있다.
- [ ] 백엔드팀과 용도 확정 (**사용자 액션**)
  - 순수 캐시 → AOF 끄고 가볍게 (재시작 시 날아가도 무방)
  - 세션/분산 락 → **AOF 유지**, 재시작 시 로그인 세션이 날아가면 안 됨
- [ ] 확정 결과에 맞춰 현재 설정(AOF 켬) 유지/변경하고 근거를 이 문서에 기록
- [ ] `maxmemory` / `maxmemory-policy` 설정 — 캐시 용도라면 `allkeys-lru`, 세션 저장이면 함부로 evict되면 안 되므로 `noeviction` 검토

### 4. MySQL 운영 설정 점검
- [ ] 현재 컨테이너의 기본 설정 확인 후, 필요한 것만 조정 (16GB RAM 중 얼마를 줄지)
  - `innodb_buffer_pool_size` — 기본값은 128MB로 작음. 다른 컨테이너 몫을 남기고 2~4GB 정도 검토
- [ ] 느린 쿼리 로그 활성화 여부 (Day 6 모니터링과 연결)
- [ ] 타임존: 컨테이너에 `TZ=Asia/Seoul`은 넣었으나, MySQL 세션 타임존과 JDBC `serverTimezone` 설정이 어긋나지 않는지 실제 INSERT/SELECT로 1회 확인

### 5. 검증
- [ ] `docker compose down && up` 후 데이터 유지 확인 (**`down -v`는 절대 금지** — 볼륨이 지워짐)
- [ ] 앱 전용 유저(`dodam`)가 자기 DB 외에는 접근 못 하는지 권한 확인
- [ ] 백업 스크립트 수동 1회 실행 → 덤프 생성 → **복원까지** 확인

## 산출물
- 매일 자동 백업 + 복구 절차가 검증된 상태
- Redis 용도/영속화 방침 확정 및 근거 기록
- 시드 데이터 적재 경로 확정

## 리스크
- `docker compose down -v`를 실수로 실행하면 DB가 통째로 날아간다 → `deploy.sh`에는 down이 없지만, 수동 작업 시 주의. Day 8 문서에 경고 문구 명시
- 암호화 키 관리가 정해지지 않은 채 데이터가 쌓이기 시작하면 나중에 되돌리기 어렵다 → Day 5 시크릿 작업과 순서를 맞출 것
