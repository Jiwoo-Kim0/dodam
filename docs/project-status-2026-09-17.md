# 프로젝트 현황 — 2026-09-17 (D13, 1차 QA일)

FE·BE·Infra의 현재 상태와 영역별 남은 작업을 담당과 함께 정리한 문서다. 스프린트 중간 공유와 스크럼 참고용이며, 명세서가 아니라 **작성 시점의 코드·문서 기준**이다.

- 스프린트: `sprint_2026-09-14 ~ 2026-09-20`
- 기준 커밋: `develop` @ `185247e` (`origin/develop`과 동일, 로컬 미푸시 0건)
- 근거 자료: [fe-be-api-integration-status.md](fe-be-api-integration-status.md), [backend/docs/remaining-work.md](../backend/docs/remaining-work.md), [infra/README.md](../infra/README.md), 로컬 Jira 내보내기(`SPRINT/`, 저장소 미포함)
- 확인 방법: BE는 컨트롤러 매핑과 `remaining-work.md` 체크박스, FE는 `frontend/src/api`·`frontend/src/app` 실제 코드, 관할은 커밋 이력 + 역할 분담

> 담당 표기 기준: BE-A 구동재 / BE-B 정현진 / BE-C 김지우 / FE 정서영·이진웅 / Infra 김지우.
> FE 신규 화면처럼 배정 기록이 없는 항목은 `미배정`으로 두었다. 틀리면 그 줄만 고쳐 쓴다.

---

## 0. 오늘은 전원 1차 QA일이다

| 이슈 | 담당 | 범위 |
| --- | --- | --- |
| 115 | 구동재 | 인증·금융데이터·목표·알림 1차 QA |
| **124** | **정현진** | **진단·추천·시뮬레이션 1차 QA** — 진단 근거, 추천 상태, 시뮬레이션 전 유형, 복합 시나리오, 전후 비교, 목표 영향, 경계값 |
| 135 | 김지우 | AI Coach·개선현황·리포트 + 통합 환경 QA, **팀 QA 결과 취합** |
| 147 | 정서영 | 가계부·자산·목표·발견 UI QA |
| 157 | 이진웅 | 인증·홈·AI Coach·리포트·알림 UI QA |

- 선행: 134(김지우, 9/16) QA 버전 배포·시연 기준 데이터·접속 방법 공유. **QA 시작 전에 이것부터 확인해야 한다.**
- 완료 기준(공통): 시나리오별 통과·실패·진행 불가와 재현 절차·기대/실제 결과·심각도·환경 기록.
- 135에 명시된 대로 **QA 완료를 결함 수정 완료로 간주하지 않는다.**

---

## 1. 전체 현황 한눈에

| 영역 | 담당 | 상태 | 요약 |
| --- | --- | --- | --- |
| BE | 구동재(A)·정현진(B)·김지우(C) | 구현 완료, 검증 단계 | 24개 도메인, 컨트롤러 38개, 엔드포인트 235개 |
| FE | 정서영·이진웅 | 연동 진행 중 | 235개 중 84개(36%) 연동, **불일치 0건**, 업무 데이터 mock 0건 |
| Infra | 김지우 | 운영 중 | EC2 + Docker Compose, `develop` 머지 시 자동 배포 |

---

## 2. BE

### 2-1. 도메인 관할

| 담당 | 도메인 |
| --- | --- |
| 구동재 (BE-A) | user, auth, finance, account, transaction, spending, goal, subscription, pattern, notification |
| 정현진 (BE-B) | card, loan, deposit, savings, depositsaving, installment, minusaccount, cashflow, product, diagnosis, recommendation, simulation |
| 김지우 (BE-C) | coach, improvement, report + QA·배포 |

### 2-2. 2026-09-16 병합 (MR 8건 + 590)

