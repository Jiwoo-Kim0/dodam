-- Jira: S15P21D105-626 (목표별 입출금 계좌 할당, 입출금 1 : 목표 N)
-- Schema basis: V8 goal_account. 기획 변경 2026-09-16 확정(backend/docs/goal-allocation-626-plan.md).
--
-- 범위: 한 계좌 = 한 목표 제약(`uk_goal_account_account`)을 예금·적금에만 남기고, 입출금(DEMAND_DEPOSIT) 계좌는
-- 여러 모으기 목표에 금액을 나눠 담을 수 있게 한다. 컬럼 추가·데이터 변경은 없다.
--
-- `contribution_amount` 의미(626부터):
--   - DEPOSIT·SAVINGS: 항상 NULL. 잔액 전체가 연결 목표의 확보액이다.
--   - DEMAND_DEPOSIT: 그 목표에 담은 금액. NULL이면 626 이전 방식(잔액 전체)으로 읽는다.
--     기존 행과 시연 seed(DODAM_GOAL_SEED.sql)의 NULL은 그대로 유효하며 사용자가 처음 담기/꺼내기를 하면 숫자로 바뀐다.
--     따라서 입출금 non-null CHECK를 추가하지 않는다.
--
-- 조건부 UNIQUE: MySQL은 부분 인덱스가 없으므로 예·적금일 때만 account_id, 입출금이면 NULL인 생성 컬럼에 UNIQUE를 건다
-- (NULL은 UNIQUE 중복 검사에서 제외). JPA 엔티티는 이 컬럼을 매핑하지 않는다(읽기·쓰기 모두 DB가 계산).
-- H2 테스트 스키마는 엔티티 기준(ddl-auto)이라 이 제약이 없고, 서비스 검증(ACCOUNT_ALREADY_LINKED_TO_GOAL)이 먼저 막는다.
-- DB 단계 제약은 *MySqlIntegrationTest로 확인한다.
--
-- `fk_goal_account_account`(user_id, account_id, account_type)는 `idx_goal_account_user_account`가 받치므로
-- account_id 단독 UNIQUE를 지워도 외래키 인덱스 요건을 만족한다.

ALTER TABLE `goal_account`
  DROP INDEX `uk_goal_account_account`;

ALTER TABLE `goal_account`
  ADD COLUMN `exclusive_account_id` BIGINT
    GENERATED ALWAYS AS (IF(`account_type` IN ('DEPOSIT', 'SAVINGS'), `account_id`, NULL)) VIRTUAL
    COMMENT '626: 예·적금이면 account_id, 입출금이면 NULL. 계좌당 목표 1개 제약을 예·적금에만 적용하기 위한 생성 컬럼';

ALTER TABLE `goal_account`
  ADD UNIQUE KEY `uk_goal_account_exclusive_account` (`exclusive_account_id`);
