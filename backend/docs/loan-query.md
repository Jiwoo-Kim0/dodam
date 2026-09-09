# 대출 조회·상환일정 API 구현 기준 (S15P21D105-531)

## 상태와 근거

2026-09-09 기준 목록·요약·상세·상환내역·상환일정 API 5개를 구현했다.

- 로컬 Jira 내보내기의 531 범위: 대출 목록·요약·상세·상환내역·일정.
- API 기준: `docs/API명세서/10 Loan 3cdbb6ea3564803eb613df84c254cb7e.csv` (일반 CSV).
- 저장 기준: `docs/ERD/DODAM_ERDCLOUD_IMPORT.sql`의 `loan`, `loan_repayment`.
- 기획 기준: 기획안의 대출 항목(총 대출잔액, 대출별 잔액, 금리, 대출유형, 월 상환액, 상환 방식).
- 착수 시 원격 확인 결과: `origin/S15P21D105-531-api-10-loan`은 최신 `develop`(`a012a0e`) 기준으로
  생성돼 있었다. 529와 530은 미병합 상태다.

같은 API 10의 부담·이자 계산은 532, 금리비교·상환우선순위는 533 범위이므로 이 문서에 포함하지 않는다.
532 구현 계약과 제한은 [loan-analysis.md](loan-analysis.md)를 참고한다.

## 인증과 공통 응답

모든 API는 인증이 필요하다. `AuthenticatedUserIdResolver`로 본인 ID를 얻고 `loan.user_id`
소유권 조건을 조회에 포함한다. 공통 `ApiResponse`와 상환내역의 `PageResponse`를 사용한다.

`{accountNo}`는 숫자 10~20자리의 평문 계좌번호를 받아 `SensitiveNumberHasher`로 HMAC 처리한 뒤
`account_no_hash`로 조회한다. 자릿수 범위는 은행별 차이를 흡수하려고 넓게 두었고 실제 소유 여부는
해시 조회로 판별한다. 응답에는 `account_no_masked`만 포함하며 원문과 암호문은 노출하지 않는다.

타인 대출과 존재하지 않는 대출은 모두 `RESOURCE_NOT_FOUND`로 처리해 리소스 존재 여부를 노출하지
않는다. 인증 실패는 기존 401 흐름을 따른다.

## 엔드포인트와 입력

| Method | Endpoint | 입력 및 기본값 |
| --- | --- | --- |
| GET | `/api/v1/loans` | `status?`, `loanType?` |
| GET | `/api/v1/loans/summary` | 없음 |
| GET | `/api/v1/loans/{accountNo}` | 숫자 10~20자리 |
| GET | `/api/v1/loans/{accountNo}/repayments` | `startDate?`, `endDate?`, `page=0`, `size=20` (1~100) |
| GET | `/api/v1/loans/{accountNo}/repayment-schedule` | 없음 |

- `status`는 `ACTIVE`, `OVERDUE`, `COMPLETED`, `CLOSED`, `loanType`은 `CREDIT`, `MORTGAGE`,
  `JEONSE`, `AUTO`, `OTHER`만 허용하며 대소문자를 가리지 않는다. 생략하면 전체를 조회한다.
- 알 수 없는 상태·유형은 `INVALID_REQUEST`, 페이지·크기 제약 위반은 `VALIDATION_ERROR`다.
- 상환내역 기간은 공통 `QueryPeriodPolicy`를 사용한다. 기본 3개월, 최대 12개월이며 시작일이
  종료일보다 늦거나 종료일이 오늘 이후면 `INVALID_REQUEST`다.

## 정렬

- 목록: 상태 우선순위 -> `interest_rate DESC` -> `loan_id ASC`. 상태 우선순위는
  `ACTIVE`, `OVERDUE`, `COMPLETED`, `CLOSED` 순이며 `LoanStatus`의 선언 순서와 같다.
  상환 중인 대출과 연체를 먼저 보여주고, 같은 상태에서는 금리가 높은 대출을 위로 올린다.
  고금리 부채를 먼저 확인하려는 기획 의도를 따랐다.
  상태를 문자열로 저장하므로 DB 정렬에 맡기면 사전순(`ACTIVE`, `CLOSED`, `COMPLETED`,
  `OVERDUE`)이 되어 연체가 종료된 대출보다 뒤로 밀린다. 그래서 정렬은 조회 후 메모리에서
  수행한다. 목록은 사용자별 보유 건수라 이 방식으로 충분하다.
- 상환내역: `repayment_date DESC, loan_repayment_id DESC`.

## 요약 계산

잔액이 남은 대출(`ACTIVE`, `OVERDUE`)만 집계한다. `COMPLETED`, `CLOSED`는 제외한다.

| 필드 | 계산 |
| --- | --- |
| `loanCount` | 집계 대상 건수 |
| `totalOriginalPrincipal` | 원금 합계 |
| `totalBalance` | 잔액 합계 |
| `monthlyRepayment` | 대출별 다음 회차 상환액(상환일정 계산기 결과)의 합 |
| `weightedAverageRate` | 잔액 가중평균 금리 = Σ(잔액 x 금리) / Σ잔액, 소수 4자리 반올림 |
| `nextRepaymentDate` | 가장 이른 `next_repayment_date` |
| `loanTypes` | 대출 유형별 건수와 잔액 |

금리는 단순 평균이 아니라 **잔액 가중평균**이다. 소액 고금리 대출이 평균을 왜곡하면 뒤따르는 533의
고금리 판단 기준과 어긋나기 때문이다. 집계 대상이 없어 잔액 합계가 0이면 `weightedAverageRate`는
null이다.

## 상환일정 계산