| MR | 내용 | 담당 |
| --- | --- | --- |
| !150 | `GET /cards` 목록·상세에 `cardId` | 정현진 |
| !151 | 예·적금 이자소득세·중도해지이율 정책 통합 | 정현진 |
| !152 | `GET /installments/burden` 신규 | 정현진 |
| !153·!154 | 루트 명세 반영, 545 군집 제외·532 보류 결정 기록 | 정현진 |
| !155 | 대환·추가상환 본문 `loanId?`, `GET /loans` `loanId`, 프리셋에 `loanId` | 정현진 |
| !156 | `GET /recommendations/summary`에 `discovery{…}` | 정현진 |
| !157 | 마이너스통장 상환·이자납부 `Idempotency-Key` | 정현진 |
| (별도) | 개선·리포트 API 19종(590) | 김지우 |

627(발견 화면 진단 규칙 확장)·628(`sourceIssueId`) 워크트리도 `origin/develop`에 병합 확인 완료. **미커밋·미푸시 작업 없음.**

### 2-3. 정현진(BE-B) 잔여 — 기능 미구현 3건, 나머지는 검증·정책·문서

내 도메인의 **엔드포인트 중 미구현은 없다.** 아래는 `remaining-work.md`의 열린 항목을 성격별로 묶은 것이다.

#### (1) 코드가 실제로 남은 것

| 이슈 | 항목 | 현재 상태 | 판단 |
| --- | --- | --- | --- |
| 532 | 현재 월 **전체** 상환액·부담률 | 월별 일정·납부내역의 회차 매칭 계약 미확보 | **보류 결정**(`0d17e1e`에 기록). 다시 하려면 계약부터 |
| 533 | 목표 기반 상환 우선순위(`goalId`) | 미지원 | **범위 제외 결정**. 후속 이슈로 분리할지만 미정 |
| 540 | 급여 등 **예정 입금이 예측에 안 잡힘** | `AccountScheduledEventSource` 인터페이스만 있고 구현체 0개. `CashflowEventCollector`가 빈 리스트를 주입받음 | 예측 정확도에 실제 영향. 시연에서 잔액 예측이 실제보다 비관적으로 나온다 |

부수 항목: 537 `remainingLoanRepaymentAmount`는 저장된 `SCHEDULED` 회차 합계로 채우고 있어 seed에 예정 회차가 없으면 0이 된다(계산된 상환 일정으로 대체하는 항목이 열려 있음).

#### (2) MySQL·시연 seed 실측 미검증 (H2 통합 테스트만 통과) ← 오늘 QA의 핵심

| 이슈 | 미검증 범위 |
| --- | --- |
| 530 | 할부 조회 — 소유권 격리·정상/빈 결과·경계값이 검증 데이터 없이 H2 범위에 머묾 |
| 534 | 마이너스통장 — 상환·이자납부 실데이터 동작 |
| 536 | 예금 조회 4개 경로 + `ddl-auto: validate` |
| 537 | 적금 조회 9개 경로 + `ddl-auto: validate` |
| 538·540·541 | 현금흐름 저장·일별 잔액·실행/조회 — 시연 seed 적재 상태 미검증 |
| 627 | 진단 MySQL JSON 컬럼(`current_value`의 BigDecimal·LocalDate 직렬화) |

- 공통 선행 조건: **시연 seed 적재 + 암호화 픽스처 임시 테이블 3개**. 한 번 만들어 두면 위 6건을 한꺼번에 돈다.
- 587 후속(158) "시연 seed MySQL에서 시뮬레이션 10종 실행 확인"도 오늘 일정이라 **124 QA에 묶어서 수행**한다.
- 587 후속 4건 중 159·160·161은 616·617·618·619로 이미 닫혔다.

#### (3) 임의값으로 두고 팀 확정을 기다리는 정책 — 스크럼 안건

