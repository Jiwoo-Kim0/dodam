-- user.credit_score 타입을 SMALLINT → INT로 맞춘다.
-- Jira: S15P21D105-475 (680 스키마 정합 수정)
-- Dialect: MySQL 8.x
--
-- 왜 필요한가
-- -----------
-- V42가 컬럼을 SMALLINT로 만들었는데 `User` 엔티티는 `Integer creditScore`다.
-- Hibernate의 `ddl-auto: validate`가 두 타입을 다르게 보고 기동을 거부한다:
--   Schema-validation: wrong column type encountered in column [credit_score] in table [`user`];
--   found [smallint (Types#SMALLINT)], but expecting [integer (Types#INTEGER)]
-- 이 때문에 2026-09-22 배포가 사전 검증에서 실패했다(서비스 중단은 없었다. deploy.sh가 롤백함).
--
-- 왜 V42를 고치지 않는가
-- ---------------------
-- V42는 시연 서버에 이미 적용됐다. 파일을 고치면 Flyway 체크섬 검증이 깨져 기동이 막힌다.
-- 적용된 마이그레이션은 수정하지 않고 새 번호로 바로잡는다.
--
-- 대안
-- ----
-- 점수 범위가 1~1000이라 SMALLINT가 도메인에는 더 맞다. 엔티티에
-- `@JdbcTypeCode(SqlTypes.SMALLINT)`를 붙여 컬럼을 그대로 두는 방법도 있다(이 레포는
-- `@JdbcTypeCode(SqlTypes.JSON)`을 이미 쓴다). 다만 그 방식은 MySQL 대상 테스트가 없어
-- 로컬에서 검증할 수 없었고, 이 ALTER는 결과가 자명해 배포를 먼저 풀기 위해 이쪽을 골랐다.
-- 680 담당자가 SMALLINT를 유지하고 싶다면 이 파일을 되돌리는 대신 엔티티 쪽으로 다시 옮기면 된다.
--
-- CHECK 제약(`chk_user_credit_score`: 1~1000)은 컬럼을 참조하지만 MODIFY는 허용된다.
-- 값은 그대로 보존된다(4,000행 적재 완료 상태).

ALTER TABLE `user`
  MODIFY COLUMN `credit_score` INT NULL;
