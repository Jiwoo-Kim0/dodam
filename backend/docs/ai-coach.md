# AI Coach API (S15P21D105-589)

작성일: 2026-09-16. 명세 21의 3종을 구현했다.

기준 자료: [API 21](../../docs/API명세서/21%20AI%20Coach%203cdbb6ea356480ca99fdce9da0644e7b.csv),
[기획안](../../docs/기획안/기획안%20작성%203c3bb6ea35648097a693e0e321b3e9c0.md) 11-3(AI 금융코치 · 대체 방법 "템플릿 기반 응답"),
[진단 API](diagnosis-api.md), [추천 API](recommendation-api.md), [패턴 분석](pattern.md).

## 1. 이 API가 하는 일과 하지 않는 일

코치는 **계산기가 아니라 표현 계층**이다. 수치·판정은 이미 진단(14)·추천(16)·패턴(3)이 계산해 저장했고,
코치는 그 저장값을 문장으로 옮긴다. 명세의 제한사항 3줄이 전부 같은 말이다.

| 명세 제한사항 | 구현에서 대응하는 것 |
| --- | --- |
| 서버 계산 결과만 입력으로 사용 | `CoachFactAssembler`가 만든 `CoachFacts` 밖의 값은 프롬프트에 들어가지 않는다 |
| AI는 설명 생성 / AI 자체 판정·계산 금지 | 프롬프트 규칙(1차) + `CoachNumberGuard`의 응답 검사(강제) |
| 군집 라벨·기준 범위는 서버 계산값만 전달, 군집 판정 금지 | `pattern_analysis.cluster_stats_json`(이미 서버 계산값)을 그대로 옮기고, 없으면 "또래 비교를 언급하지 말 것"을 프롬프트에 넣는다 |

## 2. 엔드포인트

세 엔드포인트가 **같은 응답 스키마**를 쓴다. 다른 것은 `topic`과 `facts`의 내용뿐이다.

| API | Method · Endpoint | Body | 대상 |
| --- | --- | --- | --- |
| 금융현황 AI 요약 | POST `/api/v1/coach/financial-summary` | `{ diagnosisId? }` (본문 생략 가능) | 진단 1건 전체. 생략 시 최신 완료 진단 |
| 비효율 원인 설명 | POST `/api/v1/coach/inefficiency-explanation` | `{ diagnosisId, issueId }` | 진단 문제 1건 |
| 추천 근거 설명 | POST `/api/v1/coach/recommendation-explanation` | `{ recommendationId }` | 개선안 1건 |

### 응답

```json
{
  "success": true,
  "data": {
    "topic": "RECOMMENDATION_EXPLANATION",
    "source": "AI",
    "degradedReason": null,
    "model": "gpt-4o-mini",
    "generatedAt": "2026-09-16T12:00:00",
    "subject": "쓰지 않는 구독을 정리하세요",
    "message": "매달 12,000원을 아낄 수 있는 개선안입니다.\n\n사용 기록을 확인해 보세요.",
    "highlights": ["영역: SPENDING", "유형: CANCEL_SUBSCRIPTION", "우선순위(서버 계산): 1"],
    "facts": {
      "topic": "RECOMMENDATION_EXPLANATION",
      "subject": "쓰지 않는 구독을 정리하세요",
      "sections": [{ "title": "예상 효과(서버 계산)", "facts": [{ "label": "월 절감액", "value": "12,000원" }] }],
      "notes": ["우선순위와 효과 금액은 서버 추천 엔진이 계산해 저장한 값이다. ..."]
    },
    "disclaimer": "이 설명은 ... 투자·금융 자문이 아닙니다."
  }
}
```

- `facts`를 응답에 싣는 이유: **FE가 화면의 수치를 AI 문장에서 긁지 않게** 하기 위해서다. 문장은 바뀌어도
  숫자는 서버 계산값이어야 한다. `facts` = 모델에 준 입력 = 화면에 쓸 값이다.
- `highlights`는 AI 경로에서도 서버(`CoachTemplateWriter`)가 만든다. 수치는 어떤 경로에서도 모델을 거치지 않는다.
- `message`만 모델이 쓴다. `source=TEMPLATE`이면 그것도 서버가 쓴다.

## 3. 인증·소유권