| 이슈 | 항목 | 현재 값 |
| --- | --- | --- |
| 543·538 | `rule_version` / `calculation_version` 표기 규칙 | 둘 다 `"v1"` 상수 |
| 627 | 소비 증가·고금리 심각도 경계 | 증가율 50% → HIGH, 편차 2.0%p 또는 연체 → HIGH, 최근/이전 3개월 고정, 미분류 제외, 대환 후보는 같은 업권 한정 |
| 540 | 예측 파라미터 | `horizonDays` 기본 90·상한 365, 완료 캐시 당일, 진행 중 인정 10분(임의값) |
| 541 | 완료된 예측이 없을 때 | 404 `RESOURCE_NOT_FOUND` — 빈 목록으로 바꿀지 |
| 536·537 | 중도해지이율 | 상품별 공시값 미확보, 구간표로 대체 중 |
| 536 | 과세 구분 | 비과세·세금우대·분리과세 미반영, 일반과세 15.4% 단일 |
| 627 | 대환 절감액 대상 | 잔여 회차와 **정확히 같은** `term` 옵션이 있는 상품만. seed 옵션이 12·24·36·60개월 위주라 대부분 `NO_ELIGIBLE_PRODUCT` |

이 값들이 바뀌면 `DiagnosisRuleVersion` / `CashflowCalculationVersion`을 올려야 한다.

#### (4) 다른 담당과 합의해야 닫히는 것 (혼자 못 끝냄)

| 상대 | 항목 |
| --- | --- |
| BE-A 구동재 | 534 — `source_type='SEED'` 계좌를 `POST /finance-data/sync`(`ALL`/`ACCOUNT`) 대상에서 제외. **지금은 sync가 로컬 상환분을 덮어쓴다** |
| BE-A 구동재 | 534 — 상환·이자납부를 `transaction`에 기록할지 |
| BE-A 501 | 534 — `JdbcAccountBalanceWriter` 존재 인계, 명세 5 `GET /accounts/{accountNo}/transactions`와 응답 계약 대조 |
| 458 | 534 — `source_type='MINUS_ACCOUNT'`, `source_id=account_id` 알림 규약 |
| 팀/ERD | 538 — `cashflow_event.type`·`cashflow_risk.type` 값 집합을 ERD 코드값 표에 반영(코드에서는 확정) |
| 팀/ERD | 535 — 루트 ERD SQL `chk_deposit_saving_withdrawal_account`에 `withdrawal_account_type IS NOT NULL` 반영 |

#### (5) 문서만 남은 것 (루트 `docs/`라 요청 시 반영)

- 명세 10: `GET /loans`에 `loanId`
- 명세 16: 추천 요약 `discovery{diagnosisId, issueCount, openRecommendationCount, expectedAnnualCostSaving, expectedGoalAdvanceDays, notes[]}`, 추천 응답 `sourceIssueId`
- 명세 9: `GET /installments/burden` 응답(현재 파라미터만 정의)
- 명세 17: `POST /simulations/saving` 본문 `goalId?`와 "accountNo와 동시 지정 불가"

추가로 `remaining-work.md` 543 절 체크박스 9개는 대부분 "544·545에서 정한다"인데 **544·545가 이미 끝나 사실상 해소**됐다. 문서 정리가 필요하다.

### 2-4. 내 관할이 아닌데 BE에 남아 있는 것 (발견·기록만 함)

| 항목 | 담당 | 내용 |
| --- | --- | --- |
| 589 Redis 연결 확인 | 김지우 | 프로젝트 첫 Redis 사용. 실패해도 예외를 삼켜 **겉으로 티가 안 난다** — 배포 후 로그에서 `Coach cache lookup failed` 반복 여부 확인 필요 |
| 589 코치 레이트 리밋 | 김지우 | 외부 과금이 걸린 유일한 엔드포인트인데 사용자별 호출 제한이 없다(nginx 제한은 로그인 경로만) |
| 공유 H2 컨텍스트 캐시 | 공용 설정 / BE-A | `jdbc:h2:mem:dodam` + `create-drop` 통합 테스트가 컨텍스트 축출에 취약. 587에서 새 컨텍스트 2개를 더한 첫 전체 빌드에서 96건이 `Table "auth_session" not found`로 실패했다. 다음에 `@SpringBootTest`를 추가하는 사람에게서 재발한다 |
| 545 군집 신호 미연결 | BE-A | `diagnosis_issue.cluster_context_json`이 항상 null(`CLUSTER_NOT_CONNECTED`). 붙으면 코치 "군집 근거" 구간이 코드 변경 없이 채워진다 |
| seed 날짜 고정 | 데이터 담당 | 523 seed의 `next_repayment_date`가 2026-09 고정 → **09-21부터 시연 계정에 추가상환·대환 가능한 대출이 없다.** `next_interest_date`도 1,000행 모두 `2026-09-25`. U0001 목표는 이미 달성 상태 |

