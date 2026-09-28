## 📌 작업 목적
- 목표 상세에서 "5,053일 늦어요 / 예상 달성일 2041.08.22"가 나왔다. 계산은 정확했지만(남은 17,900,000 ÷ 저축속도 100,000 = 179개월) 완료 월 3개월 치 거래로 잰 속도를 15년 뒤 날짜로 하루 단위까지 단정하는 구조였다.
- 626이 확보액에서 막아 둔 "같은 돈이 두 목표에 중복 합산되지 않는다"가 694에서 따로 생긴 저축속도에는 없었다. 한 입출금 계좌를 두 목표에 담으면 두 목표가 같은 증가분을 각자 전액 센다. 현재 시연 계정에는 공유 입출금이 없어 드러나지 않았지만, 입출금 1 : 목표 N은 V35(626)로 이미 열려 있다.
- 세이프박스(목표 담기·꺼내기)의 1단계다. 계획은 [safebox-allocation-plan.md](backend/docs/safebox-allocation-plan.md).

## 🛠️ 작업 내용

**저축속도 목표별 안분 (`GoalSavingPaceReader.shareOf`)**
- 입출금 계좌의 순현금흐름에 `이 목표의 기여액 / 그 계좌의 총 할당액`을 곱한다. 확보액(626)과 같은 기준이다.
- 담은 금액이 아직 없으면(`contribution_amount` NULL) 각 목표의 기여액이 잔액 전체라 몫이 `1/연결 목표 수`, 즉 균등 분할이 된다. 폴백을 따로 두지 않았다.
- 총 할당액이 0이거나 이 목표가 전부를 차지하면 전액으로 본다 — **몫이 모두 1이면 702까지와 같은 값이다.**
- 계좌별로 나눈 뒤 합하지 않고, 몫을 곱해 합한 뒤 개월 수로 한 번만 나눈다(기존 값과의 일치를 위해).
- `순현금흐름 × 기여액`이 long 범위를 넘을 수 있어 중간 곱만 `BigInteger`로 계산한다.

**계좌별 순현금흐름 조회 (`JdbcGoalPaceReader.readNetCashflowByAccount`)**
- 합계 반환 → 계좌별 `Map` 반환. 카드 승인은 대금이 빠지는 출금 계좌 몫으로 돌린다(702 귀속 규칙 유지).
- 귀속 식을 파생 테이블에서 한 번만 계산한다. SELECT와 GROUP BY에 같은 `CASE` 식을 두 번 쓰면 `:accountIds`가 서로 다른 파라미터로 펼쳐져 H2가 같은 식으로 보지 않는다.
- `readFirstTransactionDate`와 `LINKED_ACCOUNT_CONDITION`을 계속 공유해 두 조회의 범위가 어긋나지 않게 뒀다.

**예상 달성일 상한 (`GoalCalculationService.beyondForecastHorizon`)**
- 예상일이 목표일을 `목표 기간(생성일 → 목표일, totalDays)`만큼 더 넘어서면 `expectedAchievementDate`·`monthsToAchieve`·`daysDifference`를 null로 둔다(`onSchedule = false`). 속도 0과 같은 경계로 취급한다.
- 기준을 "남은 기간"이 아니라 "목표 기간"으로 뒀다. 남은 기간은 목표일이 가까울수록 0으로 수렴해 하루 지연까지 지우고, 목표일이 지나면 어떤 예측도 낼 수 없다.
- 공유 식 `GoalForecastFormula`가 아니라 파생값 조립 단계에 뒀다. 식에 넣으면 시뮬레이션 before/after가 한 축에서 어긋나지 않게 맞춰 둔 658의 보장이 흔들린다.
- 목표일 안에 들어오는 예측(조기 달성·소폭 지연)은 그대로 낸다.