- `SecurityConfig`가 `/api/v1/**`를 인증 대상으로 잡으므로 코치는 별도 설정 없이 인증 필수다. **401**.
- 사용자 ID는 토큰에서만 온다(`AuthenticatedUserIdResolver`). 요청 본문에 userId를 받지 않는다 —
  받는 순간 그 값이 남의 데이터를 읽는 입력이 된다.
- 소유권은 `userId` 조건이 **쿼리 안에 걸린** 메서드로만 조회한다. `diagnosis_issue`·`recommendation_reason`은
  테이블에 `user_id`가 없어 상위 테이블을 조인한다(`DiagnosisIssueRepository.findOwnedById`,
  `RecommendationReasonRepository.findOwned`).
- 남의 리소스와 없는 리소스는 모두 **404 `RESOURCE_NOT_FOUND`**로 같다. 구분해 주면 ID를 훑어 남의 진단
  존재 여부를 셀 수 있다. 미완료 진단도 같은 이유로 404다(`DiagnosisQueryService`와 동일한 계약).

## 4. 프롬프트에 무엇이 들어가는가

`CoachFacts` = `subject` + `sections[]{title, facts[]{label, value}}` + `notes[]`. **모든 `value`는 서버가 포맷한 문자열이다.**

| topic | sections |
| --- | --- |
| FINANCIAL_SUMMARY | 진단 개요(완료 시각·규칙 버전·종합 상태·문제 수·높은 심각도 수·목표 연계 여부) / 심각도별 문제 수 / 영역별 문제 수 / 주요 문제 3건 / **진단 검사 범위**(검사한·검사 못 한 항목 수와 이름) / 소비패턴 군집(라벨·모델 버전·표본 수·신뢰도·이탈 상위 3개 특징의 내 값·기준 범위·방향) |
| INEFFICIENCY_EXPLANATION | 문제 개요(영역·유형·심각도·탐지 방식·탐지 시각·모델 버전·규칙 버전) / 현재 값 / 기준 값 / 탐지 근거 / 군집 근거 |
| RECOMMENDATION_EXPLANATION | 개선안 개요(영역·유형·우선순위·상태·생성 시각) / 예상 효과(null 아닌 것만) / 저장된 근거(code·description·evidence) |

### `issueCount = 0`을 "이상 없음"으로 옮기지 않기

**문제 0건은 "다 검사했는데 이상이 없다"와 "검사를 못 했다"를 구분하지 않는다.** 현재 진단 규칙 12개
중 10개가 미구현(`RULE_POLICY_NOT_DEFINED`)·미연동(`CLUSTER_NOT_CONNECTED`)으로 항상 `UNAVAILABLE`이고,
나머지 2개(GOAL·CASHFLOW)도 자료가 없으면 건너뛴다. 실제로 2026-09-16 운영 진단은 12개 중 **GOAL 1개만**
검사됐다.

이 상태에서 "탐지된 문제 0건"만 주면 모델은 그것을 "금융 상태가 건강하다"로 옮긴다. 그것은 서버가
하지 않은 판정이다. 그래서 진단 스냅샷의 `coverage`를 사실로 싣고, 검사하지 못한 항목이 있으면
`notes`로 "문제가 전혀 없다고 단정하지 말고 검사하지 못한 항목이 있다는 점을 함께 알려라"를 넣는다.

`overallStatus`가 null인 경우도 마찬가지다. 진단은 문제가 0건일 때 `HEALTHY`가 아니라 **null(판정 보류)**을
저장한다(`DiagnosisEvaluator`). 이것을 '양호'로 바꿔 말하지 말라는 지시를 함께 넣는다.

`notes`는 모델이 **새로 판정하지 못하게 못박는 줄**이다: "심각도는 서버가 판정했다", "군집 결과가 없으니
또래 비교를 언급하지 말 것", "효과 금액이 없으니 절감액을 추정하지 말 것" 등. 데이터 상태에 따라 붙는다.

### 값을 서버가 포맷하는 이유

`0.6234`를 넘기고 "62.3%로 바꿔 써라"를 시키면 그 순간 그것이 계산이고, 명세가 금지한 "AI 자체 계산"이다.
동시에 서버가 쓴 문자열이 곧 §5의 허용 숫자 목록이 된다. 정수에 항상 천단위 구분 기호를 넣는 것도 의도적이다 —
구분 기호 없는 긴 숫자열은 §6의 계좌번호 규칙과 형태가 같아, 정상 금액이 민감정보로 걸려 버린다.

## 5. AI 자체 계산·판정 금지 (`CoachNumberGuard`)

