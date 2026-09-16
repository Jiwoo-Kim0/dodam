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

### 11. 커밋

`012a2e9` — `S15P21D105-589 feat: AI Coach API 3종 구현 및 Day 11 응답 계약 선공유 **#in-progress**`
(38 files, +2884 / -2). 브랜치 `S15P21D105-589-AI-coach`. push는 아직 안 했다.

`#done`이 아니라 **`#in-progress`**로 단 이유: 아래 "남은 것"에 응답 캐시·레이트 리밋처럼
**코드로 남은 항목**이 있다. 기능이 동작한다는 것과 티켓이 끝났다는 것은 다르다.
(처음에 `#done`으로 쓰려던 것을 사용자가 잡아 주었다.)

## 남은 것

- [ ] **응답 캐시가 없다.** 같은 진단으로 요약을 반복 호출하면 그때마다 과금된다.
      `(userId, topic, 대상 id, facts 해시)` 키 + 짧은 TTL이 다음 단계다.
      Redis는 이미 붙어 있고 아직 사용처가 하나도 없다(compose 주석에도 그렇게 적혀 있다)
- [ ] **사용자별 코치 호출 레이트 리밋이 없다.** nginx 레이트 리밋은 로그인 경로에만 걸려 있다.
      외부 과금이 걸린 유일한 엔드포인트라 우선순위가 낮지 않다
- [ ] **GMS 쿼터·사용량 확인** (**사용자 액션**). Day 7부터 "OpenAI 콘솔 결제 알림"으로 적혀 있던
      항목인데, 14·16절에서 GMS 중계로 밝혀져 성격이 바뀌었다. 우리는 OpenAI 계정을 소유하지
      않으므로 OpenAI 콘솔에서 할 일이 없다. 대신 **GMS가 계정별로 거는 호출 한도**가 실질적인
      상한이며, 그것이 코치의 가용성을 좌우한다. 한도 초과 시 GMS가 무엇을 돌려주는지
      (429인지 다른 코드인지) 확인해 두면 `degradedReason` 해석이 정확해진다
- [x] ~~배포 후 `OPENAI_API_KEY` 컨테이너 주입 확인~~ — 13절, `AI_DISABLED`가 아닌 `AI_UNAVAILABLE`로 확인됨
- [x] ~~OpenAI 키 재발급~~ — **불필요**. GMS 중계 토큰이고 올바른 주소로는 200이다 (14·16절)
- [ ] 배포 후 코치 재호출로 `source: "AI"` 확인 (16절 base-url 반영분)
- [x] ~~`gpt-5.4-mini` 재측정·비교~~ — 17절, 채택(더 빠르고 도메인 해석 정확)
- [ ] 배포 후 실제 응답을 보고 프롬프트에 "대화를 이어가겠다고 제안하지 않는다" 추가 여부 판단 (17절)
- [ ] `check-env.sh`에 CRLF 검사와 키 형식(접두사·최소 길이) 검사 추가 (13절 사각지대 2개)
- [ ] 배포 후 코치 **3종 모두** 스모크 — 요약은 16절에서 경로가 확인됐지만
      `inefficiency-explanation`·`recommendation-explanation`은 아직 한 번도 실호출되지 않았다.
      진단 문제가 0건이라 대상이 없었다(시연 계정에 문제가 탐지되는 진단이 필요하다)
- [ ] 진단의 `clusterContext`는 아직 항상 null이다(`DiagnosisEvaluator`가
      `CLUSTER: UNAVAILABLE/CLUSTER_NOT_CONNECTED`를 남긴다). 545가 붙으면 군집 근거가 그대로 실린다
- [x] ~~모델 교체 시 파라미터명 변경~~ — 15절에서 설정으로 뺐고 17절에서 실제로 교체했다
- [ ] `docs/API명세서/21 AI Coach ....csv`의 진행상태가 아직 "시작 전"이다 (Notion 내보내기 파일이라
      레포에서 고치면 다음 내보내기에 덮인다 — **Notion에서 갱신할 것**)
- [ ] Day 11 계약서(`backend/docs/improvement-report-contract.md`)를 FE에 전달하고 필드 합의받기

### 12. `backend/.env`에도 키가 필요한가 — 아니다 (경로 정리)

사용자 질문: "OPENAI_API_KEY는 infra/.env 말고도 backend/.env 안에도 들어가 있어야 하나?"

**아니다.** 운영 경로에 `backend/.env`는 존재하지 않는다.

```
infra/.env → docker-compose.yml environment: → 컨테이너 환경변수 → application-prod.yml ${OPENAI_API_KEY}
```

