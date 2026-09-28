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
- [x] **Firebase 프로젝트 생성 + Android 앱 등록 (2026-09-10)**
      패키지명 `com.ssafy.d105.dodam`. FE가 `google-services.json`을 받아
      `app.json`에 연결하고 `.gitignore`로 차단했다 (MR !61)
- [x] **서비스 계정 키(JSON) 발급 → EC2 `infra/secrets/` 전달 → 마운트 활성화 (2026-09-10)**
      컨테이너 경로 `/run/secrets/fcm-service-account.json`.
      키 없이 마운트를 걸면 Docker가 디렉터리를 만들어버리므로 수령 후에 활성화했다.
      ⚠️ `scp`한 파일은 `ubuntu`(uid 1000) 소유라 비루트(uid 10001) 컨테이너가 **읽지 못한다.**
      마운트는 성공하고 컨테이너도 healthy로 떠서 겉으로는 정상으로 보이는 유형의 결함 —
      `sudo chown 10001:10001` 후 컨테이너 내부에서 실제 읽힘까지 확인했다
- [ ] FE(Expo)에서 FCM 토큰 등록 흐름은 FE팀 담당, 인프라는 백엔드 쪽 서버 키 보관만 책임
      (`expo-notifications` 설치 완료 ✅ / 토큰 발급·등록 구현은 대기)

### 4. 소셜 로그인 (Kakao/Google)
- [x] **인증 흐름 확정 (2026-09-11, 백엔드)** — 앱이 네이티브 SDK로 로그인하고
      서버는 `POST /api/v1/auth/oauth/{provider}`로 받은 토큰만 provider에 검증한 뒤
      `user_oauth_account`로 매핑해 기존 JWT를 발급한다.
      **서버 Redirect(Authorization Code Flow) 방식은 쓰지 않는다** — RN에서 웹뷰·딥링크
      왕복이 필요해 UX가 나쁘고, ERD도 provider 토큰을 저장하지 않는 구조다.
      `build.gradle`의 `spring-boot-starter-oauth2-client`는 향후 확장용으로 남긴 것이며
      이 흐름에서는 쓰이지 않는다
- [x] ~~Redirect URI `dodam://oauth/kakao` 확정~~ → **정정**: 이 흐름에서는 Redirect URI를
      **등록하지 않는다.** 네이티브 SDK는 콘솔의 Redirect URI 항목을 쓰지 않고
      고정 스킴 `kakao{네이티브앱키}://oauth`로 돌아온다 (`app.json`의 `scheme`과 무관).
      → **FE 작업 필요**: AndroidManifest에 해당 intent-filter 추가.
      빠지면 로그인 후 앱으로 복귀하지 못한다 (09-11 현재 `app.json`에 없음)
- [x] **Client Secret은 Kakao/Google 양쪽 모두 불필요** — 서버가 authorization code를
      교환하지 않으므로 쓸 자리가 없다. `.env`에서 두 변수를 제거했다
- [ ] Kakao Developers 등록 (**사용자 액션**)
      **플랫폼 → Android**(패키지명 + 키 해시)가 콘솔에 필요한 핵심.
      카카오 로그인 활성화 ON, OIDC 쓸 경우 **OpenID Connect 활성화 ON**(기본 OFF).
      서버에 필요한 값: `GET /v2/user/me` 방식이면 **없음**, OIDC 방식이면 aud 비교용 앱 키 1개
      (앱 키가 4종이므로 실제 `id_token`을 디코드해 확인 후 넣을 것)
- [ ] Google Cloud Console (**사용자 액션**)
      **웹 애플리케이션 유형 클라이언트의 ID**가 필요하다 — Credential Manager가 `id_token`을
      받을 때 `serverClientId`로 쓰고, 서버는 `aud` 비교에만 쓴다. **Secret은 배포하지 않는다.**
      Android 유형 클라이언트(패키지명 + SHA-1)와 OAuth 동의 화면(+ 테스트 사용자 전원)도 필요.
      ⚠️ Firebase에 SHA-1을 추가하면 Android·웹 클라이언트가 **자동 생성**되므로
      수동 생성 전에 GCP 사용자 인증 정보 목록을 먼저 확인할 것