프롬프트의 "계산하지 마라"는 지켜지는 편이지만 **보장이 아니다**. 월 절감액 12,000원에서 모델이 "연 144,000원"을
만들어 내면 그 문장은 그럴듯하고 검토자가 알아채기 어렵다. 우리 도메인에서 연 절감액은 서버가 계산해 저장하는
값이므로(`ANNUAL_IS_MONTHLY_TIMES_12`) 모델이 만들 이유가 없다.

- 허용 숫자 = `CoachFacts`의 모든 문자열에서 뽑은 숫자 + 각 숫자의 정수부·소수 1자리 반올림·버림·올림 + `1`,`2`,`3`(목록 번호).
- 구분 기호·선행 0은 무시한다(`12,000` = `12000`, `09` = `9`).
- 반올림을 허용하는 이유: 막으면 "약 63%" 같은 정상 문장이 계속 탈락해 템플릿 비율이 올라간다.
- 검사 대상은 아라비아 숫자뿐이다. "세 가지" 같은 한글 수량 표현은 통과한다 — 막으려다 정상 문장을 대부분 떨어뜨린다.
- 걸리면 **문장을 버리고 템플릿으로 내려간다**. 틀린 숫자를 보여주느니 덜 매끄러운 문장을 보여준다.

판정(심각도·종합 상태·군집)은 애초에 모델을 거치지 않는다. 응답의 해당 값은 전부 `facts`에서 오고,
모델은 `message` 문자열만 만든다.

## 6. 민감정보 제외

2단 방어다.

1. **조립 단계** — `CoachEvidenceReader`가 스키마 미고정 JSON 컬럼(`currentValue`, `benchmarkValue`,
   `evidence`, `clusterContext`)에서 실을 값을 고른다. 이 컬럼들은 의도적으로 스키마를 고정하지 않았고
   (`DiagnosisIssue` 주석), 이미 `targetId`·`forecastId` 같은 내부 식별자가 들어 있다.
   - 키 이름이 `...Id`/`...No`/`...Number`로 끝나거나 email·phone·address·account·name류를 포함하면 버린다.
     단 `categoryName`·`productName`·`bankName` 등은 설명의 핵심이라 이름으로 지정해 되살린다.
   - **정수 자릿수 10자리 초과면 버린다.** 개인 재무 금액으로 비현실적이고(10자리 = 10억 원),
     계좌·카드번호가 숫자로 들어온 경우의 방어선이다.
   - 중첩 깊이 2, 배열 3개, 문자열 120자, 항목 12개로 자른다(프롬프트 길이 = 비용 + 지시 희석).
2. **송신 직전** — `CoachSensitiveDataGuard`가 완성된 프롬프트 문자열을 다시 훑는다.
   `EMAIL`, `LONG_DIGIT_RUN`(10자리 이상 연속 숫자), `HYPHENATED_IDENTIFIER`(하이픈 묶음 숫자 총합 10자리 이상)
   중 하나라도 걸리면 **호출하지 않고** 템플릿으로 내려가며 ERROR 로그를 남긴다.
   - 날짜 `2026-09-16`은 숫자가 8자리라 걸리지 않는다. 이 경계가 규칙의 핵심이다.
   - 이름·주소처럼 형태가 없는 값은 정규식으로 못 잡는다 → 애초에 조립기가 담지 않는 것으로 처리한다.
     사용자 이름·이메일은 코치 경로의 어떤 쿼리에서도 읽지 않는다.

로그에도 프롬프트·모델 응답 본문을 남기지 않는다. 남기는 것은 topic, 위반 종류, 걸린 숫자 토큰뿐이다.

## 7. 외부 호출 실패 처리

```
사실 조립(소유권 검증) → 프롬프트 생성 → 민감정보 검사 → 외부 호출 → 숫자 검증 → 응답
                                          ↘ 걸리면        ↘ 실패하면   ↘ 걸리면
                                                     템플릿 응답(기획안 11-3의 대체 방법)
```

**어떤 실패에서도 200을 돌려준다.** 코치 문장은 진단·추천 데이터의 표현 계층이지 데이터 자체가 아니다.
외부 API가 죽었다고 502를 주면 FE는 화면을 못 그리는데, 보여줄 수치는 전부 서버에 있다. 그래서 실패를
상태 코드가 아니라 `source`/`degradedReason` 필드로 알린다. 인증·소유권 실패(401/404)는 그대로 예외로 올린다.

