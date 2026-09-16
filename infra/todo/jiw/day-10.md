# Day 10 실제 진행 기록

## 2026-09-16

### 1. 작업 범위 확인 — API 21 AI Coach (S15P21D105-589)

명세(`docs/API명세서/21 AI Coach ....csv`)가 정의한 3종:

| API명 | Method | Endpoint | Body | 제한사항(명세 원문) |
| --- | --- | --- | --- | --- |
| 금융현황 AI 요약 | POST | `/api/v1/coach/financial-summary` | `diagnosisId?` | 서버 계산 결과만 입력으로 사용; AI는 설명 생성; 군집 라벨·기준 범위는 서버 계산값만 전달 |
| 비효율 원인 설명 | POST | `/api/v1/coach/inefficiency-explanation` | `diagnosisId, issueId` | Diagnosis 결과 기반; AI 자체 판정 금지; 군집 근거는 서버가 계산해 프롬프트 입력에만 포함(군집 판정 금지) |
| 추천 근거 설명 | POST | `/api/v1/coach/recommendation-explanation` | `recommendationId` | Recommendation 계산 결과 기반; AI 자체 계산 금지 |

완료 기준(사용자 지시):
1. 인증·소유권 검증
2. 서버 계산 결과만 AI에 전달
3. AI 자체 계산·판정 금지
4. 외부 호출 실패 처리
5. 민감정보 제외
6. Day 11 Improvement/Report 응답 계약 FE 선공유

### 2. 사전 조사 — 붙일 수 있는 서버 계산 결과가 이미 있는가

| 출처 | 확인 내용 |
| --- | --- |
| `domain/diagnosis` | `Diagnosis`(집계값 + `inputSnapshot`), `DiagnosisIssue`(`currentValue`/`benchmarkValue`/`evidence`/`clusterContext` JSON). 소유권은 `DiagnosisIssueRepository`가 상위 `diagnosis`를 조인해 쿼리 안에서 건다 |
| `domain/recommendation` | `Recommendation`(효과 8종) + `RecommendationReason`(code/description/evidence). `RecommendationQueryService`가 "저장값 그대로, 재계산 없음"을 이미 계약으로 못박아 둠 |
| `domain/pattern` | `ClusterProfileResponse` — 군집 라벨·표본 수·신뢰도·특징별 기준 범위(p5/p95/mean/std/direction)가 **이미 서버 계산값으로 저장**되어 있다. 명세의 "군집 라벨·기준 범위는 서버 계산값만 전달"이 그대로 충족된다 |

→ **AI가 계산할 것이 하나도 없다.** 세 API 모두 "이미 계산돼 저장된 값을 문장으로 바꾸는" 일만 남는다.
   이 사실이 아래 설계(사실 목록 → 프롬프트 → 숫자 검증)의 근거다.

### 3. 외부 AI 공급자 확인

- `infra/.env.example:36` `OPENAI_API_KEY=`, `infra/todo/day-05.md` "OpenAI API Key | AI 코치 | ✅ 발급됨(47자) | EC2 `infra/.env`"
- `infra/todo/jiw/day-07.md` — backend 컨테이너 → `api.openai.com` HTTP 401(= 네트워크 열림) 확인 완료
- **그러나 `infra/docker-compose.yml`의 backend `environment:` 블록에 `OPENAI_API_KEY`가 없다.**
  키는 EC2 `.env`에 있는데 컨테이너에 전달되지 않는 상태 → 이번 작업에서 함께 추가한다.
- 기획안 11-3 표: AI 금융코치의 대체 방법이 **"템플릿 기반 응답"**으로 이미 정해져 있다 → 실패 처리 설계의 근거.

### 4. 설계 결정 — "AI가 계산하지 않는다"를 어떻게 보장할 것인가

명세의 세 줄(서버 계산 결과만 입력 / AI 자체 판정 금지 / AI 자체 계산 금지)은 프롬프트에
"계산하지 마라"라고 쓰는 것으로는 **보장이 되지 않는다.** 지켜지는 편이지만 지켜졌는지 확인할 방법이 없다.
포트폴리오에서 이 부분이 가장 설명할 값어치가 있다고 보고 두 겹으로 나눴다.

| 겹 | 무엇 | 성격 |
| --- | --- | --- |
| 1차 | 시스템 프롬프트의 금지 규칙 6줄 + 데이터 상태별 `[주의]` 줄 | 지켜지길 **바라는** 것 |
| 2차 | `CoachNumberGuard` — 모델 문장의 모든 숫자가 서버가 준 숫자인지 검사 | 지켜졌는지 **확인하는** 것 |

