# Day 4 — 실제 진행 기록

계획: `infra/todo/day-04.md`, 보안 방침: `infra/todo/security.md`

## 2026-09-07

### DB 백업/복구 체계 구축
착수 시점 상태: DB에 `flyway_schema_history`만 존재(마이그레이션 0건), 볼륨 201MB, 디스크 여유 298GB.
**데이터가 없는 지금이 복구 리허설을 마음껏 해볼 수 있는 유일한 시점**이라 판단해 이 작업을 먼저 했다.

- `infra/scripts/backup.sh`
  - `mysqldump --single-transaction`으로 락 없이 일관 시점 덤프 → gzip → 권한 600
  - 비밀번호는 `MYSQL_PWD` 환경변수로 전달 (명령행 인자로 주면 컨테이너 프로세스 목록에 노출됨)
  - **덤프 검증 3단계**: 파일 크기 / gzip 무결성 / 내용 마커(`MySQL dump`)
    — 백업에서 가장 위험한 실패는 "빈 파일이 성공으로 기록되는 것"이라 명시적으로 막았다
  - 보관 7일, 초과분 자동 삭제, 디스크 사용률 85% 초과 시 경고
- `infra/scripts/restore.sh`
  - **기본 동작이 임시 DB(`dodam_restore_test`) 복구** — 리허설을 부담 없이 반복하기 위함
  - 운영 덮어쓰기는 `--into-production` + DB명 확인 문구 입력 + **복구 전 안전 백업 자동 생성**
- cron 등록: 매일 04:00 KST (`timedatectl`로 시스템 타임존이 Asia/Seoul임을 확인)

**복구 리허설 (실제 수행)**
1. 운영 DB에 `restore_probe` 테이블 생성 후 행 삽입
2. `backup.sh` 실행 → 덤프 생성
3. `restore.sh <덤프>` → 임시 DB로 복구
4. 임시 DB에서 `SELECT` → `1  backup-restore-test` 조회 성공
5. 테스트 흔적 제거, 운영 DB가 원상태(`flyway_schema_history`만)인지 확인

**남은 한계**: 백업이 같은 EC2 디스크에만 있다. 인스턴스 자체가 손실되면 백업도 함께 사라진다.
(S3는 자격증명이 없어 사용 불가 → Day 8에 "주기적으로 로컬로 내려받기" 절차를 넣을 것)

---

## 2026-09-08

### 팀원 요청 접수 (Auth·User API MR)
백엔드 팀원으로부터 요청: MR(`[BE] Feature: D2 Auth·User API 구현`)이 머지되면 백엔드가 환경변수 4개를 기동 시점에 검사하므로, 머지 전에 EC2 `.env`에 채워달라는 내용.
- `JWT_SECRET`(기존 값 유지), `DATA_ENCRYPTION_KEY`, `DATA_HASH_KEY`, `SSAFY_FINANCE_API_KEY`
- 암호화 키는 잃으면 복구 불가하므로 `security.md` R5대로 DB 백업과 **별도 장소에** 백업 요청
- MR이 `docker-compose.yml`(인프라 파일)을 이미 건드렸다는 사전 공유
- Hibernate validate 에러가 나면 `finance_connection CHAR(64)` 또는 `user_setting TINYINT(1)` 매핑 문제일 것이라는 추정, 급하면 `ddl-auto: validate`를 `none`으로 낮춰도 된다는 허용

### 1. MR 변경분 사전 검토 (충돌 여부)
`git diff origin/develop...origin/S15P21D105-494-...`로 확인:
- `infra/docker-compose.yml`: backend `environment`에 4줄 추가뿐 → 내 작업분(certbot 서비스, jenkins 루프백 바인딩, nginx `depends_on` 제거)과 **다른 구역이라 충돌 없음**
- `infra/.env.example`: 키 2개 추가
- **`db/migration/V1`, `V2` 신규** ← 이때 처음 인지. 지금까지 DB가 비어 있던 이유가 "마이그레이션이 아직 없어서"였는데, 이제 실제 스키마가 생긴다는 뜻
- `application-prod.yml`에 `app.auth.jwt.secret`, `app.crypto.data-key/hash-key`, `app.ssafy-finance.api-key` 추가

