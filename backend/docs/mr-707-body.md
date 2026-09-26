제목: `[S15P21D105-707] [BE] Feat: 소비 예측 근거 분해와 AI 코치 소비 예측 도구`

> 같은 이슈의 FE MR(`S15P21D105-707-spending-forecast-explanation-fe`, 예상 소비 카드 근거 표시)이 이 브랜치 위에 있다. 이 MR을 먼저 병합한다.

---

## 📌 작업 목적
- 706의 시계열 예측은 리포트 "예상 소비" 카드 한 곳에서 **숫자만** 보여 준다. 모델은 평소 수준·추세·계절 계수를 이미 추정해 두었는데 응답에 싣지 않아, 시연에서 "왜 12월이 많은가"를 설명할 근거가 화면에 없다.
- AI 코치(토리)는 목표 달성 예측·진단만 조회할 수 있고 소비 예측을 모른다. "다음 달 얼마 쓸 것 같아?"에 답하지 못한다.
- 둘 다 **읽기 전용**이다. 굳힌 진단 스냅샷·효율 점수에 영향이 없어 시연(09-28) 전에 넣을 수 있다. 영향이 큰 재사용(이번 달 예상 소비·현금흐름)은 [spending-forecast-reuse-plan.md] 결정대로 시연 뒤로 둔다.

## 🛠️ 작업 내용

**소비 예측 응답 근거 분해 (`GET /api/v1/spending-forecasts`)**
- 최상위 `explanation { level, monthlyTrend, seasonalityApplied }`. 예측이 없으면(이력 부족) null.
- 예측 달마다 `trendEffect`, `seasonalEffect`, `seasonalFactor`(소수 2자리, 계절 미사용이면 null), `lastYearAmount`(1년 전 같은 달 실적, 없으면 null).
- **`level + trendEffect + seasonalEffect = predicted`가 원 단위로 정확히 성립한다.** 두 효과를 차이(`baseline − level`, `predicted − baseline`)로 정의해 반올림 오차를 `seasonalEffect`가 흡수한다. 화면·코치가 세 조각을 나란히 보여 줘도 합이 어긋나지 않는다.
- `lastYearAmount`는 화면 창(12개월)이 아니라 추정 입력(24개월)에서 찾는다. 다음 달의 1년 전은 11개월 전이라 창 안이지만, `horizonMonths`를 늘리면 창 밖이 된다.
- 계산기 `MonthlyForecast`에 `baseline`(계절 적용 전 값)과 `seasonalFactor`만 더했다. **예측 계산식은 바꾸지 않았다.**
- 기존 필드는 그대로라 현재 develop의 카드와 호환된다.

**AI 코치 `get_spending_forecast` 도구**
- `SpendingForecastQueryService.forecast(userId, null)` — 카드와 같은 경로, 다음 3개월.
- **인자가 없다.** 기간을 열면 서버 검증 문구(`horizonMonths는 1~12…`)가 그대로 사용자에게 나간다. 9월 기준 10~12월이면 "연말에 왜 많아?"까지 답할 수 있다.
- 사실: `평소 한 달 변동 소비(계절 제외)`, `한 달 추세`(부호), 달마다 `예상 변동 소비`·`예상 범위`·`추세 영향`·`계절 영향`(계절 사용 시)·`작년 같은 달 실적`(있을 때). 월 라벨을 `2026년 12월`로 두어 모델이 "12월"이라고 말해도 `CoachNumberGuard`에 걸리지 않는다.
- 이력 부족이면 도구 오류 `소비 기록이 3개월 이상 쌓여야 예측할 수 있어요.`
- 채팅 규칙에 "물어본 달의 예상 금액과 가장 큰 근거 하나, 원 단위 표기 그대로"를 더했다. "약 65만 원"으로 줄이면 `65`가 허용 목록에 없어 템플릿으로 떨어진다.
- 템플릿(`CoachTemplateWriter.chat`)은 첫 예측 달 한 문장을 말한다: `2026년 10월 변동 소비는 520,000원으로 예상돼요.`

## 🔍 리뷰 포인트
- `SpendingForecastResponse.of`의 분해 정의(차이로 정의해 합을 보장)가 의도에 맞는지.
- `baseline`이 `max(0, level + D(h)·trend)`라 추세가 음수로 커서 0에 걸리면 `trendEffect = −level`이 된다. 합은 여전히 맞는다.
- 코치 채팅 규칙 두 줄 추가가 기존 시뮬레이션 답변 형식을 흐리지 않는지.

## ✅ 테스트
- [x] 빌드 성공
- [x] 기능 테스트 완료 (자동 테스트. 실제 LLM 호출은 미확인 — 아래)

### 테스트 내용
- 백엔드 전체 `./gradlew test`: 1,417건, 실패 0, 스킵 28(환경변수 게이트 MySQL 테스트 등).
- `SpendingForecastApiTest` 신규 2건·보강 2건: 평탄 이력에서 `level = predicted`·효과 0·계수 1.00·작년 실적, **추세 + 계절 이력에서 모든 예측 달의 합 일치**와 계절 효과 양수·작년 같은 달 실적, 12개월 미만이면 `seasonalFactor` null·`seasonalEffect` 0, 이력 부족이면 `explanation` null.
- `CoachToolDispatcherTest` 신규 3건: 사실 목록 전체(순서 포함), 계절 미사용 시 계절 사실 없음 + 사유 note, 예측 없음 → 도구 오류.
- `CoachToolRegistryTest`: 도구 8개. `CoachTemplateWriterChatTest`: 예측 대체 문장.
- **미검증**: 실제 GMS 모델이 이 도구를 고르는지와 답변이 숫자 검사를 통과하는지(시연 질문으로 리허설 필요), 24개월 seed가 적재된 DB에서 U0003의 실제 분해값, `JdbcSpendingForecastReaderMySqlIntegrationTest`는 게이트로 스킵(조회 SQL은 이번에 바꾸지 않음).

## 📎 참고
- 구현 기준: `backend/docs/spending-forecast.md` §2·§6, `backend/docs/ai-coach.md` §13-2-1
- 선행: S15P21D105-706 (`timeseries-forecast-plan.md`), 재사용 계획 `spending-forecast-reuse-plan.md`(이번 범위 밖)
- 루트 `docs/API명세서/`에는 706부터 이 엔드포인트가 없다. 이번에도 수정하지 않았다.
