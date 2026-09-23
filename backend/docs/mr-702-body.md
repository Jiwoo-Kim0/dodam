## 📌 작업 목적
- 모으기 목표 저축속도의 입출금 부분이 **사용자 전체** 순현금흐름을 써서, 목표에 연결하지 않은 계좌의 수입·지출까지 그 목표의 속도에 섞였다. 연결한 계좌 기준으로 한정한다.
- 소비 증가 추천(`REDUCE_CATEGORY_SPENDING`)의 `advancedDays`가 개선 후에도 목표일을 넘는 경우까지 "N일 단축"으로 노출돼, 발견 요약 "목표 N일 단축"이 과장됐다. 목표일 이내일 때만 값을 낸다.

## 🛠️ 작업 내용
- `JdbcGoalPaceReader`: 순현금흐름·첫 거래일 SQL을 연결 입출금 계좌로 한정. 거래의 `account_id`가 연결 계좌이거나 `card_id`가 그 계좌에서 대금이 빠지는 카드(`card.withdrawal_account_id`)인 행만 센다. 카드 대금 결제는 `card_billing_statement`에만 있어 이중 계산 없음. 연결 입출금이 없으면 조회하지 않는다.
- `GoalSavingPaceReader`: 연결된 DEMAND_DEPOSIT 계좌 ID 목록을 넘긴다. 511 이력·546 goal-impact·시뮬레이션 기준선이 같은 정의를 쓴다.
- `CategorySpendingIncreaseGenerator` `696.1 → 702.1`: 개선 예상일이 스냅샷 `forecast.targetDate` 이내일 때만 `advancedDays` 제공. 넘으면 날짜는 유지하고 `advancedDays = null`, 근거 `GOAL_FORECAST_ADVANCE` evidence에 `targetDate`·`goalImpactStatus`(`TARGET_DATE_MET`/`TARGET_DATE_NOT_MET`), 문구 "…이지만 목표일 …보다 늦어요." 과거 스냅샷에 목표일이 없으면 기존 계산 유지.
- 테스트: `GoalSavingPaceReaderTest`·`RecommendationGeneratorTest` 갱신, `JdbcGoalPaceReaderIntegrationTest` 신규(H2로 실제 SQL 검증), `Simulation*ApiTest` 3개·`GoalHistoryApiIntegrationTest` fixture의 수입 행에 연결 계좌 `account_id` 부여.
- 문서: `goal.md` 저축속도 정의, `recommendation-api.md` 702.1 행, `discovery-report-metrics-675.md` §3 "목표 N일 단축" null 의미, `remaining-work.md` 702 절.

## 🔍 리뷰 포인트
- **카드 승인 귀속 결정**: 카드 거래는 `account_id`가 없어 처음엔 제외했는데, 시연 seed는 변동 지출의 70%가 카드라 제외하면 입출금 연결 목표(U0001)의 속도가 부풀려진다. 그래서 카드의 출금 계좌로 귀속했다(`JdbcGoalPaceReader` Javadoc). Jira 702 설명과 어긋나는 부분이 있으면 알려주세요.
- `SimulationApiTest`의 수입을 933,333 → 333,333으로 바꿨다. 기존엔 계좌 없는 카드 지출 60만이 속도에서 빠졌지만 이제는 계좌도 카드도 없는 행이라 들어가지 않으므로, 예상일(2027-03-14)을 유지하려 수입만 조정한 것이다.
- 발견 요약 `expectedGoalAdvanceDays`가 `GOAL_ADVANCE_UNAVAILABLE`로 비는 경우가 늘 수 있다(모든 개선안이 목표일을 넘는 사용자). FE는 evidence `goalImpactStatus`로 "개선해도 목표일을 넘어요" 문구를 붙일 수 있다.

## ✅ 테스트
- [x] 빌드 성공
- [x] 기능 테스트 완료

### 테스트 내용
- `./gradlew test` 전체 1348건 통과(실패 0, 스킵 24), `git diff --check` 이상 없음
- `JdbcGoalPaceReaderIntegrationTest`: 연결 계좌 거래 + 그 계좌 카드 승인만 합산, 미연결 계좌·카드·타인·숨김·창 밖 거래 제외, 첫 거래일도 같은 범위
- `RecommendationGeneratorTest`: 목표일 이내 → `TARGET_DATE_MET`·`advancedDays=105`, 목표일 초과 → `advancedDays=null`·`TARGET_DATE_NOT_MET`·문구 분기, 속도 0·스냅샷 목표 없음 기존 동작 유지
- 미검증: H2만 통과(서브쿼리 `IN`은 표준 SQL이라 MySQL 실측 생략). 배포 뒤 U0001 `GET /goals/{id}/forecast` 속도가 702 이전과 같은지, U0001~U0004 진단 `forceRefresh` → 추천 재생성으로 `702.1` 추천이 생기는지 확인 필요(`remaining-work.md`).

## 📎 참고
- Jira: S15P21D105-702 (선행 694 저축속도, 696 소비 증가 추천 목표 기여)
- `backend/docs/goal.md` §저축속도, `backend/docs/recommendation-api.md` §2, `backend/docs/discovery-report-metrics-675.md` §3

🤖 Generated with [Claude Code](https://claude.com/claude-code)