### 2. `.env` 정비 — 조용한 함정 두 가지 발견
요청대로 값을 채우려고 `.env`를 열었더니 두 가지 문제가 있었다.

**(1) 중복 키**
```
24:SSAFY_FINANCE_API_KEY=
28:SSAFY_FINANCE_API_KEY=
```
`docker compose --env-file`은 **나중 정의가 이긴다.** 24행에 값을 채워 넣어도 28행의 빈 값이 덮어써서, "분명히 넣었는데 안 먹는" 상황이 된다. 게다가 겉으로는 아무 경고도 없다.
→ 뒤에 나온 중복 줄 제거 (앞 정의를 정본으로).

**(2) 인라인 주석 때문에 값이 비어 있음**
```
DATA_ENCRYPTION_KEY=   # openssl rand -base64 32
DATA_HASH_KEY=         # openssl rand -base64 32
```
팀원 메시지의 **안내 문구가 그대로 붙여넣어져 있었다.** `#` 뒤는 주석으로 잘리므로 값은 빈 문자열이다. `sed`로 마스킹해서 보면 `DATA_ENCRYPTION_KEY=***`처럼 **채워진 것처럼 보이는** 게 특히 위험했다 — 실제로 처음엔 "이미 값 있음"으로 잘못 판정했고, `base64 -d | wc -c`로 길이를 재보고 나서야 0바이트임을 알았다.

**교훈**: 시크릿은 "있는지"가 아니라 **"형식이 맞는지"로 검증**해야 한다. 존재 검사만으로는 이런 값을 걸러내지 못한다.

→ 실제 키 생성(각 `openssl rand -base64 32`, 디코드 32바이트 = AES-256), `chmod 600`.
   기존 값이 있으면 덮어쓰지 않도록 처리 — **암호화 키를 바꾸면 기존 데이터를 복호화할 수 없기 때문.**

### 3. 재발 방지: 배포 전 `.env` 사전 검사 (`check-env.sh`)
위 두 문제는 모두 **"백엔드 컨테이너가 죽는다"는 증상으로만 드러나서** 원인 추적에 시간이 걸린다. 배포 전에 먼저 걸러내도록 스크립트를 만들고 `deploy.sh`에 연결했다.

검사 항목
1. 중복 키
2. 값 뒤 인라인 주석
3. **필수 변수 공백 여부** — 목록을 하드코딩하지 않고 **`docker-compose.yml`이 참조하는 `${VAR}`에서 자동 추출**한다. 그래야 백엔드가 새 변수를 요구하기 시작할 때 검사도 자동으로 따라간다 (`${VAR:-기본값}`은 기본값이 있으므로 제외)
4. `DATA_*` 키의 base64 32바이트 여부
5. `.env` 권한 600

**검증**
- 현재 `.env` → 통과 (필수 변수 7개)
- **MR의 compose 파일을 기준으로 돌려 "머지 후 상황"을 시뮬레이션** → `SSAFY_FINANCE_API_KEY 가 비어 있음`을 정확히 검출하고 종료코드 1
  즉 MR이 머지되면 배포가 **컨테이너 크래시 루프 대신 명확한 메시지로 즉시 중단**된다.

### 4. 시크릿 백업 (`backup-secrets.sh`)
`security.md` R5의 "키와 덤프를 같은 곳에 두지 않는다"를 실제 절차로 만들었다.
- `.env`를 `openssl enc -aes-256-cbc -pbkdf2 -iter 200000`으로 암호화한 단일 파일 생성
- 출력 위치를 DB 덤프 디렉터리(`~/backups/mysql`)와 **의도적으로 분리**(`~/secrets-export`)
- **cron에 넣지 않았다** — 자동화하면 DB 덤프와 같은 주기·같은 서버에 시크릿 백업이 계속 쌓여서, 한 번의 유출로 둘 다 털린다. 수동으로, 서버 밖으로 내보내는 것이 목적
- 실행 후 `scp`로 내려받고 서버 사본을 삭제하는 절차를 스크립트가 직접 안내
- **검증**: 암호화 → 복호화 → 원본 `.env`와 `diff` 일치 확인 후 테스트 산출물 삭제

