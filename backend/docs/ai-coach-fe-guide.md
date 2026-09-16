# AI Coach API — FE 연동 가이드 (API 21 / S15P21D105-589)

작성일: 2026-09-16. 구현 상세는 [ai-coach.md](ai-coach.md)에 있고, 이 문서는 **화면을 붙이는 데 필요한 것만** 담는다.

## 0. 결론 — 지금 바로 붙일 수 있다

이미 배포되어 200을 돌려주고 있다. 다음 세 가지를 **기다릴 필요 없다.**

| 기다릴 이유? | 답 |
| --- | --- |
| 응답 캐시 작업 | ❌ 응답 스키마가 바뀌지 않는다 |
| 다음 배포 | ❌ 바뀌는 것은 `source`가 `TEMPLATE` → `AI`로, `message` 문장이 나아지는 것뿐. **계약은 동일** |
| AI 연동 완료 | ❌ AI가 죽어 있어도 200 + 같은 스키마다 |

**세 엔드포인트가 같은 응답 형식을 쓴다.** 다른 것은 `topic`과 `facts`의 내용뿐이라, 컴포넌트 하나로 세 화면을 다 그릴 수 있다.

## 1. 엔드포인트

| 용도 | Method · Path | Body |
| --- | --- | --- |
| 금융현황 요약 | POST `/api/v1/coach/financial-summary` | `{ "diagnosisId": 12 }` 또는 `{}` (생략 시 최신 완료 진단) |
| 비효율 원인 설명 | POST `/api/v1/coach/inefficiency-explanation` | `{ "diagnosisId": 12, "issueId": 34 }` |
| 추천 근거 설명 | POST `/api/v1/coach/recommendation-explanation` | `{ "recommendationId": 56 }` |

전부 인증 필요(Bearer). GET이 아니라 POST지만 **부수효과가 없고 몇 번 호출해도 안전하다**(캐시가 붙으면 두 번째부터 즉시 응답).

## 2. 응답

```json
{
  "success": true,
  "data": {
    "topic": "RECOMMENDATION_EXPLANATION",
    "source": "AI",
    "degradedReason": null,
    "model": "gpt-5.4-mini",
    "generatedAt": "2026-09-16T14:35:00",
    "subject": "쓰지 않는 구독을 정리하세요",
    "message": "매달 12,000원을 아낄 수 있는 개선안입니다.\n\n사용 기록을 확인해 보세요.",
    "highlights": ["영역: SPENDING", "유형: CANCEL_SUBSCRIPTION", "우선순위(서버 계산): 1"],
    "facts": {
      "topic": "RECOMMENDATION_EXPLANATION",
      "subject": "쓰지 않는 구독을 정리하세요",
      "sections": [
        { "title": "개선안 개요", "facts": [{ "label": "영역", "value": "SPENDING" }] },
        { "title": "예상 효과(서버 계산)", "facts": [{ "label": "월 절감액", "value": "12,000원" }] }
      ],
      "notes": ["우선순위와 효과 금액은 서버 추천 엔진이 계산해 저장한 값이다. ..."]
    },
    "disclaimer": "이 설명은 회원님의 등록된 금융 데이터로 서버가 계산한 결과를 문장으로 옮긴 것이며, 투자·금융 자문이 아닙니다."
  },
  "message": null,
  "code": null
}
```

