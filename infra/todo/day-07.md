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
- [ ] API 문서(`docs/API명세서/`) 기준으로 필요한 인증 방식(고정 API Key, userKey 등) 재확인
- [ ] 아웃바운드 IP 화이트리스트가 필요한지 확인 (🔲 확인 필요, **사용자 액션**: 필요하면 EC2 고정 IP를 금융망 포털에 등록)
- [ ] 백엔드가 이 API를 호출할 때 타임아웃/재시도 정책을 어디서 관리할지(백엔드 코드 vs 인프라) 협의 — 인프라는 아웃바운드 네트워크만 보장

### 2. OpenAI 연동
- [ ] API Key는 Day 4에서 정리한 시크릿 관리 체계로 등록 (조직 계정 여부, 사용량 한도 확인 — **사용자 액션**: 결제 담당자 지정)
- [ ] 비용 폭주 방지를 위해 사용량 알림(있다면 OpenAI 대시보드 알림) 설정 안내

### 3. FCM (Firebase)
- [ ] Firebase 프로젝트 생성 여부 확인 (**사용자 액션**: 없으면 생성)
- [ ] 서비스 계정 키(JSON) 발급 → 백엔드 컨테이너에 볼륨 마운트로 전달 (`.env`가 아닌 파일 마운트, Git에는 절대 커밋 금지 — `.gitignore` 재확인)
- [ ] FE(Expo)에서 FCM 토큰 등록 흐름은 FE팀 담당, 인프라는 백엔드 쪽 서버 키 보관만 책임

### 4. 소셜 로그인 (Kakao/Google)
- [ ] Kakao Developers / Google Cloud Console에 EC2 도메인(또는 IP) 기준 Redirect URI 등록 (**사용자 액션**: 각 콘솔 접근 권한 있는 팀원이 진행)
- [ ] 도메인이 확정되지 않았다면 임시로 IP 기반 URI 등록 후, 도메인 확보 시 갱신 필요함을 팀에 공지

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
