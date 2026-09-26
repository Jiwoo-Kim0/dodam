-- Jira: S15P21D105-705. 도담 전용 계좌 이름을 '도담 계좌'로 맞춘다.
-- 705 초기 배포가 금융망 상품 유형명(예: 수시입출금)을 계좌 이름으로 저장해 다른 입출금 계좌와 구분되지 않았다.
-- 이후 개설은 코드가 이름을 고정하고, 계좌 동기화도 전용 계좌 이름을 덮지 않는다. 대상은 safebox_account가 가리키는 계좌뿐이다.
UPDATE `account` a
JOIN `safebox_account` s ON s.account_id = a.account_id
SET a.account_name = '도담 계좌'
WHERE a.account_name <> '도담 계좌';
