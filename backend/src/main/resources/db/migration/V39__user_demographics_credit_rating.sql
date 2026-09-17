-- Jira: S15P21D105-629 (User 나이·성별·신용등급 추가)
-- Schema basis: V1 user.
--
-- 범위: 사용자 기본정보에 생년월일·성별·금융망 신용등급을 둔다.
--   * birth_date — 나이는 매년 바뀌므로 정수가 아니라 생년월일을 저장하고 API 응답에서 age를 계산한다.
--   * gender — MALE / FEMALE. CHECK는 NULL을 통과시키므로 미입력 회원은 그대로 NULL이다.
--   * credit_rating — SSAFY 금융망 2.7.4 「내 신용등급 조회」의 ratingName(A~E). 사용자가 입력하지 않고
--     GET /finance-data/credit-rating 조회와 POST /finance-data/sync가 성공할 때 서버가 갱신한다.
--     금융망은 신용점수(숫자)를 제공하지 않으므로 등급명만 저장한다. 갱신 시각은 credit_rating_updated_at.
--
-- NULL 허용: 기존 회원·seed 사용자·소셜 가입자는 값이 없다. seed(491)는 컬럼을 명시한 INSERT라 영향이 없다.

ALTER TABLE `user`
  ADD COLUMN `birth_date` DATE NULL AFTER `name`,
  ADD COLUMN `gender` VARCHAR(10) NULL AFTER `birth_date`,
  ADD COLUMN `credit_rating` VARCHAR(5) NULL AFTER `gender`,
  ADD COLUMN `credit_rating_updated_at` DATETIME NULL AFTER `credit_rating`,
  ADD CONSTRAINT `chk_user_gender`
    CHECK (`gender` IN ('MALE', 'FEMALE'));
