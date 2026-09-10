# Day 7 — 외부 연동 인프라 & FE 배포 파이프라인 (P0/P1)

기획안 기준 외부 연동: OpenAI, SSAFY 교육용 금융망, FCM(Firebase), Kakao/Google 소셜로그인. FE(React Native+Expo)는 EC2와 무관하게 별도 빌드/배포 경로를 갖는다.

> **2026-09-07 재검토 반영 — 전제가 하나 바뀌었다.**
> `backend/docs/init_settings_prompt.md`에는 "ERD에 OAuth 사용자 식별 모델과 FCM Device Token 모델이 없다"는 미결 사항이 적혀 있었으나, **갱신된 ERD에는 이미 들어와 있다.**
> - `user_oauth_account` — `provider`, `provider_user_id_hash`, `provider_email_encrypted`
> - `user_device` — `fcm_token_encrypted`, `fcm_token_hash`, `platform`, `enabled`
> - `user.role` — `USER` / `ADMIN` CHECK 제약 (관리자 API 권한 모델도 해소됨)
>
> 즉 **소셜로그인과 FCM은 "할지 말지 미정"이 아니라 진행 전제로 확정**됐고, 두 값 모두 암호화 저장이므로 **Day 5의 데이터 암호화 키가 선행**되어야 한다.
> 또 FE 일정상 **Day 4에 "로그인 / 금융데이터 연동"** 이 시작되므로, OAuth Redirect URI와 CORS는 이 Day까지 미루지 말고 **가능한 앞당겨 확정**할 것.

## 체크리스트

### 1. SSAFY 교육용 금융망 연동 준비
- [x] 인증 방식 확인 — `application.yml`에 `app.ssafy-finance.api-key`(=`SSAFY_FINANCE_API_KEY`)로 정의됨, `.env` 설정 완료
- [x] **아웃바운드 연결성 확인 (2026-09-10)** — Day 1부터 미확인이던 항목
      EC2 호스트·backend 컨테이너 양쪽에서 `https://finopenapi.ssafy.io` **HTTP 200**
      → 네트워크 차단 없음. IP 화이트리스트 등록 없이 호출 가능
      (인증 단계에서 IP를 보는 경우에 대비해 공인 IP `43.203.198.18`을 문서에 기록)
- [ ] 백엔드가 이 API를 호출할 때 타임아웃/재시도 정책을 어디서 관리할지(백엔드 코드 vs 인프라) 협의 — 인프라는 아웃바운드 네트워크만 보장

### 2. OpenAI 연동
- [x] API Key `.env` 설정 완료, backend 컨테이너 → `api.openai.com` 연결 확인(401 = 네트워크 정상)
- [ ] 사용량 한도·결제 담당자 확인 (**사용자 액션**)
- [ ] 비용 폭주 방지를 위해 사용량 알림(있다면 OpenAI 대시보드 알림) 설정 안내

### 3. FCM (Firebase)
- [x] 키 보관 구조 준비 — `infra/secrets/` 생성, `.gitignore`로 차단(검증 완료), 발급 절차 문서화
- [x] compose에 마운트 설정 준비 (**주석 상태**) — 키 파일 없이 bind mount를 걸면
      Docker가 그 경로에 디렉터리를 만들어버려 나중에 꼬이므로, 키 수령 후 활성화
- [ ] **Firebase 프로젝트 생성 + Android 앱 등록** (**사용자 액션**)
      패키지명 `com.ssafy.d105.dodam` (FE 확정)
- [ ] 서비스 계정 키(JSON) 발급 → EC2 `infra/secrets/`로 전달 → 인프라가 마운트 활성화
- [ ] FE(Expo)에서 FCM 토큰 등록 흐름은 FE팀 담당, 인프라는 백엔드 쪽 서버 키 보관만 책임

### 4. 소셜 로그인 (Kakao/Google)
- [x] 등록할 정확한 값 확정 — [[docs/external-integrations.md]]에 정리
      Redirect URI: **`dodam://oauth/kakao`** (커스텀 스킴, 웹 방식 아님)
      Android 패키지명: `com.ssafy.d105.dodam`
- [ ] Kakao Developers 등록 + REST API 키/Secret 발급 (**사용자 액션**)
- [ ] Google Cloud Console OAuth 클라이언트 생성 (**사용자 액션**)
      Android 유형은 Client Secret이 없으므로, 백엔드가 secret을 요구하면 웹 클라이언트도 함께 생성 필요

### 5. FE 빌드/배포 파이프라인
현재 FE 구성 실측: **Expo SDK 57 / React Native 0.86 / expo-router**.

> **2026-09-07 확정**: FE팀이 **웹 없이 네이티브 앱(APK)만** 만들기로 결정.
> 함의:
> - **CORS 불필요** — 브라우저가 아니므로 적용되지 않음 (환경변수 자체는 백엔드 기동을 위해 남겨둘 것)
> - **nginx가 FE를 서빙할 일 없음** — `web.output: static` 기반 정적 배포 계획 폐기, `/` 라우팅은 상태 페이지로 확정
> - **HTTPS가 선택이 아니라 필수** — Android는 targetSdk 28+부터 평문 HTTP를 기본 차단한다. Day 3에서 붙인 Let's Encrypt 인증서는 공개 신뢰 체인이라 앱에서 추가 설정 없이 동작한다 (자체서명이었다면 앱에 인증서를 심어야 했음)

- [ ] **APK 빌드 방식 확정** (**사용자 액션**: FE팀 협의)
  - EAS Build(클라우드, Expo 계정 필요) vs 로컬 빌드(`expo run:android` / `eas build --local`)
  - 인프라 관점에서는 EC2와 무관하므로 개입할 것이 없다. 다만 **APK 배포처**(팀 공유 드라이브 등)는 정해둘 것
- [ ] FE가 백엔드 API를 호출할 BaseURL을 환경변수로 관리하는 방식 합의 — **`https://j15D105.p.ssafy.io/api`**
- [ ] **소셜 로그인 Redirect URI는 웹 방식이 아님** — 네이티브 앱은 커스텀 스킴(`frontend://`, `app.json`의 `scheme`) 또는 App Links를 쓴다. Kakao/Google 콘솔에 등록할 값이 웹과 다르므로 FE팀과 형식을 맞출 것
- [ ] FCM 관련: `package.json`에 `expo-device`는 있으나 **`expo-notifications`가 아직 없음** → FE팀이 토큰 발급 흐름을 붙일 때 추가 필요함을 공유
- [ ] OTA 업데이트(Expo Updates) 사용 여부는 시간 되면 검토, 이번 범위에서는 필수 아님(P2)

## 산출물
- 외부 연동 시크릿/등록 상태 체크리스트 완료 (또는 담당자에게 위임된 액션 목록)
- FE가 백엔드에 붙을 수 있는 BaseURL 확정