### 5. 머지 전 사전 검증 — 팀원의 추정과 다른 원인을 발견
팀원이 경고한 Hibernate validate 문제를 **머지 후 장애로 겪지 않기 위해**, 해당 브랜치를 격리 환경에 실제로 띄워봤다.

방법 (운영에 영향 없이)
1. `git archive`로 브랜치 소스만 `/tmp`에 추출 (배포 체크아웃을 건드리지 않기 위해 worktree 대신 archive 사용)
2. 그 소스로 `premerge-backend` 이미지 빌드
3. **별도의 임시 MySQL·Redis 컨테이너**를 띄워 운영 DB와 완전히 분리
4. 실제 키를 주입하고 기동 (`SSAFY_FINANCE_API_KEY`는 더미) → 로그 관찰

결과
- Flyway V1·V2 **정상 적용** (`Successfully applied 2 migrations`)
- Hibernate validate **실패** — 그런데 원인이 팀원의 추정(`CHAR(64)` / `TINYINT(1)` 매핑)이 아니라
  ```
  Schema-validation: missing table [financial_company]
  ```
- Hibernate는 첫 실패만 보고하므로, **엔티티 `@Table` 목록과 실제 생성된 테이블을 대조**해 누락분을 전부 특정했다.

| 엔티티가 요구 | Flyway가 생성 | 판정 |
| --- | --- | --- |
| auth_session, finance_connection, financial_profile, user_setting | 생성됨 | OK |
| **financial_company** | 없음 | ⚠️ 누락 |
| **financial_product** | 없음 | ⚠️ 누락 |
| **financial_product_option** | 없음 | ⚠️ 누락 |

추가 확인
- 누락 3개는 `domain/product/entity/`의 `FinancialCompany`, `FinancialProduct`, `FinancialProductOption`이 요구
- **`docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`에 세 테이블 DDL이 모두 존재** → 마이그레이션 작성 시 그대로 가져다 쓸 수 있음
- `backend/database/seeds/`의 금융상품 시드 SQL이 바로 이 `financial_company` / `financial_product`에 INSERT 한다
  → **테이블이 없으면 시드 적재도 불가능**하다. Day 4의 "시드 적재 방안" 항목과 같은 뿌리의 문제였다

검증 후 임시 컨테이너·이미지·소스를 모두 정리하고 운영에 영향이 없음을 확인했다.

### 6. 그런데 이미 운영이 죽어 있었다 — 장애 대응
검증 환경을 정리한 직후 상태를 확인하다가 **운영 백엔드가 재시작 루프**에 빠져 있는 것을 발견했다.

```
RestartCount=604  ExitCode=1
Caused by: SchemaManagementException: Schema-validation: missing table [financial_company]
외부 https://j15d105.p.ssafy.io/api/health → 502
```

**원인이 예상과 달랐다.** Auth MR은 **아직 머지 전**이었고, 이미 머지된 것은 상품 API MR들이었다.

| | 상태 |
| --- | --- |
| `origin/develop`의 마이그레이션 | **`README.md`뿐 — 0건** |
| develop에 병합된 것 | `S15P21D105-525` 금융회사·상품 조회 API, `S15P21D105-526` 상품 비교·추천 API |
| 그 MR들이 들여온 것 | 상품 **엔티티**(테이블을 만드는 마이그레이션 없이) |

