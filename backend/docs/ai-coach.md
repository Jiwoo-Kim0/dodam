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
| FINANCIAL_SUMMARY | 진단 개요(완료 시각·규칙 버전·종합 상태·문제 수·높은 심각도 수·목표 연계 여부) / 심각도별 문제 수 / 영역별 문제 수 / 주요 문제 3건 / 소비패턴 군집(라벨·모델 버전·표본 수·신뢰도·이탈 상위 3개 특징의 내 값·기준 범위·방향) |
| INEFFICIENCY_EXPLANATION | 문제 개요(영역·유형·심각도·탐지 방식·탐지 시각·모델 버전·규칙 버전) / 현재 값 / 기준 값 / 탐지 근거 / 군집 근거 |
| RECOMMENDATION_EXPLANATION | 개선안 개요(영역·유형·우선순위·상태·생성 시각) / 예상 효과(null 아닌 것만) / 저장된 근거(code·description·evidence) |

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
    base-url: https://api.openai.com
    chat-path: /v1/chat/completions
    model: gpt-4o-mini          # max_tokens 파라미터를 받는 모델이어야 한다(o-시리즈는 파라미터명이 다르다)
    temperature: 0.2
    max-tokens: 600
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

## 9. 테스트

| 파일 | 검증 |
| --- | --- |
| `CoachApiTest` | 401, 타인·미존재 404, 본문 검증, facts 내용, 내부 식별자·계좌번호 미포함, TEMPLATE/AI 분기 4종 |
| `CoachNumberGuardTest` | 서버 숫자 통과, 모델이 계산한 연 절감액 차단, 반올림 허용, 선행 0·목록 번호 |
| `CoachSensitiveDataGuardTest` | 이메일·계좌·휴대폰 차단, 금액·날짜·비율 통과 |
| `CoachEvidenceReaderTest` | 식별자 키 제거, 이름 키 예외 목록, 11자리 정수 차단, 중첩·배열 상한 |
| `OpenAiCoachTextGeneratorTest` | 요청 형식, 401 즉시 포기, 5xx 재시도 후 실패, 429 재시도 후 성공, 비JSON, 키 없음 |

테스트는 실제 OpenAI를 호출하지 않는다(`app.ai.enabled=false` + `CoachTextGenerator` Mock).
실패 경로(타임아웃·지어낸 숫자)는 실제 호출로는 재현할 수 없다.

## 10. 남은 것

- **응답 캐시가 없다.** 같은 진단으로 요약을 반복 호출하면 그때마다 과금된다. `(userId, topic, 대상 id,
  facts 해시)` 키로 짧은 TTL 캐시를 두는 것이 다음 단계다(Redis는 이미 붙어 있고 아직 사용처가 없다).
- **사용자별 호출 레이트 리밋이 없다.** nginx 레이트 리밋은 로그인 경로에만 걸려 있다.
- 군집 구간은 `pattern_analysis`에 완료 행이 있어야 채워진다. 진단의 `clusterContext`는 아직 항상 null이다
  (`DiagnosisEvaluator`가 `CLUSTER: UNAVAILABLE/CLUSTER_NOT_CONNECTED`를 남긴다) — 545가 붙으면 그대로 실린다.
- 모델을 o-시리즈로 바꾸려면 `max_tokens` → `max_completion_tokens` 파라미터명 변경이 필요하다.