### 5. FE 빌드/배포 파이프라인
현재 FE 구성 실측: **Expo SDK 57 / React Native 0.86 / expo-router**.

> **2026-09-07 확정**: FE팀이 **웹 없이 네이티브 앱(APK)만** 만들기로 결정.
> 함의:
> - **CORS 불필요** — 브라우저가 아니므로 적용되지 않음 (환경변수 자체는 백엔드 기동을 위해 남겨둘 것)
> - **nginx가 FE를 서빙할 일 없음** — `web.output: static` 기반 정적 배포 계획 폐기, `/` 라우팅은 상태 페이지로 확정
> - **HTTPS가 선택이 아니라 필수** — Android는 targetSdk 28+부터 평문 HTTP를 기본 차단한다. Day 3에서 붙인 Let's Encrypt 인증서는 공개 신뢰 체인이라 앱에서 추가 설정 없이 동작한다 (자체서명이었다면 앱에 인증서를 심어야 했음)

- [x] **APK 빌드 방식 확정 — EAS Build (2026-09-10)**
      keystore를 Expo가 클라우드에서 관리하므로 **분실 위험이 없고**(로컬 빌드였다면 릴리즈
      keystore를 잃는 순간 앱 업데이트가 불가능해진다) 팀원 누구나 같은 키로 빌드한다.
      인프라가 보관할 서명 키는 없고, 대신 **Expo 공용 계정**이 그 자리를 대신한다.
      소셜 로그인에 필요한 **SHA-1 조회 절차**까지 문서화했다 ([[docs/external-integrations.md]] §5)
      - 남은 것: `eas.json` 작성·`eas init` (FE팀), **APK 배포처** 결정
- [x] FE가 백엔드 API를 호출할 BaseURL 확정 — `EXPO_PUBLIC_API_BASE_URL=https://j15D105.p.ssafy.io/api`
      (`EXPO_PUBLIC_` 변수는 APK에 그대로 박히므로 공개돼도 되는 값만 넣을 것)
- [x] **소셜 로그인 Redirect URI 형식 확정** — 커스텀 스킴 `dodam://oauth/kakao`
      (`app.json`의 `scheme: "dodam"`과 일치 확인). 웹 방식(`https://...`)이 아니다
- [x] FCM 관련: `expo-notifications` **설치 완료** (`~57.0.17`) — 토큰 발급 흐름 구현은 FE팀 대기
- [ ] OTA 업데이트(Expo Updates) 사용 여부는 시간 되면 검토, 이번 범위에서는 필수 아님(P2)

## 산출물
- 외부 연동 시크릿/등록 상태 체크리스트 완료 (또는 담당자에게 위임된 액션 목록)
- FE가 백엔드에 붙을 수 있는 BaseURL 확정

## 진행 상태 (2026-09-11 기준)

| 연동 | 상태 | 남은 것 |
| --- | --- | --- |
| SSAFY 금융망 | ✅ 키 설정 + 아웃바운드 200 확인 | 타임아웃/재시도 정책 담당 협의 |
| OpenAI | ✅ 키 설정 + 연결 확인(401) | 사용량 한도·결제 담당자, 사용량 알림 (**사용자 액션**) |
| FCM | ✅ 프로젝트·앱 등록 + 서버 키 마운트 | FE 토큰 발급 흐름, 백엔드 `user_device` 저장 API |
| Kakao OAuth | ❌ 미발급 | Android 플랫폼 등록 + 네이티브 앱 키 (**사용자 액션**) |
| Google OAuth | ❌ 미발급 | 웹 클라이언트 ID + Android 클라이언트 + 동의화면 (**사용자 액션**) |
| FE 빌드 | ✅ EAS Build 확정 + 절차 문서화 | `eas init`/`eas.json`, APK 배포처 (FE팀) |

**인프라가 단독으로 끝낼 수 있는 일은 남아 있지 않다.** Kakao/Google은 콘솔 등록이 선행이고,
등록 후 인프라 작업은 `.env`에 값을 넣고 backend를 재기동하는 것뿐이다.
→ 다음은 [[day-08.md]] (통합 리허설·장애 대응·문서화).