`loan_repayment`는 실제 상환 내역만 보관하므로 향후 일정은 저장값이 아니라 계산값이다.
`LoanRepaymentScheduleCalculator`가 현재 잔액, 연이율, 상환일, 만기일, 상환 방식으로 계산한다.
요약의 월 상환액은 첫 회차만 필요하므로 `calculateNext`로 1회차만 계산한다. 만기가 긴 대출의 남은
회차를 모두 만들지 않기 위한 경로다.

월이율 r = 연이율 / 100 / 12이고 회차별 이자는 직전 잔액 x r을 원 단위 반올림(HALF_UP)한다.
금액은 원 단위 `long`이고 비율 계산에만 `BigDecimal`을 사용한다.

| 상환 방식 | 계산식 |
| --- | --- |
| `EQUAL_PRINCIPAL` | 회차 원금 = 잔액 / 남은 회차, 이자 = 직전 잔액 x r |
| `EQUAL_PAYMENT` | 월 상환액 A = P x r x (1+r)^n / ((1+r)^n - 1), 이자 = 직전 잔액 x r, 원금 = A - 이자 |
| `BULLET` | 매 회차 이자만, 마지막 회차에 원금 전액 |

- 첫 회차는 `next_repayment_date`가 있으면 그 날짜, 없으면 `repayment_day` 기준으로 기준일 이후
  가장 빠른 날짜다. 둘 다 없으면 회차를 특정할 수 없어 빈 일정을 반환한다.
- 마지막 회차는 반올림 누적 오차를 흡수하도록 남은 잔액 전액을 원금으로 상환한다.
- 31일처럼 해당 월에 없는 상환일은 그 달의 말일로 보정한다. 532에서 약정 상환일을 보존하도록
  보완해 첫 회차가 2월 말일이어도 이후 31일 약정을 복원한다. 약정일이 없을 때만 첫 회차 일자를 쓴다.
- 약정일로 추론한 첫 회차가 만기 뒤면 만기일 최종 1회로 보정한다. 저장된 다음 상환일이 만기 뒤면
  빈 일정이다. 마지막 회차 날짜도 만기를 넘지 않는다. 단축된 회차에도 기존 월이율 모형을 적용한다.
- 회차 수는 첫 회차 월부터 만기 월까지이며 양끝을 모두 포함한다.
- 만기일이 없거나 이미 지났거나 잔액이 0이면 빈 일정을 반환한다. 값을 추정하지 않는다.
- 최대 360회까지만 반환하고 초과하면 `truncated`가 true다. 전체 만기와 별개인 응답 상한이다.
  `totalPrincipal`과 `totalInterest`는 반환한 회차만 더한 값이다.
- 무이자(`interest_rate = 0`)면 이자는 0이고 원금만 상환한다.

기준일은 요청 시점의 Asia/Seoul 날짜다.

## 구현 경계와 데이터 출처

명세상 목록·상세·상환내역의 데이터 출처는 SSAFY 금융망이지만 현재 `SsafyFinanceClient`에는 회원
생성·조회만 있다. 카드 사용내역과 같은 방식으로 상환내역을 `LoanRepaymentReader` 경계 뒤에 두고
로컬 `loan_repayment`에서 읽는다. 외부 연동이 붙으면 구현만 교체한다
(`TODO(S15P21D105-531)`). 대출 기본 정보는 `loan` 테이블을 JPA로 조회한다.

출금계좌(`withdrawal_account_id`)는 명세와 기획안의 대출 조회 항목에 없어 연관관계로 매핑하지 않고
식별자만 둔다. 응답에도 노출하지 않는다. 필요해지면 Account 도메인 구현과 함께 다시 검토한다.

상세의 `repaidPrincipal`은 ERD가 상환 원금을 따로 보관하지 않아 원금과 잔액의 차이로 계산한다.

## 스키마

`V13__create_loan_and_repayment.sql`에서 `loan`, `loan_repayment`를 생성한다. `loan`은 ERD대로
`financial_product`와 `account`를 참조하므로 529 브랜치의 `V3`(`account` 선반영)와 `V10`
(금융상품) 이후에 적용된다. 즉 529를 먼저 병합해야 한다. `card`에는 의존하지 않아 530과의 병합
순서 제약은 없다. `account_no_hash`는 `CHAR(64)`이므로 엔티티에 `columnDefinition`을 명시했다.

`account`는 BE-A 소관(S15P21D105-501)이지만 이미 병합된 카드 도메인이 매핑하고 있어 develop이
기동하지 못하는 상태였고, 담당자 부재로 529에서 스키마만 선반영했다. 계좌 조회 API는 501에 그대로
남으며 같은 테이블을 다시 만들지 않도록 인수인계가 필요하다.

## 검증

- H2 통합 테스트: 목록·필터·요약·상세·상환내역·상환일정의 정상 흐름, 입력 검증, 인증 실패,
  소유권 격리, 빈 결과, 페이지 경계를 확인했다. 상환일정 단위 테스트는 세 가지 상환 방식과 말일
  보정, 무이자, 만기 경과, 회차 상한을 포함한다.
- MySQL 8.0 일회용 컨테이너: `V13` 적용, 상환내역 SQL `PREPARE`, 엔티티 `ddl-auto: validate`
  통과와 정상 기동을 확인했다(2026-09-09). 검증 과정에서 `card`의 `CHAR` 매핑 문제(529에서 수정)를
  우회하려고 검증 DB의 card 컬럼만 임시로 변경했으며 저장소 코드에는 반영하지 않았다.
- 미검증: 시연 데이터를 적재하지 못해 MySQL에서의 데이터 기준 동작은 확인하지 못했다. 시드 적재
  경로 확정과 암호화 픽스처 생성기가 선행 조건이다.