**문서**
- `goal.md` 저축속도 정의에 703 안분 규칙, 예상 달성일에 상한 규칙 추가.
- 문서 부채 3건 정정: `goal.md`에 남아 있던 694 이전 속도 정의 문장, 마이그레이션 README의 "한 Account는 하나의 Goal에만 연결" 규칙(V35가 이미 뒤집음), 구간표 "다음은 V44"(V44는 697 사용 → **V45**).
- 세이프박스 구현 계획([safebox-allocation-plan.md](backend/docs/safebox-allocation-plan.md))과 FE 인계 문서([safebox-fe-handover.md](backend/docs/safebox-fe-handover.md)) 추가.

## 🔍 리뷰 포인트
- **안분 몫의 경계 처리.** `allocatedTotal <= 0`(잔액 0 계좌)과 `contribution >= allocatedTotal`(단독 연결)을 전액으로 본 것이 맞는지. 702까지의 값을 그대로 유지하려는 선택이다.
- **상한 기준을 "목표 기간"으로 둔 판단.** 자의적 상수(예: 10년)를 피하고 사용자가 정한 값에서 끌어왔지만, 오래전에 만든 목표는 상한이 그만큼 넉넉해진다.
- **상한의 파급.** 진단 스냅샷의 `forecast.daysDifference`도 null이 되므로, 추천 생성기가 "가장 많이 밀린 모으기 목표"를 고를 때 그 목표가 뒤로 간다(`GeneratorSupport.savingGoalForForecast`). 달성 시점을 계산할 수 없는 목표에 소비 추천을 붙이는 것보다 낫다고 판단했으나 의도한 동작인지 확인 필요.
- **파생 테이블 SQL.** 원래 한 번의 집계였던 것이 파생 테이블 + 상관 서브쿼리가 됐다. MySQL에서 실측은 했지만 실행 계획까지는 보지 않았다.

## ✅ 테스트
- [x] 빌드 성공
- [x] 기능 테스트 완료

### 테스트 내용
- `./gradlew test` 전체 통과(5분 13초).
- `GoalSavingPaceReaderTest` — 공유 계좌 몫(균등 분할 1/2, 담은 금액 3:1, 음수 상쇄), 단독 계좌 전액, 총 할당액 0.
- `JdbcGoalPaceReaderIntegrationTest`(H2 MySQL 모드) — 계좌별 분리, 카드 승인의 출금 계좌 귀속, 조회 대상 밖 계좌 제외.
- `JdbcGoalPaceReaderMySqlIntegrationTest`(신규, MySQL 8.0 실측) — 위와 같은 값 확인. 환경변수 `GOAL_PACE_MYSQL_TEST_URL` 게이트라 CI에서는 건너뛴다. 준비 절차는 remaining-work 703 절에 있다(**V6을 V11 뒤로 옮겨** 올리고 Flyway는 끈다. 버전 순서 그대로면 `Failed to open the referenced table 'card'`).
- `GoalCalculationApiIntegrationTest` — 상한 경계 양쪽: 목표 기간 9개월에 5개월 지연은 날짜 유지, 91개월 지연은 null.
- **시연 영향 없음 확인**: 시연 서버 U0001~U0004 모두 SAVING 목표 1개이고 목표에 연결된 입출금 중 `allocatedElsewhere > 0`인 계좌가 0건이다. 몫이 전부 1이라 저축속도가 바뀌지 않는다.

## 📎 참고
- 이슈: S15P21D105-703
- 구현 계획: [safebox-allocation-plan.md](backend/docs/safebox-allocation-plan.md)
- 선행 계약: [goal-allocation-626-plan.md](backend/docs/goal-allocation-626-plan.md) §2·§4·§7
- 계약 문서: [goal.md](backend/docs/goal.md) §저축속도 정의, §예상 달성일
- FE 인계: [safebox-fe-handover.md](backend/docs/safebox-fe-handover.md) — 담기 화면 여력 확인 요청. 추가로 상한 때문에 `daysDifference`가 null인 경우가 늘어나며, 목표 상세의 `getSavingAccent(null)` 문구 '달성일을 계산 중이에요'가 실제 의미("현재 속도로는 목표일까지 어려워요")와 다르다.
- **배포 후 확인**: U0001~U0004의 `GET /goals/{id}/forecast` `averageMonthlySaving`이 유지되는지.