---

## 3. FE

### 3-1. 현황

- API 모듈 13개, 호출 84건. 경로·메서드·파라미터·응답 필드·enum까지 **전부 일치(불일치 0건)**.
- 업무 데이터 mock **0건**. 남은 mock import는 은행 로고(`FINANCIAL_INSTITUTIONS`)·가계부 입력 초기값 2건뿐.
- 화면: 로그인·약관·본인확인 / 온보딩(인증서·금융망 연결·동기화·알림권한) / 자산·가계부·발견(+인사이트 상세)·홈(+데일리브리핑·목표상세·알림)·리포트 탭.

### 3-2. BE는 준비됐는데 FE 화면이 아예 없는 도메인

| 도메인 | 미연동 API | FE 담당 | BE 담당 |
| --- | ---: | --- | --- |
| Subscription (구독 관리) | 10 | 미배정 | 구동재 |
| Product (예적금·대출·카드 비교/추천) | 15 | 미배정 | 정현진 |
| Improvement (개선 리포트, 590) | 11 | 미배정 (리포트 계열 → 이진웅) | 김지우 |
| Report (일간·월간 리포트, 590) | 9 | 이진웅 | 김지우 |
| Pattern (소비·현금흐름 패턴, 우선순위 프로파일) | 5 | 미배정 | 구동재 |

### 3-3. 화면은 있으나 데이터 연결이 남은 곳

| 항목 | 내용 | FE | BE |
| --- | --- | --- | --- |
| 리포트 탭 | `useReportData`가 자산·현금흐름·가계부 API를 조합해 **FE에서 효율점수를 직접 계산** 중. `GET /reports/monthly` 계열로 교체 필요 | 이진웅 | 김지우 |
| 발견 화면 | 추천 요약 `discovery` 통합 수치(문제 수·연 절감액·목표 단축일) 미사용 | 정서영 | 정현진 |
| 추천 액션 | 수락·거절·완료·보류(`POST /recommendations/{id}/*`) 미연동 | 정서영 | 정현진 |
| 자산 상세 | 계좌·카드·대출·예적금·할부·마이너스통장의 상세/거래내역/상환일정/만기예측 약 60건. **상세 화면 계층 자체가 없다** | 정서영 | 정현진 (계좌·거래는 구동재) |
| 시뮬레이션 | 소비·저축 2종만 연동. 대출 신규·대환·중도상환·리볼빙·현금서비스·복합 미구현 | 정서영 | 정현진 |

### 3-4. 작은 후속 작업

| 작업 | FE | BE |
| --- | --- | --- |
| `CardApiResponse.cardId`·`LoanApiResponse.loanId`·`RecommendationSimulationRequestApiResponse.loanId` 타입 추가 → **리볼빙·대환 프리셋을 그대로 POST 가능** | 정서영 | 정현진 (완료) |
| 푸시 토큰 등록 `POST /notifications/devices` 호출(현재 OS 권한만 요청) | 이진웅 | 구동재 (완료) |
| 회원가입 `POST /auth/signup`, 탈퇴 `DELETE /users/me` | 정서영 | 구동재 (완료) |
| 목표 연결 계좌 배분 필드(`allocatedToThisGoal`, `availableToAllocate`, `allocationStatus` 등) 타입 확장 | 정서영 | 정현진 (완료) |
| `SavingSimulationRequestSnapshot.goalId` 추가 | 정서영 | 정현진 (완료) |
| `unavailableReason`을 문자열로 넓히기(FE 4개 열거 / BE 상수 7개) | 정서영 | 정현진 |

### 3-5. 알아 둘 차이 (동작은 됨)