- `backend/.dockerignore:3`이 `.env`를 제외하고 Dockerfile도 복사하지 않는다.
- `backend/.env.example`은 **로컬 실행용 체크리스트**다. Spring Boot는 `.env`를 자동으로 읽지 않으며
  (`backend/README.md`) 개발자가 셸·IDE에 직접 주입한다.

다만 질문 덕분에 구멍이 하나 드러났다. `api-key`를 `application-prod.yml`에만 넣어서
**로컬에서는 코치가 항상 TEMPLATE로만 내려간다.** 기본값으로는 맞다(로컬 실행이 실수로
과금되지 않는다). 문제는 그 이유가 아무 데도 적혀 있지 않아 다음 사람이 "왜 AI 응답이 안 나오지"로
시간을 쓴다는 것. 경로를 열되 기본값은 유지하는 쪽으로 정리했다.

- `application-local.yml` — `api-key: ${OPENAI_API_KEY:}` (**선택**, 비면 템플릿)
- `backend/.env.example` — `OPENAI_API_KEY=` + "선택" 주석
- `backend/README.md` 환경변수 표 — 한 줄 추가
- `backend/docs/ai-coach.md` §8 — 환경별 주입 경로 표 추가

#### 함께 발견한 것 (589 범위 밖, 손대지 않음)

`backend/.env.example`과 `README.md` 환경변수 표에 **`NUMBER_HASH_KEY`가 빠져 있다.**
`application-local.yml`은 `app.security.number-hash-key: ${NUMBER_HASH_KEY}`를 기본값 없이 참조하므로,
목록만 보고 로컬을 띄우면 **기동 실패**한다. 계좌·카드번호 HMAC 키라 한번 정하면 고정해야 하는
값이기도 하다(compose 주석). 494/보안 설정 담당 영역이라 임의로 고치지 않고 남겨 둔다.

### 13. 배포 후 스모크 — 키 주입은 성공, OpenAI가 401로 거부

시연 계정 U0001로 진단(`diagnosisId=2`) 후 `POST /api/v1/coach/financial-summary` 호출.

**동작한 것**

- `facts`의 군집 구간이 **운영 데이터로 채워졌다** — 라벨 `균형형`, 표본 1,000건, 신뢰도 74.3%,
  특징 3종의 내 값/기준 범위(WITHIN). 명세 21의 "군집 라벨·기준 범위는 서버 계산값만 전달"이
  실데이터로 확인됐다.
- 템플릿 경로 정상. 응답 200, `highlights`·`disclaimer` 모두 정상.
- `종합 상태: 미기록` — §7에서 넣은 `text()` null 가드가 동작했다. 없었으면 프롬프트와 화면에
  `null`이라는 단어가 그대로 실렸을 것이다.

**동작하지 않은 것**

```
WARN c.s.d.domain.coach.service.CoachService :
  Coach generation failed, falling back to template:
  topic=FINANCIAL_SUMMARY reason=OpenAI rejected the request with status 401
```

`AI_DISABLED`가 아니라 `AI_UNAVAILABLE`이라는 점이 중요하다 — **키는 컨테이너에 주입됐다**(8절의
compose 전달 줄이 동작했다). 호출도 정상적으로 나갔고, OpenAI가 키를 거부했다.

**원인 추적**

| 가설 | 검증 | 결과 |
| --- | --- | --- |
| `.env` CRLF (MobaXterm/Windows 편집 시 값 끝에 `\r`) | `tr -d '\r\n' \| xargs` 전후 길이·응답 코드 비교 | **기각** — 길이 47로 동일, 둘 다 401 |
| 값을 따옴표로 감쌈 | 같은 비교(`xargs`가 따옴표를 벗긴다) | **기각** — 길이 변화 없음 |
| 크레딧·한도 소진 | 응답 코드 | **기각** — 429가 아니라 401 |
| 앱 코드 문제 | EC2에서 앱과 무관하게 `curl https://api.openai.com/v1/models` | **기각** — 직접 호출도 401 |
| **키 값 자체가 무효** | 길이 47자 | **유력** |

**47자는 정상적인 OpenAI 키 길이가 아니다.** 구형 `sk-` 키는 51자, 프로젝트 키(`sk-proj-`)는
160자 이상이다. Day 5 기록에도 "47자"로 적혀 있었으니 **처음부터 잘린 값이 들어갔을 가능성**이 크다.
그동안 아무도 실제로 호출하지 않아 드러나지 않았을 뿐이다(Day 7의 연결성 확인은 인증 없이 던져
401을 받는 테스트라, 잘린 키도 같은 401을 낸다 — 구분이 안 됐다).

