-- 본인 계좌 간 자금 이동은 원장과 통장 거래내역에 보존하고 가계부·소비 분석에서만 제외한다.
ALTER TABLE `transaction`
    ADD COLUMN `internal_transfer` TINYINT(1) NOT NULL DEFAULT 0;

-- 기존 LOCAL 거래번호는 도담의 계좌 간 이체에서만 생성된다. 금융망 거래는 재동기화 시 상대 계좌 해시로 판별한다.
UPDATE `transaction`
SET `internal_transfer` = 1
WHERE `transaction_id` LIKE 'LOCAL-%'
  AND `account_id` IS NOT NULL
  AND `source_type` = 'SSAFY'
  AND `spending_type` = 'NON_SPENDING'
  AND `summary` IN ('계좌 이체', '계좌 이체 입금');