즉 **엔티티만 병합되고 스키마가 따라오지 않아서** `ddl-auto: validate`가 기동을 막은 것이다.
공교롭게도 내가 Auth 브랜치로 재현한 에러와 동일했는데, 그 브랜치도 develop을 포함하고 있어 같은 결함을 물려받았기 때문이다.

**파이프라인은 이걸 제대로 잡았다**
| 빌드 | 커밋 | 결과 |
| --- | --- | --- |
| #13 | `f26df09` (인프라 백업 스크립트) | SUCCESS |
| #14 | `c243cd5` (상품 조회 API) | **FAILURE** |
| #15 | `70570dc` (상품 비교·추천 API) | **FAILURE** |

배포 스크립트의 헬스체크가 정상 동작해 두 배포를 실패로 종료시켰다. 문제는 **아무도 그 실패를 알아채지 못했다는 것**이다. 그 사이 서비스는 502 상태였다.

**복구 조치**
팀원이 사전에 허용한 우회책을 사용하되, **백엔드 소스를 인프라가 고치지 않는 방식**을 택했다.
```yaml
# docker-compose.yml, backend 서비스
SPRING_JPA_HIBERNATE_DDL_AUTO: none   # [임시] 마이그레이션 들어오면 제거
```
Spring Boot의 relaxed binding이 이 환경변수를 `spring.jpa.hibernate.ddl-auto`에 매핑한다.
`application-prod.yml`을 직접 수정하면 백엔드 팀의 파일에 인프라 변경이 섞이고 MR 충돌을 유발하므로 피했다.

결과: `Started DodamApplication`, 컨테이너 healthy, 외부 `/api/health` **200** 복구.
Day 3의 교훈대로 **복구 직후 바로 커밋**해서, 다음 배포의 `git reset --hard`에 지워지지 않게 했다.

---

### 7. Auth MR 수용 검토 — `ddl-auto: validate` 사전 검증
팀원이 MR 설명에 남긴 우려: **"`ddl-auto: validate`는 로컬(H2)에서 검증할 수 없으니 배포 후 첫 기동 로그를 확인해야 한다. 특히 `CHAR(64)` 해시 컬럼과 `TINYINT(1)` 불리언."**

이건 **머지 전에 답을 낼 수 있는 문제**라 판단해 검증했다. 앞선 5번 검증은 `missing table [financial_company]`에서 멈춰 **컬럼 검사까지 도달하지 못했다** — Hibernate는 첫 실패에서 중단하기 때문이다. 그래서 상품 테이블을 채운 상태로 다시 돌렸다.

방법
1. `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`에서 누락 3개 테이블 DDL을 추출해 마이그레이션 초안 작성
   → `infra/todo/drafts/V3__create_financial_product.sql`
2. Auth 브랜치 소스에 이 초안을 얹어 이미지 빌드
3. **별도의 임시 MySQL/Redis**에 운영과 동일한 조건(`ddl-auto`는 낮추지 않은 원래 `validate`)으로 기동
4. 실제 `.env`의 키 4종을 주입 (운영과 같은 값으로 검증)

결과
```
Migrating schema `dodam_vtest` to version "1 - create user and auth session"
Migrating schema `dodam_vtest` to version "2 - create user setting financial profile finance connection"
Migrating schema `dodam_vtest` to version "3 - create financial product"
Successfully applied 3 migrations, now at version v3
Started DodamApplication in 11.566 seconds
```
→ **Hibernate validate 통과, 기동 성공.**

팀원이 지목한 컬럼들의 실제 생성 결과
| 테이블 | 컬럼 | 타입 |
| --- | --- | --- |
| `user_setting` | `notification_enabled`, `daily_briefing_enabled`, `monthly_report_enabled` | `tinyint(1) NOT NULL DEFAULT 1` |
| `finance_connection` | `member_email_hash`, `external_user_key_hash` | `char(64)` (UNIQUE) |
| `finance_connection` | `member_email_encrypted`, `external_user_key_encrypted` | `varbinary(512)` |

