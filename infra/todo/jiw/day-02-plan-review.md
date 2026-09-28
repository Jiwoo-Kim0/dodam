# 2026-09-07 — 레포 문서 전면 재검토 및 Day 3~8 계획 갱신

Day 2 완료 시점에, `docs/`와 다른 폴더의 내용을 다시 읽고 앞으로의 인프라 계획에 반영할 것이 있는지 점검함.

## 읽은 문서
- `docs/dev_plan.md` (팀 전체 12일 일정표)
- `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql` (물리 스키마 1250줄, 49개 테이블)
- `backend/docs/init_settings_prompt.md` (백엔드 초기 설정 지침, 문서 간 미결 사항 목록 포함)
- `backend/src/main/resources/db/migration/README.md` (Flyway 방침 + 데이터 무결성 규칙)
- `backend/build.gradle`, `application*.yml`
- `backend/database/finance_products/` (금융상품 시드 SQL)
- `frontend/app.json`, `frontend/package.json`

## 발견 1 — 팀 일정은 8일이 아니라 12일이고, Day 9부터 담당이 바뀐다
`dev_plan.md`의 김지우 열:

| 구간 | 담당 |
| --- | --- |
| Day 1~8 | 인프라 |
| Day 9~12 | **백엔드 기능 개발** — Dashboard, AI Coach, Improvement/Report, Report 완료·통합 |

- 함의: **Day 8 이후에는 인프라에 쓸 시간이 없다.** 인프라를 "손이 덜 가는 상태"로 만들어두는 것이 8일 스프린트의 실질 목표가 됨
- 반영: `00-overview.md`에 "1-1. 팀 전체 일정과의 관계" 절 신설. Day 6은 축소하더라도 **알림(컨테이너 다운/디스크 임계치)은 남기기**로 방침 변경 — 대시보드보다 알림이 우선
- 반영: Day 8 문서 상단에 "인프라에 시간을 쓸 수 있는 마지막 날"임을 명시
- 추가 확인: FE가 **Day 4에 로그인/금융데이터 연동**을 시작 → API BaseURL과 CORS는 Day 3 종료 시점까지 정리되어야 함

## 발견 2 — ERD가 갱신되면서 민감정보가 애플리케이션 레벨 암호화로 바뀜
`account_no_encrypted`, `card_no_encrypted`, `fcm_token_encrypted`, `provider_email_encrypted`, `member_email_encrypted`, `external_user_key_encrypted` 등이 **`VARBINARY` + 조회용 `_hash CHAR(64)`** 조합으로 정의됨.

- 인프라 관점의 함의: **암호화 키를 잃으면 DB 백업이 있어도 데이터를 복구할 수 없다**
- 반영
  - `00-overview.md` 미확인 표에 "민감정보 암호화 키 관리 방식" 항목 신설
  - Day 5 시크릿 표에 **데이터 암호화 키** 신규 항목 추가 (아직 미생성)
  - Day 4 백업 항목에 "키를 DB 덤프와 **함께, 그러나 다른 위치에** 백업" 명시
  - Day 8 복구 리허설에 "키 복구 단계 포함" 추가
- 시급성: 실데이터가 쌓이기 전에 키를 확정해야 하는데 **FE 연동이 Day 4에 시작**되므로 시간이 촉박함

## 발견 3 — `init_settings_prompt.md`의 미결 사항 중 일부가 이미 해소됨
그 문서는 "ERD에 OAuth 모델·FCM Device Token·권한 모델이 없다"고 적고 있으나, 갱신된 ERD에는 전부 존재함.

| 항목 | 문서상 미결 | 실제 ERD |
| --- | --- | --- |
| OAuth 사용자 식별 | 없음 | `user_oauth_account` (provider, provider_user_id_hash, …) |
| FCM Device Token | 없음 | `user_device` (fcm_token_encrypted, platform, enabled, …) |
| 관리자 권한 모델 | 없음 | `user.role` + `CHECK (role IN ('USER','ADMIN'))` |
| 민감정보 암호화 | ERD는 평문 | `VARBINARY` 암호화 컬럼으로 전환됨 |

- 함의: **소셜로그인/FCM은 "할지 미정"이 아니라 진행 전제로 확정**됨 → Day 7 계획의 불확실성이 줄었고, 대신 Day 5 암호화 키가 선행 조건이 됨
- 반영: Day 7 문서 상단에 이 전제 변화를 명시