구체적인 시나리오로 잡은 것: 서버가 **월** 절감액 12,000원만 저장했는데(연 절감액은 `null`) 모델이
"연 144,000원"을 만들어 내는 경우. 문장은 그럴듯하고 검토자가 알아채기 어렵다. 실제로 연 절감액은
서버가 `ANNUAL_IS_MONTHLY_TIMES_12` 가정으로 계산해 저장하는 값이라 모델이 만들 이유가 없다.
→ 걸리면 **문장을 버리고 템플릿으로 내려간다.** 틀린 숫자보다 덜 매끄러운 문장이 낫다는 판단.

이 검사가 성립하려면 **숫자 포맷을 서버가 확정**해야 한다. `0.6234`를 넘기고 "62.3%로 바꿔 써라"를
시키는 순간 그것이 계산이기 때문이다. 그래서 `CoachFacts`의 모든 값이 `String`이고, 그 문자열들이
곧 허용 숫자 목록이 된다. 설계가 한 바퀴 맞물린다.

**반올림을 허용한 이유**: 막으면 "약 63%" 같은 정상 문장이 계속 탈락해 템플릿 비율만 올라간다.
각 숫자의 정수부·소수 1자리 반올림·버림·올림까지 같은 숫자로 인정한다.

### 5. 설계 결정 — 실패해도 200을 준다

기획안 11-3 표가 AI 금융코치의 대체 방법을 **"템플릿 기반 응답"**으로 이미 정해 놓았다. 이것을
"AI가 죽었을 때의 비상구"가 아니라 **동등한 두 경로 중 하나**로 구현했다.

- AI 경로와 템플릿 경로가 **같은 `CoachFacts`를 쓰고 응답 스키마도 같다.** FE는 `source`만 보고
  배지를 바꿀 뿐 분기하지 않는다.
- 수치(`facts`, `highlights`)는 **어느 경로에서도 모델을 거치지 않는다.** 모델이 쓰는 것은 `message` 하나다.
- 외부 API가 죽었다고 502를 주면 FE는 화면을 못 그리는데, 정작 보여줄 수치는 전부 서버에 있다.
  그래서 실패를 상태 코드가 아니라 `source`/`degradedReason` 필드로 알린다.
  (인증·소유권 실패 401/404는 그대로 예외로 올린다 — 그쪽은 데이터가 없는 것이 맞다.)

`degradedReason` 5종: `AI_DISABLED`, `AI_UNAVAILABLE`, `AI_EMPTY_RESPONSE`, `AI_UNVERIFIED_NUMBER`,
`SENSITIVE_DATA_BLOCKED`.

재시도는 **429·5xx·네트워크 오류만**(총 2회). 401·403·400은 다시 보내도 같은 답이라 한 번에 포기한다.
재시도 간 대기는 넣지 않았다 — 사용자가 화면에서 기다리는 동기 요청이라 대기를 넣으면 실패 경로의
체감 지연이 타임아웃의 두 배가 된다.

### 6. 설계 결정 — 민감정보 제외를 2단으로

문제의 출발점: 진단·추천의 JSON 컬럼(`currentValue`·`benchmarkValue`·`evidence`·`clusterContext`)은
**의도적으로 스키마를 고정하지 않았다**(엔티티 주석에 근거가 적혀 있다). 통째로 직렬화해 넘기면
진단 규칙이 하나 늘 때마다 외부 API로 나가는 내용이 코드 변경 없이 넓어진다. 게다가 지금도
`DiagnosisEvaluator`가 `targetId`·`forecastId` 같은 내부 식별자를 넣고 있다.

1. **조립 단계** `CoachEvidenceReader` — 키 이름 규칙(`...Id`/`...No`/email·phone·account·name류 제거,
   단 `categoryName`·`productName` 등은 예외로 되살림) + **정수 10자리 초과 제거**(계좌·카드번호가
   숫자로 들어온 경우) + 깊이·개수·길이 상한.
2. **송신 직전** `CoachSensitiveDataGuard` — 완성된 프롬프트를 다시 훑어 EMAIL / 10자리 이상 연속 숫자 /
   하이픈 묶음 숫자 총합 10자리 이상을 찾으면 **호출하지 않는다.**

경계 설계에서 가장 신경 쓴 것: **날짜 `2026-09-16`은 숫자가 8자리**라 계좌번호 규칙에 걸리지 않고,
**금액은 천단위 구분 기호가 붙어** 긴 숫자열로 보이지 않는다. 이 두 가지가 어긋나면 정상 값이
민감정보로 걸려 매번 템플릿으로 내려갔을 것이다. 그래서 §4의 "서버가 포맷한다"가 여기서도 전제가 된다.