→ 조치는 **콘솔에서 재발급**. `.env` 교체 후 앱과 무관하게 `curl .../v1/models`로 200을 먼저 확인하고
`docker compose up -d --no-deps --force-recreate backend`로 backend만 재기동하면 된다(재배포 불필요).

**얻은 것**: 실패가 조용히 묻히지 않았다. 외부 API가 죽어도 200 + `degradedReason`으로 내려가도록
설계했기 때문에 시연 화면은 그대로 뜨고, 원인은 `degradedReason` 한 필드와 로그 한 줄로 좁혀졌다.

#### `check-env.sh`의 사각지대 2개

1. **CRLF를 잡지 못한다.** 인라인 주석(`VAR=value # 주석`)은 잡지만, MobaXterm으로 편집해 값 끝에
   `\r`이 붙는 경우는 통과시킨다. 이번엔 원인이 아니었지만 다음엔 원인일 수 있다.
2. **값의 형식을 보지 않는다.** "비어 있지 않은가"만 검사하므로 잘린 키는 통과한다.
   키 형식이 정해진 변수(`OPENAI_API_KEY`는 `sk-` 접두사, `DATA_*_KEY`는 Base64 32바이트)는
   최소 길이·접두사 검사를 넣을 수 있다.

### 14. 키가 GMS 중계 토큰이었다 — 13절 진단의 전제가 틀렸다

사용자 확인: OpenAI 키를 **SSAFY GMS를 통해** 쓰고 있다. 중계 토큰이다.

그러면 13절의 결론("47자이므로 잘린 키 → 재발급")은 **전제부터 틀렸다.**

- 중계 토큰이면 `api.openai.com`에 직접 던졌을 때 **401이 나오는 것이 정상**이다.
  OpenAI가 발급한 키가 아니므로 OpenAI가 모르는 값이다.
- 47자라는 비표준 길이도 이것으로 설명된다. 애초에 OpenAI 키 형식일 이유가 없다.
- 즉 13절에서 "무효한 키"의 증거로 읽은 401은 실제로는 **"엉뚱한 곳에 던졌다"**는 증거였다.

**교훈**: 외부 API 실패를 볼 때 "우리가 올바른 상대에게 말하고 있는가"를 먼저 확인해야 한다.
13절은 응답 코드만으로 원인을 좁히려다 잘못된 방향으로 네 단계를 갔다. 정작 필요한 정보는
"이 키를 누가 발급했는가"였고, 그것은 로그에도 코드에도 없었다.

#### 조치 — 중계 서버 대응은 이미 설정으로 가능하다

`base-url`·`chat-path`를 처음부터 설정으로 빼 두었다. 중계 서버 주소만 바꿔 끼우면 코드 변경이 없다.
남은 변수는 (1) 중계 서버가 요구하는 인증 헤더 형식, (2) 허용 모델명이다.

### 15. 모델 교체를 설정으로 (gpt-5 검토에 따른 선제 조치)

사용자가 `gpt-5` 사용을 검토 중이라 걸림돌 두 개를 먼저 제거했다.

| 걸림돌 | 조치 |
| --- | --- |
| 상한 필드 이름이 계열마다 다름(`max_tokens` / `max_completion_tokens`). 틀리면 400 | `app.ai.max-tokens-parameter` 신설(기본 `max_tokens`) |
| `temperature`를 받지 않는 모델이 있음 | `temperature`를 비우면 요청 본문에서 **제외**하도록 변경(기본값 주입을 없앰) |
| 추론형 모델은 `read-timeout`(PT12S)을 넘기기 쉬움 | 코드가 아니라 **판단 사항**이라 문서에만 남김 (§8-1) |

마지막 항목이 제일 위험하다. 타임아웃을 넘기면 `AI_UNAVAILABLE`로 조용히 템플릿에 내려가고
**화면은 정상으로 보인다.** "느려졌다"는 사실 자체가 드러나지 않는다.

**모델 선정 근거도 이번에 명문화했다**(그전에는 제약만 적고 근거가 없었다):
코치가 하는 일은 "이미 계산된 사실 목록 → 2문단 400자 이내 한국어"다. 추론이 필요 없는 제한된
생성 작업이고 수치·판정은 애초에 모델을 거치지 않으므로(숫자 가드) 작은 모델로 충분하다.
값이 싸고 응답이 빨라 12초 예산 안에 안정적으로 들어온다.

- 테스트 2건 추가: 계열별 본문 구성(`max_completion_tokens` + temperature 제외), 기본 구성
- 전체 1,095건 통과(기존 1,058 + 코치 37)