- 마이너스통장 상환·이자납부를 연동할 때 **`Idempotency-Key` 헤더를 항상 보내야 한다**(!157).
- `goalImpacts[].status/reasons`는 `@JsonInclude(NON_NULL)`이라 spending/saving 결과에는 키 자체가 없다. 585 계열 유형을 붙일 때 `before/after/cashflow`의 확장 필드(`details`, `currentMonth`, `truncated`)를 추가로 다뤄야 한다.
- 628 이전에 생성된 추천은 `sourceIssueId`가 null이다.
- FE가 안 보내는 선택 파라미터(계좌·카드·대출 필터, 알림 기간, 진단 `goalId` 등)는 모두 BE 기본값으로 동작한다.

---

## 4. Infra — 담당 김지우

- EC2 1대 + Docker Compose 단일 스택: nginx, backend, mysql, redis, jenkins, certbot, prometheus, grafana, loki, promtail, node-exporter.
- `develop` 머지 → GitLab 웹훅 → Jenkins 빌드·테스트·배포·헬스체크 자동화. 배포 대상에서 jenkins는 제외한다(자기 빌드를 죽인다).
- HTTPS(Let's Encrypt 자동 갱신), Jenkins UI는 루프백 바인딩(SSH 터널 경유만), 백업·복구 스크립트 보유.
- ⚠️ `docker compose down -v` 금지 — `mysql_data`·`jenkins_home`이 삭제된다.
- 현재 FE 연동 작업에 **추가 인프라 변경은 없다.** FE 앱 배포(Expo 빌드·배포) 방식은 별도 논의가 필요하다.
- 미결 1건: 시연 서버 `infra/.env`의 `SSAFY_FINANCE_API_KEY`를 새 키로 교체하고 backend 재기동(500 절).

---

## 5. 오늘 결정하거나 확인하고 갈 것

1. **QA 버전·시연 데이터가 실제로 올라가 있는가**(134). 안 올라갔으면 오늘 QA 5건이 전부 막힌다.
2. **시연 seed 대출 날짜**(`next_repayment_date` 2026-09 고정) — 09-21부터 대환·추가상환 시연이 불가능하다. 잔여 회차가 옵션 term(6/12/24/36)과 같은 대출을 시연 계정에 넣을지 결정.
3. **`rule_version`·`calculation_version` 표기 규칙** — 지금 둘 다 `"v1"`.
4. **627 진단 심각도 경계값**을 팀 값으로 확정할지, 현재 초기값으로 시연할지.
5. **541 빈 예측 응답** 404 vs 빈 목록.
6. **534 sync가 로컬 상환분을 덮어쓰는 문제** — BE-A와 `SEED` 계좌 제외 합의.
7. **FE 미배정 화면 5개 도메인**(구독·상품비교·개선·패턴 + 리포트 API 교체)을 이번 스프린트에 넣을지 시연 범위에서 뺄지.
8. **루트 API 명세 4건 반영**(명세 9·10·16·17) 요청 여부.

---

## 6. 리스크

| 리스크 | 영향 | 완화 |
| --- | --- | --- |
| BE-B 도메인 6건이 MySQL 실측 미검증 | 시연 중 조회 실패·`ddl-auto: validate` 실패 가능 | 오늘 124 QA에서 seed + 암호화 픽스처로 한 번에 확인 |
| 예정 입금 미반영(540) | 현금흐름 예측이 실제보다 비관적 | 시연 시나리오에서 예측 수치 사용을 제한하거나 구현체 추가 |
| 대환이 seed에서 `NO_ELIGIBLE_PRODUCT`/400 | 발견 → 추천 → 시뮬레이션 대표 흐름이 끊긴다 | seed 대출 잔여 회차를 옵션 term에 맞춰 조정 |
| AI Coach가 Redis·AI 실패를 조용히 삼킴 | 문제가 있어도 QA에서 드러나지 않는다 | 배포 후 로그 확인(김지우), `source`가 `TEMPLATE` 고정인지 확인 |
| FE 미연동 151건 중 상세 화면 계층 부재 | 자산 탭이 목록까지만 시연 가능 | 시연 범위를 목록 + 발견 흐름으로 고정할지 오늘 결정 |