## 발견 4 — Flyway 방침 확정, 마이그레이션은 아직 0개
- `build.gradle`에 `flyway-core` + `flyway-mysql`, `application-prod.yml`에 `flyway.enabled: true`, `ddl-auto: validate`
- `db/migration/`에는 README만 있고 마이그레이션 파일 없음 — "엔티티/물리 스키마 확정 전에는 추가하지 않는다"는 명시적 방침
- 즉 **인프라가 "빈 DB + 계정"만 보장하고 스키마는 앱이 적용**하는 기존 계획 방향이 그대로 맞음 (Day 4의 "협의 필요" 항목 → **확정**으로 변경)
- 현재 배포된 DB는 `flyway_schema_history`만 있는 빈 상태이며, 이는 정상 (빌드 #3 로그에서 "No migrations found" 경고 확인됨)

## 발견 5 — 금융상품 시드 SQL이 새로 들어옴 (계획에 없던 항목)
`backend/database/finance_products/`에 누적본 591KB + 카드 127KB.

- 적재 경로가 정해져 있지 않음 → Day 4에 신규 체크리스트 항목으로 추가
- 주의점 기록: `mysql/init/`(docker-entrypoint-initdb.d)는 **볼륨이 비어 있을 때 최초 1회만** 실행되므로, 이미 볼륨이 생성된 지금은 이 경로로는 동작하지 않음
- 591KB 단일 SQL 적재 시 `max_allowed_packet` 확인 필요

## 발견 6 — FE는 Expo 웹 정적 빌드가 가능함
`frontend/app.json`에 **`web.output: "static"`**, Expo SDK 57 / RN 0.86 / expo-router.

- `expo export --platform web` 산출물을 **이미 떠 있는 nginx가 `/`에서 서빙**하면 별도 호스팅이 필요 없음
- 현재 `/`는 임시 텍스트 200을 반환 중 → Day 3에서 라우팅 방침을 정하고, Day 7에서 빌드 파이프라인을 붙이는 흐름으로 연결
- `expo-device`는 있으나 **`expo-notifications`는 아직 없음** → FCM 토큰 발급 흐름 붙일 때 필요함을 FE팀에 공유할 항목으로 기록

## 발견 7 — 모니터링 선행 조건이 백엔드에 아직 없음
- `build.gradle`에 **`micrometer-registry-prometheus` 없음**
- `application.yml`의 `management.endpoints.web.exposure.include`가 **`health`만** 열려 있음
- 즉 지금 상태로는 `/actuator/prometheus` 수집 불가 → Day 6 문서에 백엔드팀 요청 항목을 두 가지로 구체화하고, actuator를 외부에 노출하지 않도록 nginx에서 차단할 것도 함께 기록

## 발견 8 — Redis 사용 범위가 여전히 미확정
- `init_settings_prompt.md`에 "캐시 / 세션 / 분산 락 중 실제 적용 범위 미확정"으로 명시
- Day 2에서는 일단 **AOF 켠 상태**로 구성해둠 → 순수 캐시라면 꺼도 되고, 세션/락이면 유지해야 함
- 반영: `00-overview.md` 미확인 표에 추가, Day 4에 확정 항목으로 편입 (`maxmemory-policy`도 용도에 따라 갈림)

## 그 밖에 Day 2 결과로 갱신한 것
- `00-overview.md` 미확인 표
  - GitLab Runner 등록 가능 여부 → **불필요해짐** (Jenkins로 성공)
  - 백엔드 코드/Dockerfile 존재 여부 → **해소됨**
  - 외부 포트 → **80은 확인, 443은 여전히 미검증** (Day 3에서 SSL 붙이기 전 먼저 확인해야 함을 명시)
- Day 3/4/5 문서 상단에 "Day 2에서 이미 끝난 것" 목록을 넣어 재작업 방지
  - Day 3: `/api/` 프록시, `/jenkins/` 프록시, 포트 노출 정리 완료
  - Day 4: MySQL/Redis 컨테이너·영속화·계정 분리 완료 → **백업/복구와 운영 설정 확정**으로 범위 재정의
  - Day 5: Dockerfile·파이프라인·헬스체크·재시작 정책 완료 → **시크릿 관리와 배포 안정화(롤백·JVM 메모리)** 로 범위 재정의
- Day 3에 HTTPS 후속 작업 명시: **webhook URL을 https로 바꾸고 SSL verification 재활성화**, Jenkins URL 설정 변경, CORS 갱신
- Day 8에 경고 추가: `down`은 jenkins도 내리므로 **호스트에서 직접** 실행할 것, **`down -v` 절대 금지**(mysql_data/jenkins_home 소실)

## 결론
Day 3~8 계획의 **골격은 유지**되지만, Day 2의 초과 달성으로 Day 4·5의 절반가량이 이미 끝났고 그 자리를 **백업/복구, 시크릿(특히 암호화 키), 배포 롤백**이 채우게 됨. 새로 생긴 가장 중요한 리스크는 **데이터 암호화 키 관리**이며, FE 연동이 시작되는 Day 4 전에 확정되어야 한다.
