# 계좌 이체 (S15P21D105-703)

`POST /api/v1/accounts/transfer`는 인증 사용자 소유의 수시입출금 계좌 사이에서 SSAFY 금융망 이체를 실행한다.
사용자에게 [도담 전용 계좌](safebox.md)가 있으면 그 계좌의 출금은 목표에 배정되지 않은 잔액까지만 허용한다.

요청은 `{ "withdrawalAccountId": 11, "depositAccountId": 12, "amount": 500000 }`이고 성공 응답은
`ApiResponse`의 `data`에 `withdrawalAccountId`, `withdrawalBalance`, `depositAccountId`,
`depositBalance`, `transferredAmount`를 담는다. 계좌번호 원문은 응답에 없다.

두 계좌 모두 본인 것이어야 하며, 없는 계좌와 타인 계좌는 같은 404 `RESOURCE_NOT_FOUND`로 처리한다.
같은 계좌, 0원 이하, 수시입출금이 아닌 계좌, 해지 계좌는 400이다. 금융망 연결이 없으면 409
`FINANCE_NOT_CONNECTED`다. 금융망 `A1014`는 409 `TRANSFER_INSUFFICIENT_BALANCE`, `A1016`·`A1017`은
409 `TRANSFER_LIMIT_EXCEEDED`다. 그 외 금융망 오류는 502, 통신 실패나 응답 해석 실패는 503이다.
한도 금액은 계좌마다 다를 수 있어 고정 금액을 안내하지 않는다.

금융망 호출을 끝낸 뒤 사용자 행 쓰기 잠금과 DB 트랜잭션으로 두 잔액 및 거래 두 건을 저장한다.
출금은 `EXPENSE`, 입금은 `INCOME`이며 양쪽 모두 `SAVING` 카테고리와 `NON_SPENDING`을 쓴다.
동기화 경로의 카테고리와 소비 유형 기본값은 기존처럼 NULL이다. 거래 ID는 금융망의 서로 다른
`transactionUniqueNo`를 그대로 사용한다. `last_transaction_date`는 저장 시점의 서비스 날짜로 갱신한다.

금융망 요청의 금액 `transactionBalance`는 문자열로 보낸다. JSON 숫자로 보내면 입금·출금·이체 모두
`Q1001`(요청 본문 형식 오류)로 거절된다. 실제 성공 응답 REC에는 `transactionUniqueNo`·`transactionDate`
(이체는 `transactionType` 추가)만 있고 시각·금액·잔액·적요가 없으므로, 거래 시각은 서비스 시계, 금액은
요청값, 적요는 도담 기본 문구로 채운다. 응답에 금액이 오면 요청값과 대조한다. 2026-09-26 실제 호출로 확인했다.
거래 목록은 별도 조회 API의 정렬과 페이지 정책을 따른다.

동일 사용자·출금계좌·입금계좌·금액의 요청은 프로세스 메모리에서 10초 동안 409
`TRANSFER_DUPLICATE_REQUEST`로 거절한다. 단일 인스턴스 전제의 짧은 중복 방지이며, 프로세스 재시작 후에는
보장하지 않는다. 금융망 성공 뒤 DB 저장이 실패하면 자동 역이체하지 않고 오류를 기록한다. 다음 계좌
조회에서 금융망 잔액을 조회해 두 로컬 잔액을 맞춘다. 실패한 거래 원장 2건은 자동 복구하지 않으므로
별도 대조가 필요하다. 계좌번호 원문과 userKey는 로그에 남기지 않는다.
복구 대상도 프로세스 메모리에서 관리하므로 재시작하면 자동 대조가 이어지지 않는다.

seed·수동 계좌가 낀 이체는 금융망 쪽만 입금(2.4.9)·출금(2.4.8)하고 seed 쪽은 도담 DB에서 반영한다(705, [safebox.md](safebox.md) seed 계좌 이체 규칙). 시연 데이터 생성은 이 변경에 포함하지 않는다. 실제 금융망과 MySQL 전용 제약은
시연 리허설에서 따로 검증해야 한다.
