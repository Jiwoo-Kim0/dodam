-- Jira: S15P21D105-680 (4,000명 합성 개인신용점수)
--
-- `credit_rating`은 SSAFY 금융망의 자산 기준 A~E 등급으로 남긴다. 아래 컬럼은 공개된
-- 개인신용평가 영역을 도담 보유 데이터에 적용한 1~1,000점 예상 점수다. 실제 KCB/NICE
-- 조회값과 혼동하지 않도록 출처와 산식 버전을 함께 저장한다.
ALTER TABLE `user`
  ADD COLUMN `credit_score` SMALLINT NULL AFTER `credit_rating_updated_at`,
  ADD COLUMN `credit_score_source` VARCHAR(30) NULL AFTER `credit_score`,
  ADD COLUMN `credit_score_model_version` VARCHAR(30) NULL AFTER `credit_score_source`,
  ADD COLUMN `credit_score_updated_at` DATETIME NULL AFTER `credit_score_model_version`,
  ADD CONSTRAINT `chk_user_credit_score`
    CHECK (`credit_score` IS NULL OR `credit_score` BETWEEN 1 AND 1000),
  ADD CONSTRAINT `chk_user_credit_score_source`
    CHECK (`credit_score_source` IS NULL OR `credit_score_source` IN ('SYNTHETIC', 'KCB', 'NICE')),
  ADD CONSTRAINT `chk_user_credit_score_metadata`
    CHECK ((`credit_score` IS NULL
            AND `credit_score_source` IS NULL
            AND `credit_score_model_version` IS NULL
            AND `credit_score_updated_at` IS NULL)
        OR (`credit_score` IS NOT NULL
            AND `credit_score_source` IS NOT NULL
            AND `credit_score_updated_at` IS NOT NULL
            AND (`credit_score_source` <> 'SYNTHETIC'
                 OR (`credit_score_model_version` IS NOT NULL
                     AND TRIM(`credit_score_model_version`) <> ''))));