이름·주소처럼 형태가 없는 값은 정규식으로 못 잡으므로 **애초에 조립기가 담지 않는 것**으로 처리했다.
코치 경로의 어떤 쿼리도 사용자 이름·이메일을 읽지 않는다.

### 7. 구현한 파일

**도메인** `backend/src/main/java/com/ssafy/dodam/domain/coach/`

| 파일 | 역할 |
| --- | --- |
| `controller/CoachController.java` | POST 3종. 사용자 ID는 토큰에서만 온다(본문에 userId 없음) |
| `dto/CoachFacts.java` · `CoachTopic.java` | 프롬프트 입력 전체. 이 레코드 밖의 값은 나가지 않는다 |
| `dto/request/*.java` (3) | 명세 그대로의 본문 3종 |
| `dto/response/CoachMessageResponse.java` · `CoachSource` · `CoachDegradedReason` | 3종 공통 응답 |
| `service/CoachFactAssembler.java` | **소유권 검증 + 사실 조립.** 재계산 없음 |
| `service/CoachEvidenceReader.java` | 스키마 미고정 JSON에서 실을 값 선별 |
| `service/CoachFormat.java` | 금액·비율·날짜 표기 확정(천단위 구분 기호 포함) |
| `service/CoachPromptFactory.java` | 시스템·사용자 프롬프트 |
| `service/CoachNumberGuard.java` | 모델 문장의 숫자 검증 |
| `service/CoachSensitiveDataGuard.java` | 송신 직전 민감정보 검사 |
| `service/CoachTemplateWriter.java` | 템플릿 문장 + `highlights`(AI 경로에서도 이쪽이 만든다) |
| `service/CoachService.java` | 전체 흐름 + 실패 대체 |
| `service/CoachTextGenerator.java` · `CoachGenerationException.java` · `CoachClock.java` | 외부 호출 포트 |

**인프라** `infrastructure/external/ai/` — `AiCoachProperties.java`, `OpenAiCoachTextGenerator.java`

**설정**
- `application.yml` — `app.ai` 블록 신규(공급자·모델·타임아웃·재시도)
- `application-prod.yml` — `app.ai.api-key: ${OPENAI_API_KEY}`
- `application-test.yml` — `app.ai.enabled: false` (테스트는 외부 호출 안 함)
- `infra/docker-compose.yml` — backend `environment:`에 `OPENAI_API_KEY: ${OPENAI_API_KEY:-}` 추가

**문서**
- `backend/docs/ai-coach.md` — 구현 문서
- `backend/docs/improvement-report-contract.md` — **Day 11 Improvement/Report 응답 계약 (FE 선공유)**

### 8. `OPENAI_API_KEY` 전달 경로 — `:-` 기본값을 쓴 이유

`check-env.sh`의 두 검사가 서로 다르게 동작한다.

| 검사 | 읽는 곳 | 동작 |
| --- | --- | --- |
| 3) 필수 변수 | compose의 `${VAR}` (기본값 없는 것만) | `.env`에 값이 없으면 **배포 중단** |
| 3-1) 앱↔compose 대조 | `application-prod.yml`의 `${VAR}` | compose가 그 키를 전달하지 않으면 **배포 중단** |

그래서 조합을 이렇게 잡았다.

- `application-prod.yml`에 `${OPENAI_API_KEY}` → **3-1이 감시한다**(병합 중 compose 줄이 유실되면 잡힘)
- compose에 `${OPENAI_API_KEY:-}` → **3에는 안 걸린다**(값이 비어도 배포를 막지 않음)

키가 비면 코치만 템플릿으로 내려가고 나머지는 정상이다. 배포를 막을 사안이 아니라고 판단했다.
소셜 로그인 2줄이 겪은 "병합 중 유실" 사고의 반대 방향 대비다.

### 9. 테스트 — 그리고 도중에 터진 것

작성한 테스트 30건(전부 통과):

| 파일 | 검증 |
| --- | --- |
| `CoachApiTest` (11) | 401 / 타인·미존재 404 / 본문 검증 / facts 내용 / **내부 식별자·계좌번호 미포함** / TEMPLATE·AI 분기 4종 |
| `CoachNumberGuardTest` (5) | 서버 숫자 통과, **모델이 계산한 연 144,000원 차단**, 반올림 허용, 선행 0·목록 번호 |
| `CoachSensitiveDataGuardTest` (2) | 이메일·계좌·휴대폰 차단 / **금액·날짜·비율은 통과**(오탐 방지가 절반의 요구사항) |
| `CoachEvidenceReaderTest` (5) | 식별자 키 제거, 이름 키 예외, 11자리 정수 차단, 중첩·배열 상한 |
| `OpenAiCoachTextGeneratorTest` (7) | 요청 형식, 401 즉시 포기, 5xx 재시도 후 실패, 429 재시도 후 성공, 비JSON, 키 없음 |