| `degradedReason` | 언제 |
| --- | --- |
| `AI_DISABLED` | `app.ai.enabled=false` 또는 API 키 미설정(로컬·테스트 기본값) |
| `AI_UNAVAILABLE` | 타임아웃·연결 실패·401/403/400·5xx·429(재시도 후)·응답 해석 불가 |
| `AI_EMPTY_RESPONSE` | 호출은 성공했으나 본문이 비었다 |
| `AI_UNVERIFIED_NUMBER` | §5에 걸렸다 |
| `SENSITIVE_DATA_BLOCKED` | §6-2에 걸려 호출하지 않았다. 정상 흐름에서는 발생하지 않는다(발생 = 조립기 회귀) |

재시도는 **429·5xx·네트워크 오류만** 한다(총 `max-attempts`회, 기본 2). 401·403·400은 다시 보내도 같은 답이라
사용자를 기다리게 할 뿐이므로 한 번에 포기한다. 재시도 간 대기는 넣지 않았다 — 사용자가 화면에서 기다리는
동기 요청이라 대기를 넣으면 실패 경로의 체감 지연이 타임아웃의 두 배가 된다.

## 8. 설정

```yaml
app:
  ai:
    enabled: true
    base-url: https://gms.ssafy.io/gmsapi/api.openai.com   # SSAFY GMS 중계
    chat-path: /v1/chat/completions
    model: gpt-5.4-mini
    max-tokens: 600
    max-tokens-parameter: max_completion_tokens   # 4o 계열은 max_tokens
    temperature: 0.2                              # 비우면 요청 본문에서 빠진다
    max-attempts: 2
    connect-timeout: PT3S
    read-timeout: PT12S
    api-key: ${OPENAI_API_KEY}  # application-prod.yml에만 있다
```

- 키가 비어도 **기동은 정상**이고 코치만 템플릿으로 내려간다. 키가 없다고 앱이 죽으면 로컬·테스트에서
  진단·추천 API까지 함께 막히는데, 코치는 그 데이터의 표현 계층일 뿐이라 그럴 이유가 없다.
- `api-key`를 `application-prod.yml`에 둔 이유: `check-env.sh`의 앱↔compose 대조가 그 파일만 읽는다.
  병합 중 compose의 `OPENAI_API_KEY` 전달 줄이 유실되면 배포 전에 잡힌다.
- `infra/docker-compose.yml` backend `environment:`에 `OPENAI_API_KEY: ${OPENAI_API_KEY:-}`를 추가했다.
  키는 Day 5에 발급해 EC2 `.env`에 있었지만 컨테이너로 전달되지 않는 상태였다.

### 8-1. 모델 선정과 교체

코치가 하는 일은 "이미 계산된 사실 목록 → 2문단 400자 이내 한국어"다. 추론이 필요 없는 제한된
생성 작업이고, 수치·판정은 애초에 모델을 거치지 않는다(§5). 그래서 작은 모델이면 충분하다.

**`gpt-5.4-mini`를 고른 근거** (2026-09-16 GMS 중계로 같은 프롬프트 실측):

| | gpt-4o-mini | **gpt-5.4-mini** |
| --- | --- | --- |
| 응답 시간 | 1.849s | **1.715s** |
| `reasoning_tokens` | 0 | **0** — 추론형 지연 위험 없음 |
| 도메인 해석 | ❌ 금융 진단 결과를 시스템 모니터링 지표로 오해<br>("시스템이 정상 작동", "관련 데이터가 균형") | ✅ 재무 맥락 유지<br>("지출과 수입 흐름을 꾸준히 확인하면") |
| 신뢰도 표현 | 단정 | 유보적("일부 변동 가능성은 염두에") — `disclaimer`와 같은 방향 |
| 사실에 없는 숫자 | 없음 | 없음 |

더 빠르면서 문장이 정확했다. `reasoning_tokens`가 0이라 추론형 모델에서 우려한 타임아웃 위험도 없다.

**`temperature`는 낮게 유지한다.** 재현성 때문만이 아니라, 출력이 흔들릴수록 없는 숫자를 만들 확률이
올라가 §5의 가드에 걸리는 빈도(= 템플릿으로 내려가는 빈도)가 는다. `gpt-5.4-mini`가 이 값을 받는 것은
실측으로 확인했다.

