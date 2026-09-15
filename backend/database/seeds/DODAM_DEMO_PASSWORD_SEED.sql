-- DODAM Demo password seed (시연 사용자 U0001~U0004 로그인 비밀번호)
-- Jira: S15P21D105-521
-- Dialect: MySQL 8.x
-- Schema basis: backend/src/main/resources/db/migration/V1__create_user_and_auth_session.sql (`user.password_hash`)
-- Dependency: 491 User seed (seed.kmeans.u0001~u0004@dodam.invalid 행이 있어야 한다)
-- Snapshot: 2026-09-15
--
-- What this file does
-- -------------------
-- 1. 491 seed는 모든 사용자의 password_hash를 NULL로 두므로 API 로그인이 불가하다. 이 파일은 시연 사용자 4명에게만
--    같은 시연 비밀번호의 BCrypt 해시를 넣어 `POST /api/v1/auth/login`이 가능하게 한다. 나머지 3,996명은 그대로 NULL이다.
-- 2. 시연 비밀번호는 backend/database/seeds/README.md "시연 계정 로그인" 절에 있다(회원가입 규칙: 8~72자, 영문+숫자, 공백 없음).
--    해시는 애플리케이션과 같은 BCryptPasswordEncoder(`$2a$`, cost 10)로 만들었다. 비밀번호를 바꾸려면 해시만 다시 만들어 교체한다.
-- 3. 재실행은 안전하다(같은 값으로 UPDATE). 탈퇴(deleted_at) 사용자는 건너뛴다.
--
-- 시연 계정에 한정된 값이고 실제 사용자 계정에는 절대 적용하지 않는다. 시연 종료 후 되돌리려면 같은 조건으로 password_hash = NULL.
--
SET NAMES utf8mb4;

UPDATE `user`
SET password_hash = '$2a$10$qUPzuw/2k/nAfXV87ezt0egmN7UZMMb7ZCc3LjBubpwEgWXGYPtsq',
    updated_at = NOW()
WHERE email IN (
  'seed.kmeans.u0001@dodam.invalid',
  'seed.kmeans.u0002@dodam.invalid',
  'seed.kmeans.u0003@dodam.invalid',
  'seed.kmeans.u0004@dodam.invalid'
)
AND deleted_at IS NULL;

-- 검증: 시연 사용자 4명은 해시가 있고(demo_users_with_password = 4), 그 외 사용자에는 해시가 없다(other_users_with_password = 0)
SELECT
  (SELECT COUNT(*) FROM `user`
    WHERE email IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                    'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid')
      AND password_hash IS NOT NULL) AS demo_users_with_password,
  (SELECT COUNT(*) FROM `user`
    WHERE email LIKE 'seed.kmeans.u%@dodam.invalid'
      AND email NOT IN ('seed.kmeans.u0001@dodam.invalid', 'seed.kmeans.u0002@dodam.invalid',
                        'seed.kmeans.u0003@dodam.invalid', 'seed.kmeans.u0004@dodam.invalid')
      AND password_hash IS NOT NULL) AS other_users_with_password;
