제목: `[S15P21D105-707] [FE] Feat: 예상 소비 카드 예측 근거 표시`

> 같은 이슈의 BE MR(`S15P21D105-707-spending-forecast-explanation`, 응답 근거 필드·코치 도구) 위에 있다. **BE MR이 병합되기 전에는 대상 브랜치를 BE 브랜치로 둔다.** BE 병합 뒤 대상을 `develop`으로 바꾸면 diff가 FE 한 커밋만 남는다.

---

## 📌 작업 목적
- 리포트 "예상 소비" 카드는 예상 금액만 보여 줘서 **왜 그 금액인지** 알 수 없다. 특히 12월처럼 크게 튀는 달이 추세 때문인지 계절 때문인지 화면에서 구분되지 않는다.
- BE MR이 응답에 싣는 근거(평소 수준·추세 영향·계절 영향·작년 같은 달 실적)를 카드에 보여 준다.

## 🛠️ 작업 내용
- `forecastApi.types.ts`: `SpendingForecastExplanation`과 `SpendingForecastPoint`의 `trendEffect`·`seasonalEffect`·`seasonalFactor`·`lastYearAmount`를 백엔드 null 가능성대로 추가.
- `SpendingForecastCard`: **예측 달을 선택했을 때만** 차트 아래에 "N월 예상 근거" 블록을 보여 준다. 기본 선택이 다음 달이라 카드를 열면 바로 보인다.
  - `평소 한 달 소비` = `explanation.level`
  - `늘어나는 추세` / `줄어드는 추세` = `trendEffect`(부호 표기)
  - `N월 계절 영향` = `seasonalEffect` — `seasonalityApplied`일 때만
  - `작년 N월에는 X원을 썼어요.` — `lastYearAmount`가 있을 때만
- 세 금액의 합은 서버가 `predicted`와 원 단위로 맞춰 내려 준다. **화면에서 다시 계산하지 않는다.**
- 실측 달을 고르면 근거 블록이 없다(분해할 모델 값이 없다).

## 🔍 리뷰 포인트
- 근거 블록 위치(범례와 안내 문구 사이)와 문구가 카드 흐름에 맞는지.
- 배경색은 기존 토큰 `colors.report.patternSurface`, 모서리는 `radius.default`를 썼다. 새 색 토큰은 없다.

## ✅ 테스트
- [x] 빌드 성공 (`npx tsc --noEmit`, 변경 파일 eslint)
- [ ] 기능 테스트 완료

### 테스트 내용
- `npx tsc --noEmit` 통과, 변경 3개 파일 `eslint` 통과.
- **미검증**: Android·iOS·Web 어디에서도 화면을 직접 띄워 보지 않았다. 시연 서버(24개월 seed)에서 12월 선택 시 계절 영향이 기대대로 크게 나오는지 확인이 필요하다.
- 확인 시나리오: 리포트 진입 → 카드 머리 "10월 예상" + 근거 블록 → 12월 점 선택 → 계절 영향 증가 확인 → 8월(실측) 선택 → 근거 블록 사라짐.

## 📎 참고
- API 계약: `backend/docs/spending-forecast.md` §2·§6
- BE MR 본문: `backend/docs/mr-707-body.md`