**교체는 코드 변경이 아니라 설정 변경이다.** 요청 본문 구성이 모델 계열마다 다르기 때문에 세 가지를
설정으로 뺐다.

| 설정 | 언제 바꾸나 |
| --- | --- |
| `model` | 모델 이름 |
| `max-tokens-parameter` | 상한 필드 이름이 계열마다 다르다(`max_tokens` / `max_completion_tokens`). 틀리면 공급자가 **400**으로 거부한다 |
| `temperature` | 이 값을 받지 않는 모델이면 **비운다**(비우면 본문에서 빠진다) |

**추론형 모델로 바꿀 때는 `read-timeout`도 함께 올린다.** 내부 사고에 시간을 쓰는 모델은 12초를
넘기기 쉽고, 그러면 `AI_UNAVAILABLE`로 조용히 템플릿에 내려간다 — 화면은 정상으로 보이므로
느려졌다는 사실 자체가 드러나지 않는다.

### 환경별 키 주입 경로

| 환경 | 경로 |
| --- | --- |
| 운영 | `infra/.env` → `docker-compose.yml` `environment:` → 컨테이너 환경변수 → `application-prod.yml` |
| 로컬 | 셸·IDE 환경변수 → `application-local.yml`(`${OPENAI_API_KEY:}`, **선택**) |
| 테스트 | 주입하지 않는다. `app.ai.enabled=false` |

### 8-2. 우리는 OpenAI에 직접 붙지 않는다 (SSAFY GMS 중계)

`OPENAI_API_KEY`는 **OpenAI가 아니라 SSAFY GMS가 발급한 중계 토큰**이다. 이름이 `OPENAI_API_KEY`라
오해하기 쉬우므로 여기 명시해 둔다.

| 항목 | 값 |
| --- | --- |
| 중계 주소 규칙 | `https://gms.ssafy.io/gmsapi/<원래 호스트>/<원래 경로>` |
| `base-url` | `https://gms.ssafy.io/gmsapi/api.openai.com` |
| 인증 헤더 | OpenAI와 동일 — `Authorization: Bearer <토큰>` |
| 토큰 형식 | OpenAI 형식(`sk-`)이 **아니다**. 길이도 다르다 |

> **같은 토큰을 `api.openai.com`에 직접 던지면 401이 난다. 이것은 정상이며 키 문제가 아니다.**
> 2026-09-16에 이 401을 "키가 잘렸다"로 읽고 재발급까지 갈 뻔했다(day-10 13·14절).
> 외부 API 실패를 볼 때는 "올바른 상대에게 말하고 있는가"를 응답 코드보다 먼저 확인해야 한다.

**`backend/.env`는 운영 경로에 없다.** `backend/.dockerignore`가 `.env`를 제외하고 Dockerfile도
복사하지 않는다. `backend/.env.example`은 로컬 실행 시 개발자가 직접 주입할 값의 체크리스트일 뿐이다
(Spring Boot는 `.env`를 자동으로 읽지 않는다 — `backend/README.md`).

로컬에서 키를 비워 두는 것이 기본값이다. 로컬 실행이 실수로 과금되지 않게 하기 위해서이며,
그때 코치는 `source: "TEMPLATE"` + `degradedReason: "AI_DISABLED"`로 정상 동작한다.

## 9. 테스트

| 파일 | 검증 |
| --- | --- |
| `CoachApiTest` | 401, 타인·미존재 404, 본문 검증, facts 내용, 내부 식별자·계좌번호 미포함, TEMPLATE/AI 분기 4종 |
| `CoachNumberGuardTest` | 서버 숫자 통과, 모델이 계산한 연 절감액 차단, 반올림 허용, 선행 0·목록 번호 |
| `CoachSensitiveDataGuardTest` | 이메일·계좌·휴대폰 차단, 금액·날짜·비율 통과 |
| `CoachEvidenceReaderTest` | 식별자 키 제거, 이름 키 예외 목록, 11자리 정수 차단, 중첩·배열 상한 |
| `CoachCacheKeyTest` | 같은 facts→같은 키, 값 변경→키 변경, 사용자 분리, 실패 시 null |
| `RedisCoachAnswerCacheTest` | TTL 적용, 왕복, 미스, **Redis 장애·손상값 무시**, 비활성 시 미접속 |
| `OpenAiCoachTextGeneratorTest` | 요청 형식, 401 즉시 포기, 5xx 재시도 후 실패, 429 재시도 후 성공, 비JSON, 키 없음 |