| 필드 | 설명 |
| --- | --- |
| `topic` | `FINANCIAL_SUMMARY` \| `INEFFICIENCY_EXPLANATION` \| `RECOMMENDATION_EXPLANATION` |
| `source` | `AI` \| `TEMPLATE` — 문장을 누가 썼는지 |
| `degradedReason` | 템플릿으로 내려간 사유. `source=AI`면 `null` |
| `model` | 문장을 쓴 모델명. `source=TEMPLATE`이면 `null` |
| `subject` | 설명 대상 한 줄. 화면 제목으로 쓰기 좋다 |
| `message` | **본문. 유일하게 모델이 쓴 부분.** 평문, 문단 구분은 `\n\n`, 최대 2문단·400자 |
| `highlights` | 핵심 수치 문자열 배열(최대 5개). **서버가 만든다 — AI 경로에서도 모델을 거치지 않는다** |
| `facts` | 서버 계산값 전체. 화면의 수치는 전부 여기서 온다 |
| `facts.sections[].facts[]` | `{ label, value }`. `value`는 **이미 포맷된 문자열**(`"12,000원"`, `"62.5%"`, `"3건"`) — FE가 다시 포맷하지 않는다 |
| `facts.notes` | 모델에게 준 주의사항. **화면에 노출하지 않는다**(디버깅용) |
| `disclaimer` | 고지 문구. 금융 앱이므로 화면에 함께 노출한다 |

## 3. 렌더링 규칙 6가지

**① 화면의 숫자는 `message`에서 파싱하지 않는다.** 가장 중요하다.
`facts.sections[].facts[]`와 `highlights[]`를 렌더링한다. `message`는 설명 문장이고 문장은 모델이 쓴다.
수치·판정은 언제나 `facts` 쪽에 있고 그쪽만 서버 계산값이다.

**② `source`로 에러 UI를 띄우지 않는다.**
`TEMPLATE`은 실패가 아니라 "문장을 서버가 썼다"는 뜻이다. 수치·판정은 `AI`일 때와 완전히 동일하다.
에러 화면을 띄우면 정상 데이터를 사용자에게서 숨기는 셈이 된다.

**③ `facts.sections`는 개수·제목이 고정이 아니다.**
데이터 상태에 따라 구간이 빠진다(군집 결과가 없으면 군집 구간 없음, 효과 금액이 없으면 효과 구간 없음).
`title`을 하드코딩해 인덱스로 찾지 말고 **순서대로 렌더링**한다. 앞쪽이 더 중요한 구간이다.

**④ `message`는 평문이다.** 마크다운이 아니다. `\n\n`으로 문단만 나누면 된다.

**⑤ `disclaimer`를 함께 노출한다.**

**⑥ `facts.notes`는 화면에 쓰지 않는다.** 모델에게 준 지시문이라 사용자에게 보여줄 문장이 아니다.

## 4. 상태별 UI

| `source` | `degradedReason` | 화면 |
| --- | --- | --- |
| `AI` | `null` | "AI 생성" 배지(정책은 팀 결정) + `message` |
| `TEMPLATE` | `AI_DISABLED` | 배지 없이 `message` 그대로. 정상 화면 |
| `TEMPLATE` | `AI_UNAVAILABLE` | 〃 |
| `TEMPLATE` | `AI_EMPTY_RESPONSE` | 〃 |
| `TEMPLATE` | `AI_UNVERIFIED_NUMBER` | 〃 (모델이 근거 없는 숫자를 써서 서버가 걸러낸 경우) |
| `TEMPLATE` | `SENSITIVE_DATA_BLOCKED` | 〃 (정상 흐름에서는 발생하지 않는다) |

**모든 경우 화면은 같다.** 달라지는 것은 배지 하나뿐이다.

## 5. 에러

| 상태 | `code` | 언제 | 화면 |
| --- | --- | --- | --- |
| 401 | — | 토큰 없음·만료 | 기존 인증 흐름 |
| **404** | `RESOURCE_NOT_FOUND` | **완료된 진단이 없다** / 남의 리소스 / 없는 id | "먼저 진단을 실행해 주세요" 유도 |
| 400 | `VALIDATION_ERROR` | 필수 id 누락, 0·음수 | 호출 코드 버그 |

404가 가장 자주 만날 것이다. 신규 가입자는 진단이 없으므로 **요약 화면 진입 전에 진단 실행을 유도**해야 한다.

## 6. 타입 (`src/types/coachApi.types.ts`)

