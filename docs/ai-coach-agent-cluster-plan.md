# AI 고도화 계획 — 대화형 시뮬레이션 에이전트 · 군집 이탈 진단 연결

작성일: 2026-09-18. 상태: **진행 중** — Jira S15P21D105-640(상위), 641(C-1 군집 이탈 규칙, 2026-09-18 구현 완료·MR). §1-8 GMS 확인은 637에서 통과 → ① 설계 A(tool calling) 확정.
최종 발표 2026-09-28 기준 남은 작업일 10일(**주말 포함 작업으로 합의**, 2026-09-18)이며, 1차 QA(09-17)와 결함 수정이 병행된다.
담당(2026-09-18 팀 합의 완료): **BE·AI 전부 구동재, FE 전부 정서영.** 이 계획의 BE 변경(`coach`·`diagnosis`·`recommendation`·`simulation`·`pattern` 패키지)은 원 관할과 무관하게 구동재가 전담한다.

기준 자료: [기획안](기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 4-5(개선 효과 예시)·5-2(군집 범위 이탈)·5-4(우선순위 α)·5-7(AI 개입 구조)·13-4(AI/규칙 범위)·14-3(Hybrid fallback),
[API 21](API명세서/21%20AI%20Coach%203cdbb6ea356480ca99fdce9da0644e7b.csv), [API 17](API명세서/17%20Simulation%203cdbb6ea356480a38af4c6f56a5280f2.csv),
[backend/docs/ai-coach.md](../backend/docs/ai-coach.md), [backend/docs/simulation.md](../backend/docs/simulation.md), [backend/docs/pattern.md](../backend/docs/pattern.md), [backend/docs/diagnosis-api.md](../backend/docs/diagnosis-api.md).

---

## 0. 한 장 요약

| | ① 대화형 시뮬레이션 에이전트 "도리에게 묻기" | ③ 군집 이탈 진단 규칙 |
| --- | --- | --- |
| 무엇 | 자연어 질문 → LLM이 **서버 도구**(시뮬레이션·목표·진단 조회)를 골라 호출 → 서버가 계산한 숫자로만 답변 | K-Means 배정 결과의 기준 범위 이탈을 진단 문제로 만들어 규칙 진단과 병합(Hybrid) |
| 왜 | FE에 없는 시뮬레이션 10종·복합이 채팅 한 화면으로 살아난다. 기획안 4-5의 "두 행동을 함께 하면 121일 단축" 장면을 말 한마디로 실현 | 기획안 5-2·14-3의 Hybrid 진단이 코드로 완성된다. `CLUSTER_NOT_CONNECTED`·`clusterContext`·코치 "군집 근거" 섹션이 이미 비어 있는 자리 |
| 재사용 | `SimulationRunner`, `SimulationInputResolver`, `CoachFacts`/`CoachNumberGuard`/`CoachSensitiveDataGuard`, Redis 캐시·레이트 리밋 | `pattern_analysis.cluster_stats_json`(`ClusterProfileResponse`: 특징별 `deviation`·`direction`·`deviationScore`), `DiagnosisIssue.clusterContext`, `DetectionSource.CLUSTER/RULE_AND_CLUSTER` |
| 원칙 | **LLM은 파라미터 추출과 문장만. 판정·계산은 전부 서버.** 기존 3단 민감정보 방어·숫자 검증·템플릿 fallback을 그대로 확장 | 군집 판정·이탈 점수는 서버(이미 계산돼 저장됨). LLM은 설명만(기획안 5-7) |
| 담당 | BE 구동재(포트 확장·도구 계층·에이전트 루프·GMS 확인·레이트 리밋), FE 정서영(채팅 시트) | BE 구동재(규칙·병합·α·코치 연결), FE 정서영(배지·상세 카드) |
| 공수 | BE 4일 + FE 3일(병행) | BE 2일 + FE 0.5일 |

제외: 약관 RAG(데이터 없음), 영수증 OCR, 파인튜닝, 거래 LLM 자동분류(시연 가치 대비 데이터 변경 위험).

---

## 1. ① 대화형 시뮬레이션 에이전트

### 1-1. 시연 장면

발견 상세(고금리 대출·소비 증가) 또는 홈의 "도리에게 묻기" 버튼 → 채팅 시트.

```
사용자: 카페 5만원 줄이고 대출 100만원 더 갚으면 목표 언제 돼?

도리:   지금 속도면 '비상금 500만 원' 목표는 2028.02.13에 달성돼요.
        카페를 월 50,000원 줄이고 'KB 국민ONE대출'을 1,000,000원 더 갚으면
        2027.10.15로 121일 빨라지고, 만기까지 이자도 약 384,000원 줄어요.
        [시뮬레이션 결과 카드: 전 → 후 달성일 · 단축 일수 · 이자 절감]
```

- 카드의 숫자는 응답 `facts`·`simulationId`에서 FE가 그린다. 문장 속 숫자는 `CoachNumberGuard`로 검증된 값만이다.
- 시연은 **추천 질문 칩 3개**를 기본 경로로 하고 자유 입력은 1회만 한다. **칩은 서버가 LLM 없이 도구를 직접 매핑한다**(`chipId` → 도구 호출 고정, §1-3). 도구 선택 단계의 외부 호출이 없어 시연 중 실패 지점이 2차 문장 생성 하나로 줄고, 그마저 실패해도 템플릿 문장 + 결과 카드는 나온다.

### 1-2. 아키텍처

```
FE 채팅 시트 ──POST /api/v1/coach/chat──▶ CoachChatService
                                            ├ 1) 세션 로드(Redis, 사용자별, 최근 6턴, TTL 30분)
                                            ├ 2) CoachToolRegistry.definitions() → tools 스키마
                                            ├ 3) LLM 1차 호출 (messages + tools)           ← OpenAiCoachTextGenerator 확장
                                            ├ 4) tool_calls 있으면 CoachToolDispatcher 실행 (최대 3개, 1왕복)
                                            │     ├ simulate_*  → SimulationInputResolver → SimulationRunner.run(userId, input)
                                            │     ├ get_goal_forecast → GoalCalculationService.getForecast
                                            │     └ get_latest_diagnosis → DiagnosisQueryService
                                            │     결과 → CoachFacts(서버 포맷 문자열)
                                            ├ 5) LLM 2차 호출 (tool 결과 = facts 텍스트) → message
                                            ├ 6) CoachSensitiveDataGuard(송신 전) · CoachNumberGuard(응답 후) · 실패 시 템플릿
                                            └ 7) 응답 {message, facts, toolResults[{tool, simulationId}], source, degradedReason}
```

- 도구 실행은 **서버가 사용자 ID를 토큰에서 넣는다.** LLM이 `userId`·`accountNo`·`loanId` 원문을 만들 수 없다 — 도구 스키마에 그런 인자가 없다. 대출은 `loanLabel`(계좌명)으로 받아 서버가 본인 대출 중에서 찾는다.
- 세션 저장은 **사용자 문장과 facts만** 남긴다. 모델 응답 전문·프롬프트는 저장·로그하지 않는다(ai-coach.md §6 규칙 유지).
- 시뮬레이션 행은 정상 API와 같은 경로(`SimulationRunner.run`: 잠금·지문·저장)로 남는다. 같은 날 같은 지문이면 재사용(`created=false`)이라 반복 질문에 행이 늘지 않는다.

### 1-3. API 계약

`POST /api/v1/coach/chat` — 인증 필수, 본인 데이터만.

```json
// 요청
{ "message": "카페 5만원 줄이고 대출 100만원 더 갚으면 목표 언제 돼?",
  "chipId": null,                                        // 칩 클릭이면 "CHIP_SPENDING_CAFE_50K" 등. 있으면 서버가 도구를 직접 매핑하고 1차 LLM 호출을 생략한다
  "context": { "diagnosisId": 12, "issueId": 34 },      // 선택. 발견 상세에서 열면 채운다
  "conversationId": "c_7f3a..." }                        // 선택. 없으면 새 세션

// 응답 (CoachMessageResponse 확장)
{ "topic": "CHAT", "source": "AI", "degradedReason": null, "model": "gpt-5.4-mini",
  "conversationId": "c_7f3a...",
  "message": "지금 속도면 ... 121일 빨라지고 ...",
  "facts": { "subject": "복합 시뮬레이션", "sections": [
      { "title": "시뮬레이션 결과(서버 계산)", "facts": [
          { "label": "현재 예상 달성일", "value": "2028-02-13" },
          { "label": "실행 후 예상 달성일", "value": "2027-10-15" },
          { "label": "단축", "value": "121일" },
          { "label": "만기까지 이자 절감", "value": "384,000원" } ] } ],
    "notes": ["달성일·절감액은 서버 시뮬레이션 엔진이 계산한 값이다. 새 숫자를 만들지 말 것."] },
  "toolResults": [ { "tool": "simulate_combined", "simulationId": 88, "created": true } ],
  "suggestedQuestions": ["구독 하나 끊으면?", "저축을 월 30만원으로 올리면?"],
  "disclaimer": "이 설명은 ... 투자·금융 자문이 아닙니다." }
```

| 상황 | 응답 |
| --- | --- |
| 도구 인자 검증 실패(감소액 > 월평균 소비 등, 기존 시뮬레이션 400 규칙) | 도구 결과에 `error` 문자열로 넣어 2차 호출 → "카페 월평균 소비(38,000원)보다 큰 금액이에요"처럼 서버 메시지를 그대로 전달. HTTP는 200 |
| 도구 없이 답할 수 있는 질문(인사·기능 안내) | tool_calls 없음 → 1차 응답 그대로. 숫자 검증은 동일 |
| 금융과 무관한 질문 | 시스템 프롬프트로 거절 문장 유도 + 템플릿 안내. 외부 호출 1회로 끝 |
| AI 비활성·GMS 장애·레이트 리밋 | 200 + `source=TEMPLATE`, `degradedReason`(기존 6종 재사용 + `TOOL_LOOP_EXCEEDED`) |
| 401/404 | 기존 코치와 같다 |

### 1-4. 도구 목록 (1차 범위 6개)

| tool | 인자(LLM이 채움) | 서버 매핑 | 비고 |
| --- | --- | --- | --- |
| `simulate_spending_reduction` | `category?`(카테고리 이름/코드), `monthlyReduction`(원), `durationMonths?` | `SpendingSimulationRequest` → `SimulationInputResolver.resolve` | 카테고리 이름 → 코드는 기존 resolver가 처리(없으면 400 메시지) |
| `simulate_saving_change` | `monthlySavingAmount`, `goalName?`, `durationMonths?` | `SavingSimulationRequest{goalId}` — `goalName`은 서버가 본인 ACTIVE 모으기 목표에서 찾는다 | `accountNo`는 스키마에 없다 |
| `simulate_loan_prepayment` | `loanLabel?`, `prepaymentAmount`, `paymentDate?` | `LoanPrepaymentSimulationRequest{loanId}` — 대출 1건이면 생략 허용, 여럿이면 라벨 매칭 실패 시 "어느 대출인지" 되묻는 도구 결과 반환 | |
| `simulate_loan_refinance` | `loanLabel?`, `targetProductName?` | `LoanRefinanceSimulationRequest{loanId, targetProductId}` — `targetProductId`는 **`@NotNull`**이다. 상품 미지정이면 도구 계층이 최신 완료 진단의 `LOAN/HIGH_INTEREST_RATE` 문제 `currentValue.refinance.targetProductId`를 읽어 채우고, 그 값도 없으면(`NO_ELIGIBLE_PRODUCT`) 실행하지 않고 사유를 도구 결과로 돌려준다 | 시연 계정 U0003 대출(633). 진단이 먼저 돌아 있어야 한다 |
| `simulate_combined` | `scenarios[]`(위 도구 인자 배열) | `CombinedSimulationRequest` | 기획안 4-5 "함께 실행" |
| `get_goal_forecast` | `goalName?` | `GoalCalculationService.getForecast(userId, goalId, today)` — **3-인자 `@Transactional(readOnly)` 버전**. 4-인자 `getForecast(…, baseDate, months)`는 목표 잔액을 갱신하고 이력을 남기는 쓰기 경로라 채팅 조회에 쓰지 않는다 | 현재 달성일만 묻는 질문 |

읽기 도구 `get_latest_diagnosis`(문제 목록 요약)는 `context.diagnosisId`가 있으면 대신 facts에 미리 실어 호출을 아낀다.

**시연 칩 3개(`chipId` → 도구 고정 매핑, 서버 상수)** — 금액은 U0003 seed(636·633) 검증 규칙을 통과하는 값이다.

| chipId | 표시 문구 | 도구·인자 |
| --- | --- | --- |
| `CHIP_SPENDING_CAFE_50K` | "카페를 월 5만원 줄이면?" | `simulate_spending_reduction{category: CAFE, monthlyReduction: 50000}` (U0003 직전 3개월 카페 평균 약 115,000원이라 통과) |
| `CHIP_LOAN_PREPAY_1M` | "대출 100만원 더 갚으면?" | `simulate_loan_prepayment{prepaymentAmount: 1000000}` (대출 1건이라 라벨 생략) |
| `CHIP_COMBINED` | "둘 다 하면?" | `simulate_combined{scenarios: [위 둘]}` — 기획안 4-5 장면 |

도구 결과 → facts 변환은 `SimulationResponse`의 `before/after.goals[].expectedAchievementDate`, `goalImpacts[].differenceDays`, `comparison.costs[]`(이자)만 뽑아 `CoachFacts` 문자열로 포맷한다(천단위 구분 기호·ISO 날짜 — ai-coach.md §4 "값을 서버가 포맷하는 이유").

### 1-5. `OpenAiCoachTextGenerator` 확장

현재 `generate(systemPrompt, userPrompt)` → `String`이다. 아래를 추가한다(기존 메서드는 그대로 두어 3종 코치에 영향 없음).

```java
// CoachTextGenerator (port)
ChatTurn chat(List<ChatMessage> messages, List<ToolDefinition> tools);
record ChatTurn(String content, List<ToolCall> toolCalls) { }
record ToolCall(String id, String name, String argumentsJson) { }
```

- 요청 본문: `messages`(system/user/assistant/tool), `tools`(OpenAI function 스키마), `tool_choice: "auto"`, 기존 `max_completion_tokens`·`temperature 0.2`.
- 왕복은 **1회**(도구 호출 → 결과 → 최종 문장). 2차 응답에 또 tool_calls가 오면 `TOOL_LOOP_EXCEEDED`로 템플릿. `chipId` 요청은 1차 호출을 건너뛰고 2차만 한다.
- `CoachTopic.CHAT` 추가에 따른 파급: `CoachPromptFactory`(system 프롬프트·길이 상한 switch에 CHAT 분기), **`CoachTemplateWriter`에 CHAT 템플릿**(facts의 시뮬레이션 결과를 "현재 A → 실행 후 B, N일 단축" 고정 문장으로 — 이것이 없으면 fallback이 성립하지 않는다), `CoachNumberGuard.findUnverifiedNumber(text, facts)`에 허용 숫자 추가 인자(사용자 입력 문장의 숫자).
- 타임아웃: 1차 12초·2차 12초(기존 read-timeout 재사용). 채팅은 사용자가 기다리는 동기 요청이라 재시도는 429·5xx 1회만(기존 정책).

### 1-6. 안전장치 (기존 규칙을 그대로 확장)

| 단계 | 기존 | 에이전트에서 추가 |
| --- | --- | --- |
| 쿼리 | 이름·이메일·원천 데이터 미조회 | 도구 결과도 `SimulationResponse` DTO만 읽는다(계좌번호 원문은 DTO에 없음) |
| 조립 | `CoachEvidenceReader` 키 필터 | 도구 결과 → facts 변환기가 정해진 필드만 옮긴다(자유 JSON 전달 없음) |
| 송신 전 | `CoachSensitiveDataGuard` | **사용자 입력 문장에도 적용** — 계좌·카드번호를 채팅에 적으면 외부로 보내지 않고 "번호는 적지 말아 주세요" 템플릿 |
| 응답 후 | `CoachNumberGuard` | 허용 숫자 = facts + **사용자 입력의 숫자**(50,000·100만 등 사용자가 말한 값은 되풀이해도 된다) + 목록 번호 |
| 도구 인자 | — | JSON 스키마 검증 → 기존 `SimulationInputResolver`/Bean Validation 400 규칙 그대로. 금액 상한(예: 1억 초과 거부)은 도구 계층에서 한 번 더 |
| 예산 | 사용자별 코치 예산(`CoachCallRateLimiter`) | 채팅은 **별도 키·별도 한도**(외부 호출이 턴당 2회). 세션당 턴 수 상한 10 |
| 로그 | topic·위반 종류·숫자 토큰만 | 도구 이름·인자 키·simulationId만. 사용자 문장·모델 응답 전문은 남기지 않는다 |

### 1-7. FE

- 화면: `pages/coachChat/` 채팅 바텀시트 1개. 진입점 2곳 — 발견 상세 하단 버튼("도리에게 물어보기", `context` 전달), 홈 메뉴.
- 메시지 버블 + 결과 카드(`facts` 섹션을 그대로 라벨/값으로 렌더, `toolResults[].simulationId`가 있으면 "자세히" → 기존 `simulationApi.getSimulation` 재사용).
- 추천 질문 칩: 첫 화면은 **정적 칩 3개**(§1-4 표, `chipId` 전송 → 서버 고정 매핑). 응답의 `suggestedQuestions`는 `chipId`가 없는 자유 입력 경로로 보낸다(1차 LLM 호출 발생) — 시연에서는 정적 칩만 쓴다.
- 로딩 8~15초를 견디는 UI(도리 타이핑 애니메이션). 실패 시 `source=TEMPLATE` 문장 표시, 재시도 버튼.
- 타입: `CoachChatRequest`/`CoachChatResponse`를 `types/coachApi.types.ts`에 추가. `api/coachApi.ts`에 `chat()`.

### 1-8. 선행 확인 (착수 첫날, 구동재) — **완료(2026-09-18, S15P21D105-637): 통과. 설계 A 확정.**

`backend/scripts/gms_tools_probe.py`로 실측했다(`backend/docs/ai-coach.md` §8-3): `tools`+`tool_choice: required` → `tool_calls` 2개 정확 인자, `tool_choice` 강제,
`response_format json_schema strict`, 2턴 `role: tool` 회신 전부 200. 도구 인자 스키마는 enum·description으로 고정한다(빼면 모델이 `"카페"` 같은 한글 값을 넣었다).
아래는 원래 확인 절차로 남긴다.

GMS 중계가 `tools`를 통과시키는지 1회 확인한다. 통과하지 않으면 **대안 B**로 간다.

```bash
curl -sS https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions \
  -H "Authorization: Bearer $OPENAI_API_KEY" -H "Content-Type: application/json" \
  -d '{"model":"gpt-5.4-mini","messages":[{"role":"user","content":"카페 5만원 줄이면?"}],
       "tools":[{"type":"function","function":{"name":"simulate_spending_reduction",
         "parameters":{"type":"object","properties":{"category":{"type":"string"},"monthlyReduction":{"type":"integer"}},
         "required":["monthlyReduction"]}}}],"tool_choice":"auto","max_completion_tokens":200}'
```

기대: `choices[0].message.tool_calls[0].function.name == "simulate_spending_reduction"`. 키 값은 출력·기록하지 않는다.

**대안 B(구조화 출력)**: `response_format: {type: "json_schema"}`로 `{intent, scenarios[]}`만 뽑고 서버가 디스패치 → 결과 facts로 2차 호출. 도구 루프가 없어 구현이 더 단순하며 시연 장면은 동일하다. `tools`·`json_schema` 둘 다 막히면 프롬프트에 JSON 형식을 지시하고 서버가 파싱(신뢰도 낮음, 최후 수단).

### 1-9. 테스트

- 단위: 도구 인자 → `SimulationInput` 변환(카테고리 이름·대출 라벨·목표 이름 매칭, 여럿·없음), facts 포맷, 숫자 허용 목록(사용자 입력 숫자 포함), 루프 상한, 세션 턴 상한.
- 통합(H2, `CoachTextGenerator` 스텁으로 tool_calls 시나리오 주입): 소비 감소 1도구 → 문장·facts·simulationId, 복합 2도구, 인자 검증 실패 메시지 전달, 민감번호 입력 차단, AI 비활성 템플릿, 레이트 리밋, 타인 대출 라벨 → 매칭 실패(존재 노출 없음), 401.
- 시연 seed(MySQL): U0003으로 칩 3개 실제 GMS 호출 각 3회, `source=AI` 비율·응답 시간 기록.

### 1-10. MR 분할

| MR | 내용 | 담당 |
| --- | --- | --- |
| A-1 | `CoachTextGenerator.chat` 포트·`OpenAiCoachTextGenerator` tools 요청/응답 파싱·GMS 확인 결과 문서 — **구현 완료(09-18)**, `backend/docs/ai-coach.md` §13-1. 계획의 `chat(messages, tools)`에 `ToolChoice`(AUTO/REQUIRED/NONE/FUNCTION)를 더했다(칩 경로에서 함수 강제·2차 문장 생성에서 NONE) | 구동재 |
| A-2 | `CoachToolRegistry`·`CoachToolDispatcher`·도구 6개·칩 매핑·facts 변환기·단위 테스트 — **구현 완료(09-18)**, `backend/docs/ai-coach.md` §13-2. 도구는 6개 + 읽기 도구 `get_latest_diagnosis`, 인자 검증 `CoachToolArguments`, 결과 `CoachToolResult` | 구동재 |
| A-3 | `CoachChatService`·Redis 세션·별도 레이트 리밋·`POST /coach/chat`·`CoachTopic.CHAT`(프롬프트·템플릿·숫자 검증 확장)·통합 테스트·`backend/docs/ai-coach.md` §13 추가 | 구동재 |
| A-4 | FE 채팅 시트·진입점·칩·타입 | 정서영 |

---

## 2. ③ 군집 이탈 진단 규칙 연결 (Hybrid 완성)

### 2-1. 현재 상태

- `PatternAnalysisRunner`(HYBRID)가 배정·기준 범위·특징별 `deviation`(`ClusterModel.rangeDeviation`: scale = max(p95−p5, std))·`direction`·`deviationScore`를 `pattern_analysis.cluster_stats_json`에 **이미 저장**한다.
- `DiagnosisEvaluator`는 `CLUSTER` coverage를 `UNAVAILABLE / CLUSTER_NOT_CONNECTED`로 고정. `DiagnosisIssue.clusterContext`·`DetectionSource.CLUSTER`·`RULE_AND_CLUSTER`는 정의만 있고 채우는 코드가 없다.
- `CoachFactAssembler.inefficiency`는 `clusterContext`가 있으면 "군집 근거(서버 계산)" 섹션을 자동으로 싣는다. `RecommendationPriorityPolicy`의 α는 0.
- FE는 `POST /patterns/analyze`를 호출하지 않는다.

### 2-2. 판정 (기획안 5-2 그대로, 새 계산 없음)

`ClusterDeviationRule.evaluate(userId, asOf)`:

1. 최신 COMPLETED `pattern_analysis`를 읽는다. 없거나 `cluster_label`이 null(RULE_ONLY fallback)이면 coverage `UNAVAILABLE / PATTERN_NOT_ANALYZED` 또는 `fallbackReason` 그대로.
2. `analyzedAt`이 진단 기준일로부터 **30일 이전**이면 `UNAVAILABLE / STALE_PATTERN_ANALYSIS`(낡은 배정으로 판정하지 않는다). 30일은 서비스 초기값이다.
3. 노출 조건(이미 분석 단계에서 검사된 값이지만 진단에서 다시 확인): `sampleSize ≥ min-cluster-sample-size(10)`, `confidence ≥ min-confidence(0.2)`, 사용자 데이터 개월 ≥ `min-user-months(3)`.
4. `clusterStats.features[]` 중 `direction != WITHIN`이고 `deviation ≥ FEATURE_DEVIATION_THRESHOLD`인 특징마다 문제 후보를 만든다. **방향 필터**: 아래 표의 "점검 방향"만 문제로 본다(저축률이 군집보다 높은 것은 문제가 아니다).

| 특징 | 점검 방향 | 문제 type | 규칙 진단과 같은 카테고리 → 병합 대상 |
| --- | --- | --- | --- |
| `spendingToIncomeRatio` | ABOVE | `SPENDING/CLUSTER_SPENDING_RATIO_HIGH` | **단독 생성.** 성격상 `GOAL/SPENDING_GOAL_RISK`와 겹치지만 FE가 GOAL 도메인을 발견 목록에서 숨기므로(`discoveryModel.isHiddenFromDiscovery`) 병합하면 배지가 화면에 나오지 않는다 |
| `savingRate` | BELOW | `SAVING/CLUSTER_SAVING_RATE_LOW` | **단독 생성**(같은 이유). 새 도메인 `SAVING`은 FE `DOMAIN_LABELS`·아이콘 추가 필요(C-3) |
| `discretionaryShare` | ABOVE | `SPENDING/CLUSTER_DISCRETIONARY_HIGH` | `SPENDING/CATEGORY_SPENDING_INCREASE`(재량 카테고리 FOOD·CAFE·SHOPPING·BEAUTY·CULTURE·TRAVEL·TAXI인 것) |
| `fixedExpenseShare` | ABOVE | `SPENDING/CLUSTER_FIXED_SHARE_HIGH` | (고정지출 증가 규칙 미구현 → 단독) |
| `spendingVolatility` | ABOVE | `CASHFLOW/CLUSTER_VOLATILITY_HIGH` | `CASHFLOW/BALANCE_SHORTAGE` |

5. 심각도: `deviation ≥ 1.0` HIGH(기준 범위 폭만큼 벗어남), 그 외 MEDIUM. **임계값 `FEATURE_DEVIATION_THRESHOLD = 0.25`, 경계 모두 서비스 초기값**(스크럼 확정 대상, 627과 같은 처리). 시연 seed로 4개 성향별 탐지 건수를 실측한 뒤 조정한다.
6. 병합: 같은 진단 안에 표의 "병합 대상" 규칙 문제가 있으면 새 문제를 만들지 않고 그 문제의 `detectionSource = RULE_AND_CLUSTER`, `clusterContext`를 채운다(기획안 5-2 "근거만 병합"). 없으면 `DetectionSource.CLUSTER` 단독 문제 `DiagnosisIssue.ofCluster(...)`를 만든다. 제목은 단정하지 않는다 — "비슷한 소비 유형과 견줘 점검할 항목이 있어요".

`clusterContext` JSON(코치가 그대로 읽는다):

```json
{ "modelVersion": "spending-kmeans-v2", "clusterId": 1, "clusterLabel": "소비 집중형",
  "sampleSize": 1000, "confidence": 0.58, "window": "2026-03~2026-08", "analyzedAt": "2026-09-18T09:12:00",
  "feature": "discretionaryShare", "displayName": "재량 소비 비중",
  "userValue": 0.44, "p5": 0.12, "p95": 0.31, "deviation": 0.68, "direction": "ABOVE",
  "percentileInCluster": 92.0, "rankBand": "TOP_10", "deviationScore": 0.23 }
```

`deviationScore`는 특징 5종 이탈의 **균등 평균**(`ClusterProfileResponse` 정의)이라 개별 특징 `deviation`보다 클 수 없다.
`currentValue` = `{feature, userValue, direction}`, `benchmarkValue` = `{p5, p95, mean, sampleSize}`, `evidence` = `{ruleKey: "CLUSTER_<feature>", targetId: patternAnalysisId}`.

### 2-3. 추천·우선순위·코치 연결

- 추천: 병합된 문제는 기존 생성기가 그대로 만든다(입력 `currentValue`는 규칙 값 유지). 단독 CLUSTER 문제는 `ClusterDeviationGenerator`가 효과 없이 `SPENDING/REVIEW_SPENDING_PATTERN` "비슷한 유형과 소비 구조 비교해 보기" 후보를 만든다(효과 금액 추정 없음 → 절감액 산정 전). 프리셋은 `discretionaryShare`·`spendingToIncomeRatio`만 `/simulations/spending`(금액 FE 입력).
- 우선순위: `RecommendationPriorityPolicy`에 α 반영 — `score += min(10, round(α × deviationScore × 10))`, `α = 1`(군집 결과 없으면 0, 기획안 5-4). `deviationScore`가 평균값(대개 0~0.5)이라 점수 기여는 최대 5점 안팎 — 순위 뒤집기가 아니라 동점 해소 수준이다. ~~`RecommendationInput.Issue`에는 `clusterContext`가 없으므로 `RecommendationInputReader`에 필드를 추가해야 한다~~ → 확인 결과 이미 있다(547에서 정의). C-2는 `RecommendationPriorityPolicy.clusterPoints`만 추가했다.
- 코치: `CoachFactAssembler.inefficiency`의 "군집 근거(서버 계산)" 섹션이 자동으로 채워진다. `notes`에 "낭비로 단정하지 말고 '비슷한 소비 유형 사용자군 대비 점검이 필요한 항목'으로 표현할 것"(기획안 5-7)을 추가.
- `DiagnosisRuleVersion`을 `v7-cluster-hybrid`로 올린다. coverage `CLUSTER`는 `EVALUATED`/`NOT_APPLICABLE`/`UNAVAILABLE(사유)`.

### 2-4. 패턴 분석 실행 시점

FE가 `POST /patterns/analyze`를 부르지 않으므로 진단 실행 시 **서버가 먼저 패턴 분석을 보장**한다: `DiagnosisRunner`가 접수 후 계산 전에 `PatternAnalysisRunner.run(userId, defaults)`(오늘 완료본 있으면 재사용, 데이터 부족 409는 삼키고 coverage 사유로 기록). `DiagnosisRunner.run`은 `admit` 트랜잭션이 커밋된 뒤 `evaluate`를 트랜잭션 밖에서 호출하므로 그 사이에 넣으면 잠금 중첩·readOnly 충돌이 없다. 사용자 1명 분석 소요 시간은 **미실측**(pattern.md의 7초는 4,000명 집계+학습 시간)이며 09-23 실측 항목에 넣는다.

### 2-5. FE

- 발견 목록: `detectionSource`가 `CLUSTER`·`RULE_AND_CLUSTER`면 배지 "또래 비교". `DiagnosisIssueApiResponse`에 `detectionSource`·`clusterContext` 타입 추가(BE 응답에 이미 있음, 연동 현황 §4-5). `DOMAIN_LABELS`·`DOMAIN_ICONS`에 `SAVING` 추가.
- 발견 상세: `clusterContext`가 있으면 카드 1개 — "비슷한 소비 유형(소비 집중형, 1,000명) 재량 소비 비중 12~31% · 회원님 44% (상위 8%)". 문구는 `clusterContext` 값만 쓰고 단정 표현 금지.

### 2-6. 테스트

- 단위: 방향 필터, 임계·심각도 경계, 병합 대상 매칭(재량 카테고리 판별), 낡은 분석·fallback·표본 부족 coverage.
- 통합(H2, `DiagnosisApiTest` 확장): 패턴 분석 없음 → `PATTERN_NOT_ANALYZED`; HYBRID 배정 + 소비 증가 규칙 문제 있음 → `RULE_AND_CLUSTER` 1건(문제 수 불변); 규칙 문제 없음 → CLUSTER 단독 1건; 코치 비효율 설명에 군집 섹션 포함; 재실행 캐시 무효(규칙 버전).
- MySQL: `clusterContext` JSON 저장·조회(627의 `current_value` 직렬화 검증과 같은 방식).
- 시연 seed: U0001~U0004(성향 4종)로 진단 실행 → 탐지 건수·라벨 기록. 전부 0건이거나 전부 HIGH면 임계 재조정.

### 2-7. MR 분할

| MR | 내용 | 담당 |
| --- | --- | --- |
| C-1 | `ClusterDeviationRule`·`DiagnosisIssue.ofCluster`·병합·coverage·규칙 버전·진단 시 패턴 분석 보장·테스트 — **641 구현 완료(09-18)**, 계약 `backend/docs/diagnosis-api.md` §641 | 구동재 |
| C-2 | `ClusterDeviationGenerator`·`RecommendationInput.Issue.clusterContext`·α 반영·코치 notes·`backend/docs/diagnosis-api.md`/`recommendation-api.md` 갱신 — **구현 완료(09-18)**. `RecommendationInput.Issue`에는 `clusterContext`가 이미 있어 리더 변경 없음 | 구동재 |
| C-3 | FE 배지·상세 카드·`SAVING` 라벨·타입 | 정서영 |

---

## 3. 일정 (2026-09-18 ~ 09-28)

| 날짜 | ① 에이전트 | ③ 군집 규칙 | 비고 |
| --- | --- | --- | --- |
| 09-18 (목) | Jira 이슈 생성, GMS `tools` 확인(§1-8), 대안 결정 | C-1 착수 | QA 결함 수정 병행. 시연 계정 U0003 구성 확인 완료(아래) |
| 09-19 (금) | A-1 포트·생성기 확장, A-2 도구 계층 | C-1 완료·MR | |
| 09-20~21 (주말, 작업 합의) | A-2 테스트, A-3 서비스·세션 | C-2 | FE A-4 착수(스텁 응답으로) |
| 09-22 (월) | A-3 통합 테스트·MR | C-2 MR, C-3 | |
| 09-23 (화) | FE 연동, 시연 seed 실측(`source=AI` 비율·응답 시간) | seed 4종 실측·임계 조정·사용자 1명 패턴 분석 소요 시간 | 2차 QA |
| 09-24 (수) | 결함 수정, 칩 문구 확정 | 코치 문구 확인 | |
| 09-25 (목) | **기능 동결**. 시연 리허설, 실패 경로 확인 | | 이후 문서·발표 자료만 |
| 09-26~27 | 발표 자료·리허설 | | |
| 09-28 (월) | 최종 발표 | | |

기능 동결 이후 발견되는 결함은 시연 경로 밖이면 고치지 않는다.

---

## 4. 리스크와 대응

| 리스크 | 영향 | 대응 |
| --- | --- | --- |
| GMS가 `tools`를 통과시키지 않음 | ① 설계 변경 | 첫날 확인. 대안 B(구조화 출력) — 도구 계층·FE는 동일하게 재사용 |
| 시연 중 GMS 지연·장애 | 채팅이 템플릿으로 강등 | 칩은 도구 매핑이 고정이라 1차 호출이 없고(§1-1), 2차 문장은 시연 전 한 번 실행해 두면 `CoachCacheKey(userId, facts)` 캐시에 적중해 외부 호출 없이 나간다(같은 날 같은 지문 → 같은 시뮬레이션 → 같은 facts). 캐시 키에 대화 이력이 섞이지 않게 **칩 질문은 단일 턴**으로 처리 |
| LLM이 대출·목표를 잘못 고름 | 엉뚱한 시뮬레이션 | 라벨 매칭 실패 시 실행하지 않고 되묻기. 시연 계정은 대출 1건(U0003)·모으기 목표 1건으로 모호성 제거 |
| 숫자 검증에 사용자 입력이 자꾸 걸림 | 템플릿 비율 상승 | 허용 목록에 사용자 입력 숫자 포함(§1-6). 실측 비율을 09-23에 기록 |
| 군집 규칙이 seed에서 0건 또는 과다 탐지 | 시연 장면 부재·소음 | 09-23 실측 후 임계 조정. 시연 계정 1개는 반드시 `RULE_AND_CLUSTER` 1건이 나오게 확인 |
| 진단 시 패턴 분석 강제 실행으로 응답 지연 | 발견 "진단하기" 대기 증가 | 오늘 완료본 재사용, 실패는 coverage로만 기록 |
| 외부 과금 | 자유 입력은 턴당 2회 호출(칩은 1회) | 별도 레이트 리밋·세션 턴 상한·GMS 사용량 확인(구동재) |
| 일정 — QA와 병행 | 미완성 노출 | 09-25 동결. ①이 밀리면 **칩 3개(고정 질문)만** 시연하고 자유 입력은 뺀다. ③은 독립이라 먼저 닫는다 |
| 시연 계정 구성 | "달성일 N일 단축" 장면 성립 여부 | **확인 완료(09-18)**: U0003(김싸피, 636)은 ACTIVE 모으기 목표 1건(target 51,300,000·목표일 2027-06-01·현재 20,000,000, seed 규칙 기준 예상 달성일 2027-07-18로 47일 지연)과 고금리 대출 1건(633). 라벨 매칭 모호성 없음. 줄이기 목표는 seed 규칙상 없으므로 예산 초과 장면은 U0002/U0004로 |

---

## 5. 발표에서 말할 것 / 말하지 않을 것

**말할 것**
- "AI는 무엇을 시뮬레이션할지 고르고 문장을 쓴다. 달성일·절감액은 전부 서버 엔진이 계산한다" — 도구 스키마에 사용자 식별자·계좌번호가 없고, 응답 숫자를 서버 사실과 대조해 어긋나면 템플릿으로 내린다.
- "규칙 진단 + K-Means 보조 탐지 + LLM 설명"의 Hybrid — 군집 판정·이탈 점수는 서버, 모델 버전·표본 수·기준 기간을 문제에 기록, 표본 부족·낡은 분석이면 자동으로 규칙만.
- K-Means 지표는 **합성 seed 4,000명 기준**(ARI 1.0·실루엣 0.51)임을 명시.

**말하지 않을 것**
- "AI가 금융 상태를 판단한다", "AI가 최적 조합을 계산한다"(조합 계산은 서버 `CombinedCalculator`).
- "실사용자 데이터로 검증된 군집 정확도".
- 도구 6개 밖의 시뮬레이션(예적금 가입·리볼빙 등)이 채팅으로 된다는 말 — 1차 범위 밖.

---

## 6. 미결 (스크럼 안건)

1. ~~GMS `tools` 통과 여부 → ① 설계 A/B 확정 (09-18).~~ **확정: 통과, 설계 A**(637).
2. `FEATURE_DEVIATION_THRESHOLD`·HIGH 경계·α 값 — seed 실측 후(09-23).
3. 채팅 레이트 리밋 값(사용자·일 단위)과 세션 TTL.
4. 진단 시 패턴 분석 보장(§2-4)을 진단에 넣을지, FE가 `POST /patterns/analyze`를 온보딩 동기화 직후 호출할지.
5. 루트 API 명세 21·14 반영 요청 여부(`POST /coach/chat`, 진단 문제 `detectionSource`·`clusterContext` 노출).