테스트는 실제 OpenAI를 호출하지 않는다(`enabled: false` + `CoachTextGenerator` Mock).
타임아웃·지어낸 숫자 같은 실패 경로는 실제 호출로는 재현할 수 없다.

#### 무관한 테스트 98건이 깨진 사건 (기록해 둘 값어치가 있는 것)

코치 테스트 30건은 다 통과하는데 **전체 실행에서 user·transaction·subscription 테스트 98건이
`Table "auth_session" not found`로 깨졌다.** 내가 건드린 적 없는 영역이다.

확인 절차:
1. `git stash`로 작업을 통째로 빼고 전체 실행 → **1058건 전부 통과.** 내 변경이 원인이 맞다.
2. 두 실행의 로그에서 Hikari 커넥션 풀 번호를 비교 → 깨끗한 트리 **32개**, 내 작업 포함 **33개**.

원인: **Spring TestContext 캐시의 기본 상한이 32**다. 상한을 넘으면 LRU로 컨텍스트를 닫는데,
닫히는 컨텍스트의 `ddl-auto: create-drop`이 H2 인메모리 DB(`jdbc:h2:mem:dodam`)의 **스키마를 드롭**한다.
그 DB를 공유하는 다른 캐시 컨텍스트들은 그때부터 전부 실패한다.

즉 이 레포는 **이미 상한 바로 앞(32/32)에 서 있었고**, 통합 테스트를 하나만 더 추가하면
누가 추가하든 무관한 테스트가 깨지는 상태였다. 내 테스트가 그 33번째였을 뿐이다.

조치: `backend/build.gradle`의 `test` 태스크에
`systemProperty 'spring.test.context.cache.maxSize', '64'`를 추가하고 위 경위를 주석으로 남겼다.
→ 전체 **1088건 통과**(기존 1058 + 신규 30).

### 10. 로컬에 JDK가 없어 직접 받아 검증했다

이 WSL 환경에는 JDK도 Docker도 없어(`JAVA_HOME` 미설정, Docker Desktop WSL 통합 꺼짐)
처음에는 컴파일조차 못 했다. Jenkins에 올려서 확인하면 피드백 주기가 너무 길어지므로
Temurin JDK 21 tarball(198MB)을 임시 디렉터리에 받아 압축만 풀고 `JAVA_HOME`을 지정해 사용했다.
sudo 없이 되고 레포를 오염시키지 않는다. §9의 98건 사건은 이 환경이 없었으면 **MR 이후에**
발견됐을 것이다.

## 남은 것

- [ ] **응답 캐시가 없다.** 같은 진단으로 요약을 반복 호출하면 그때마다 과금된다.
      `(userId, topic, 대상 id, facts 해시)` 키 + 짧은 TTL이 다음 단계다.
      Redis는 이미 붙어 있고 아직 사용처가 하나도 없다(compose 주석에도 그렇게 적혀 있다)
- [ ] **사용자별 코치 호출 레이트 리밋이 없다.** nginx 레이트 리밋은 로그인 경로에만 걸려 있다.
      외부 과금이 걸린 유일한 엔드포인트라 우선순위가 낮지 않다
- [ ] OpenAI 사용량 한도·결제 알림 설정 (Day 7부터 넘어온 **사용자 액션**). 이제 실제로 호출이 나간다
- [ ] 배포 후 `.env`의 `OPENAI_API_KEY`가 컨테이너에 실제로 주입되는지 확인
      (`docker compose exec backend printenv OPENAI_API_KEY`)
- [ ] 배포 후 코치 3종 스모크 — 키가 있는 상태에서 `source: "AI"`가 나오는지, 키를 비우면
      `TEMPLATE` + `AI_DISABLED`로 내려가는지
- [ ] 진단의 `clusterContext`는 아직 항상 null이다(`DiagnosisEvaluator`가
      `CLUSTER: UNAVAILABLE/CLUSTER_NOT_CONNECTED`를 남긴다). 545가 붙으면 군집 근거가 그대로 실린다
- [ ] 모델을 o-시리즈로 바꾸려면 `max_tokens` → `max_completion_tokens` 파라미터명 변경이 필요하다
- [ ] `docs/API명세서/21 AI Coach ....csv`의 진행상태가 아직 "시작 전"이다 (Notion 내보내기 파일이라
      레포에서 고치면 다음 내보내기에 덮인다 — **Notion에서 갱신할 것**)
- [ ] Day 11 계약서(`backend/docs/improvement-report-contract.md`)를 FE에 전달하고 필드 합의받기