### 16. GMS 중계 엔드포인트 확보 — 키는 처음부터 정상이었다

`gpt-4o-mini` / `gpt-5.4-mini` A/B 호출로 세 가지가 한 번에 판명됐다.

```
EP=https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions
gpt-4o-mini   → HTTP=200  1.849s
gpt-5.4-mini  → HTTP=400  0.562s
              "Unsupported parameter: 'max_tokens' ... Use 'max_completion_tokens' instead."
```

**1) 엔드포인트**: `https://gms.ssafy.io/gmsapi/<원래 호스트>/<원래 경로>` 규칙이다.
인증 헤더는 OpenAI와 동일한 `Authorization: Bearer`가 그대로 통한다(헤더 형식 변경 불필요).
→ `application.yml`의 `base-url`을 GMS로 바꿨다. 코드 변경 없음(처음부터 설정이었다).

**2) 키는 멀쩡했다.** 13절의 "47자이므로 잘린 키 → 재발급" 결론은 틀렸다. 14절에서 전제가
틀렸음을 알았고, 여기서 확정됐다. **잘못된 주소로 던지고 있었을 뿐이다.**

**3) `gpt-5.4-mini`는 파라미터가 다르다** — 15절에서 설정으로 뺀 `max-tokens-parameter`가
정확히 이 경우다. 예측이 맞았고 대응은 설정 한 줄이다.

#### 출력 품질 판단은 보류

4o-mini 출력이 "**시스템이** 정상 작동", "관련 **데이터**가 균형"으로 나왔다. 금융 진단 결과를
시스템 모니터링 지표로 오해한 것인데, 급조한 테스트 프롬프트에 도메인 맥락이 없어서다.
실제 `CoachPromptFactory`는 "개인 재무 관리 앱 '도담'의 금융 코치"로 시작하고 `[주의]` 줄이 붙는다.
→ **모델 품질 비교는 실제 API 응답으로 해야 한다.**

긍정적인 신호 하나: 없는 숫자를 지어내지 않았다(74.3%·0건 모두 사실 목록의 값). 숫자 가드 통과.

### 17. 모델 확정 — `gpt-5.4-mini` (실측 비교)

같은 프롬프트(도메인 맥락 포함)로 GMS 중계를 통해 실측했다.

| | gpt-4o-mini | **gpt-5.4-mini** |
| --- | --- | --- |
| 응답 시간 | 1.849s | **1.715s** |
| `reasoning_tokens` | 0 | **0** |
| 도메인 해석 | ❌ "**시스템이** 정상 작동", "관련 **데이터**가 균형" | ✅ "지출과 수입 흐름을 꾸준히 확인하면" |
| 신뢰도 74.3% 표현 | 단정("신뢰성 있는 결과를 제공") | 유보("전반적인 판단은 무난하지만 일부 변동 가능성은 염두에") |
| 사실에 없는 숫자 | 없음 | 없음 |
| `temperature: 0.2` | 지원 | **지원**(별도 확인, 200) |

**더 빠른 쪽이 문장도 정확했다.** 15절에서 추론형 모델의 지연을 걱정했는데 `reasoning_tokens`가
0으로 나와 해당이 없었다. 신뢰도를 유보적으로 표현한 점은 `disclaimer`("투자·금융 자문이 아닙니다")와
같은 방향이라 코치 톤에 더 맞는다.

→ 설정 3줄 변경으로 끝났다(15절에서 설정으로 빼둔 덕분에 코드 변경 0).

```yaml
model: gpt-5.4-mini
max-tokens-parameter: max_completion_tokens
temperature: 0.2   # 유지 — 출력이 흔들릴수록 없는 숫자를 만들어 숫자 가드에 걸리는 빈도가 는다
```

**선정 근거를 이번에 표로 남겼다.** 2절에서 "제약만 적고 근거가 없다"고 지적됐던 부분이 해소됐다.
모델을 다시 바꿀 사람이 같은 실측을 반복하지 않아도 되도록 수치와 실제 출력 차이를 함께 적었다.

#### 프롬프트 손볼 거리 하나 (배포 후 실응답 보고 판단)

5.4-mini 출력 끝에 "필요하면 최근 내역을 바탕으로 더 세밀하게 점검해드릴게요"가 붙었다.
앱이 제공하지 않는 후속 행동을 약속하는 문장이다. 실제 `CoachPromptFactory`는 형식 규칙이 더
엄격하므로 안 나올 수도 있다. 배포 후 실제 응답을 보고 필요하면 시스템 프롬프트에
"대화를 이어가겠다고 제안하지 않는다"를 추가한다.