```ts
export type CoachTopic =
  | 'FINANCIAL_SUMMARY'
  | 'INEFFICIENCY_EXPLANATION'
  | 'RECOMMENDATION_EXPLANATION';

export type CoachSource = 'AI' | 'TEMPLATE';

export type CoachDegradedReason =
  | 'AI_DISABLED'
  | 'AI_UNAVAILABLE'
  | 'AI_EMPTY_RESPONSE'
  | 'AI_UNVERIFIED_NUMBER'
  | 'SENSITIVE_DATA_BLOCKED';

export interface CoachFact {
  label: string;
  value: string;   // 이미 포맷된 문자열. 다시 포맷하지 않는다
}

export interface CoachFactSection {
  title: string;
  facts: CoachFact[];
}

export interface CoachFacts {
  topic: CoachTopic;
  subject: string;
  sections: CoachFactSection[];
  notes: string[];   // 화면에 노출하지 않는다
}

export interface CoachMessageResponse {
  topic: CoachTopic;
  source: CoachSource;
  degradedReason: CoachDegradedReason | null;
  model: string | null;
  generatedAt: string;
  subject: string;
  message: string;
  highlights: string[];
  facts: CoachFacts;
  disclaimer: string;
}

export interface FinancialSummaryRequest { diagnosisId?: number }
export interface InefficiencyExplanationRequest { diagnosisId: number; issueId: number }
export interface RecommendationExplanationRequest { recommendationId: number }
```

## 7. API 모듈 (`src/api/coachApi.ts`)

```ts
import type {
  CoachMessageResponse,
  FinancialSummaryRequest,
  InefficiencyExplanationRequest,
  RecommendationExplanationRequest,
} from '@/types/coachApi.types';

import { apiClient } from './apiClient';

const COACH_PATH = '/v1/coach';

export function getFinancialSummary(request: FinancialSummaryRequest = {}) {
  return apiClient.post<CoachMessageResponse>(`${COACH_PATH}/financial-summary`, { body: request });
}

export function getInefficiencyExplanation(request: InefficiencyExplanationRequest) {
  return apiClient.post<CoachMessageResponse>(`${COACH_PATH}/inefficiency-explanation`, { body: request });
}

export function getRecommendationExplanation(request: RecommendationExplanationRequest) {
  return apiClient.post<CoachMessageResponse>(`${COACH_PATH}/recommendation-explanation`, { body: request });
}
```

## 8. 어느 화면에서 쓰나

| 화면 | 호출 | 필요한 선행 데이터 |
| --- | --- | --- |
| 진단 결과 / 홈 요약 | `financial-summary` | 완료된 진단 1건. `diagnosisId` 생략 가능 |
| 진단 문제 상세 | `inefficiency-explanation` | `GET /api/v1/diagnoses/{id}/issues`의 `issueId` |
| 개선안 상세 | `recommendation-explanation` | `GET /api/v1/recommendations`의 `recommendationId` |

## 9. 지금 붙일 때 주의

- **다음 배포 전까지는 항상 `source: "TEMPLATE"`이 나온다. 정상이다.** 서버가 GMS 중계 주소를 아직 반영하지 않은 상태다.
  `source: "AI"` 분기의 실동작 확인만 배포 이후로 미루면 되고, 나머지는 지금 다 만들 수 있다.
- **응답에 2초 안팎 걸린다**(실측 1.7초). 로딩 UI가 필요하다. 캐시가 붙으면 두 번째 호출부터 즉시 응답한다.
- **`message` 문구는 호출마다 달라질 수 있다.** 스냅샷 테스트를 `message`에 걸지 말 것. 고정된 것은 `facts`다.

## 10. 확인용 curl

```bash
TOKEN=$(curl -s -X POST https://j15d105.p.ssafy.io/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seed.kmeans.u0001@dodam.invalid","password":"dodam1234"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

curl -s -X POST https://j15d105.p.ssafy.io/api/v1/coach/financial-summary \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}' \
  | python3 -m json.tool
```

404가 나오면 진단이 없는 것이다. `POST /api/v1/diagnoses`로 먼저 만든다.