**결론: `CHAR(64)` / `TINYINT(1)` 매핑 문제는 없다.** 유일한 실제 결함은 상품 테이블 3개 누락이었고, 그건 이 MR이 아니라 앞서 병합된 상품 API MR에서 비롯됐다.

이로써 팀원이 "배포 후에 확인해야 한다"고 남긴 항목을 **배포 전에 해소**했다. 머지 후 첫 기동에서 놀랄 일이 없다.

### 8. 환경변수 준비 상태 확인
MR 설명의 "머지 전 `.env`에 4개 채워야 함" 항목 검증:
- `JWT_SECRET` 62자, `DATA_ENCRYPTION_KEY`/`DATA_HASH_KEY` 각 base64 디코드 32바이트(AES-256), `SSAFY_FINANCE_API_KEY` 32자 — 모두 설정 완료
- **MR의 compose 파일을 기준으로 `check-env.sh` 시뮬레이션 → 필수 변수 11개 전부 통과**
  (조치 전에는 `SSAFY_FINANCE_API_KEY` 누락을 정확히 검출했었다)

## 이 장애에서 드러난 구조적 문제 (개선 항목)

1. **배포 실패가 조용하다** — 파이프라인은 실패를 감지했지만 알림이 없어 아무도 몰랐다.
   Day 6 모니터링에서 "대시보드보다 알림이 우선"이라고 정한 판단이 실제 사례로 뒷받침됐다.
2. **실패한 배포가 깨진 컨테이너를 그대로 남긴다** — `deploy.sh`는 헬스체크 실패 시 로그만 남기고 종료한다.
   그래서 이전의 정상 컨테이너가 이미 교체된 뒤라 서비스가 계속 죽어 있었다.
   → Day 5의 "배포 실패 시 롤백" 항목이 선택이 아니라 필수임이 확인됐다.
3. **스키마와 엔티티가 따로 병합될 수 있다** — 코드 리뷰만으로는 걸러지지 않는다.
   CI에서 "마이그레이션 적용 후 validate 통과"를 검증하는 단계가 있으면 머지 전에 막을 수 있다.

## 팀에 전달할 사항 (사용자 액션)

- **[백엔드] 누락 테이블 3개의 마이그레이션 필요**: `financial_company`, `financial_product`, `financial_product_option`
  - DDL은 `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`에 이미 있음
  - 이 테이블이 생겨야 `backend/database/seeds/`의 금융상품 시드도 적재 가능
  - 마이그레이션이 들어오면 `SPRING_JPA_HIBERNATE_DDL_AUTO: none`을 제거하고 `validate`로 복귀할 것
- **[Auth MR 관련] `SSAFY_FINANCE_API_KEY` 값 필요** — 현재 비어 있음.
  Auth MR이 머지되면 `check-env.sh`가 이 값이 없다는 이유로 배포를 명시적으로 중단시킨다(의도된 동작)
- **[시크릿 백업] `backup-secrets.sh`를 한 번 실행해 암호화 백업을 로컬로 내려받고 서버 사본은 삭제할 것**
  - `DATA_ENCRYPTION_KEY` / `DATA_HASH_KEY`를 잃으면 DB 백업이 있어도 암호화 컬럼을 복구할 수 없다

## 다음 기록할 것 (TODO)
- [x] 백업/복구 스크립트 및 cron
- [x] 복구 리허설
- [x] 암호화 키 생성 및 형식 검증
- [x] 시크릿 백업 절차 및 검증
- [x] 배포 전 `.env` 사전 검사
- [x] 머지 전 사전 검증으로 누락 테이블 특정
- [x] 운영 장애 복구
- [ ] 마이그레이션 반영 후 `ddl-auto`를 validate로 되돌리기
- [ ] Redis 사용 범위 확정 (캐시 / 세션 / 분산 락) — 백엔드팀 확인 필요
- [ ] MySQL `innodb_buffer_pool_size` 튜닝 (현재 기본 128MB)
- [ ] 시드 데이터 적재 (테이블 생성 이후)