테스트는 실제 OpenAI를 호출하지 않는다(`app.ai.enabled=false` + `CoachTextGenerator` Mock).
실패 경로(타임아웃·지어낸 숫자)는 실제 호출로는 재현할 수 없다.

## 10. 응답 캐시

같은 진단으로 요약을 두 번 부르면 두 번 과금된다. 코치는 "저장된 사실 → 문장"이라 **입력이 같으면
출력이 같아도 되므로**, 모델이 쓴 문장을 재사용한다.

| 항목 | 값 |
| --- | --- |
| 저장소 | Redis (`app.coach.cache-enabled`, 기본 on) |
| 키 | `coach:<userId>:<topic>:<facts SHA-256 앞 16자>` |
| 값 | `{ message, model }` — **모델이 쓴 문장만** |
| TTL | `app.coach.cache-ttl`, 기본 1시간 |

### 무효화는 TTL이 아니라 키가 한다

키에 `CoachFacts` 해시가 들어간다. 서버 계산값이 1바이트라도 달라지면 키가 달라져 자동으로 다시
생성된다 — 재진단, 추천 상태 변경, 패턴 재분석이 모두 facts를 바꾸므로 **별도의 무효화 코드가 없다.**
TTL은 "어쩌다 이상한 문장이 생성됐을 때의 잔존 상한"일 뿐이다.

| 상황 | 결과 |
| --- | --- |
| 화면 재진입, 여러 기기, 시연 중 반복 | 적중 |
| 재진단 / 최신 진단이 바뀜 / 추천 상태 변경 / 패턴 재분석 | 갱신 (facts가 달라짐) |
| 다른 사용자 | 갱신 (키에 `userId`) |

대상 id(`diagnosisId` 등)는 키에 넣지 않는다. facts가 같으면 어느 대상이든 같은 문장이 타당하고,
같은 id라도 facts가 바뀌면 이미 다른 키다. `userId`를 넣는 것은 facts가 회원 재무 수치라서,
해시가 겹치거나 두 사용자의 facts가 같아도 서로의 문장이 섞이지 않게 하기 위해서다.

### 세 가지 규칙

1. **문장만 저장한다.** `facts`·`highlights`는 매번 DB에서 다시 만든다. 캐시가 낡아서 화면의 숫자가
   틀리는 상황이 구조적으로 불가능해진다.
2. **성공한 AI 문장만 저장한다.** 템플릿을 저장하면 공급자가 복구된 뒤에도 TTL 내내 템플릿만 나간다.
3. **캐시는 생성기 가용성보다 먼저 본다.** AI가 꺼져 있어도 받아 둔 문장이 있으면 템플릿보다 낫다.

### Redis가 죽어도 코치는 산다

**이 프로젝트에서 Redis를 실제로 쓰는 첫 코드다.** 접속 설정은 처음부터 있었지만 Java 사용처가 없었고,
비밀번호·연결 설정이 맞는지는 여기서 처음 드러난다(H2로 도는 테스트는 잡지 못한다).

그래서 `RedisCoachAnswerCache`는 **모든 예외를 삼킨다.** 조회 실패는 캐시 미스와 같게, 저장 실패는
무시로 다룬다. 캐시는 비용 절감 장치이지 기능이 아니므로, Redis 장애가 코치 장애가 되면 캐시를 넣지
않느니만 못하다. 둘 다 WARN으로 남겨 "캐시가 동작하지 않는다"는 사실 자체는 드러나게 한다.

`generatedAt`은 캐시 적중 시에도 지금 시각이다. 응답 전체가 매번 새로 조립되고 문장만 재사용하므로,
문장이 만들어진 시각보다 응답이 만들어진 시각이 맞다.

## 11. 남은 것

- **사용자별 호출 레이트 리밋이 없다.** nginx 레이트 리밋은 로그인 경로에만 걸려 있다.
- 군집 구간은 `pattern_analysis`에 완료 행이 있어야 채워진다. 진단의 `clusterContext`는 아직 항상 null이다
  (`DiagnosisEvaluator`가 `CLUSTER: UNAVAILABLE/CLUSTER_NOT_CONNECTED`를 남긴다) — 545가 붙으면 그대로 실린다.
- (해소됨) 모델 교체는 설정으로 한다 — `max-tokens-parameter`와 `temperature` 비우기. §8-1 참고.
