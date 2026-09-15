# 소셜 로그인 (카카오·구글)

API 명세 1 Auth에는 없는 확장이다. 기획안·아키텍처(소셜 로그인 Kakao/Google)와 ERD `user_oauth_account`를 근거로 백엔드 범위만 먼저 구현했다(2026-09-15).
Jira 키는 아직 없다. FE(로그인 화면의 카카오·구글 버튼)와 인프라(개발자 콘솔 앱 등록, `.env`) 작업은 아래 "FE·인프라 인계"에 있다.

## 흐름

네이티브 앱이라 서버 리다이렉트 방식(Authorization Code + Redirect URI)을 쓰지 않는다. **앱이 공급자 SDK로 직접 로그인해 받은 토큰을 서버에 보내고, 서버는 그 토큰을 공급자에 검증만 한다.**
그래서 서버에는 client secret이 필요 없다.

```
앱 ──카카오/구글 SDK 로그인──▶ 공급자        (토큰 발급: 카카오 access token / 구글 ID token)
앱 ──POST /api/v1/auth/social/{provider} {token}──▶ 서버
서버 ──토큰 검증──▶ 공급자 (카카오 v2/user/me, 구글 JWK 서명 검증)
서버: user_oauth_account 조회 → 있으면 그 회원 / 없으면 이메일 일치 회원에 연결 또는 신규 회원 생성
서버 ──200|201 + 로그인 응답(accessToken·refreshToken·user)──▶ 앱
```

회원 판별 순서(`SocialAuthService`):

1. `(provider, provider_user_id_hash)`가 있으면 그 회원으로 로그인한다. 탈퇴한 회원이면 403 `USER_WITHDRAWN`.
2. 없고 공급자가 **소유를 확인한 이메일**(구글 `email_verified`, 카카오 `is_email_valid && is_email_verified`)이 기존 회원 이메일과 같으면 그 회원에 연결하고 로그인한다.
   그 회원에 같은 공급자의 다른 소셜 계정이 이미 연결돼 있으면 409 `SOCIAL_ACCOUNT_ALREADY_LINKED`.
3. 아니면 새 회원을 만든다(201). 이메일 회원가입과 같은 구성(`user`, `user_setting`, `financial_profile`, `finance_connection`)이며 `password_hash`는 NULL이다.
   - 이메일: 소유 확인된 공급자 이메일. 없으면 대체 이메일 `{provider}.{해시 앞 20자}@social.dodam.invalid`(수신 불가한 값, `.invalid` TLD).
   - 이름: 공급자 표시 이름(카카오 닉네임·구글 name). 없으면 `도담 회원`.
   - 금융망 회원 등록은 실제 이메일일 때만 시도한다. 대체 이메일이거나 금융망이 거절하면 연결 상태 FAILED로 두고 Finance Data API(498)로 나중에 연결한다.

같은 회원에 카카오·구글을 각각 하나씩 연결할 수 있다. 소셜 전용 회원(비밀번호 없음)은 `POST /auth/login`이 401 `INVALID_CREDENTIALS`다.

## API

| 기능 | Method | Endpoint | 인증 | 상태 코드 |
| --- | --- | --- | --- | --- |
| 소셜 로그인 | POST | `/api/v1/auth/social/{provider}` | 없음 | 200 기존 회원 로그인·연결, 201 신규 회원 생성 |

- `provider`: `kakao` 또는 `google`(대소문자 무관). 그 외 400 `SOCIAL_PROVIDER_NOT_SUPPORTED`.
- Body: `{"token": "..."}` — 카카오는 **access token**, 구글은 **ID token**(JWT). 공백 불가, 4096자 이하.
- 응답 본문은 `POST /auth/login`과 같은 `LoginResponse`다. 클라이언트는 201이면 온보딩(약관·금융 연동)으로, 200이면 홈으로 보내면 된다.

```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "accessTokenExpiresAt": "2026-09-15T13:30:00",
    "refreshToken": "8f3c...",
    "refreshTokenExpiresAt": "2026-09-29T13:00:00",
    "user": { "userId": 4001, "email": "dodam@gmail.com", "name": "도담", "role": "USER" }
  },
  "message": null,
  "code": null
}
```

### 오류 코드

| 코드 | HTTP | 발생 조건 |
| --- | --- | --- |
| `SOCIAL_PROVIDER_NOT_SUPPORTED` | 400 | provider가 kakao·google이 아님 |
| `VALIDATION_ERROR` | 400 | token 누락·공백·4096자 초과 |
| `SOCIAL_TOKEN_INVALID` | 401 | 공급자가 토큰을 거절(만료·위조), 구글 iss/aud 불일치, 카카오 다른 앱의 토큰(app-id 설정 시) |
| `USER_WITHDRAWN` | 403 | 연결된 회원이 탈퇴 상태 |
| `SOCIAL_ACCOUNT_ALREADY_LINKED` | 409 | 이메일이 같은 회원에 같은 공급자의 다른 소셜 계정이 이미 연결됨(동시 로그인 충돌 포함) |
| `EMAIL_ALREADY_EXISTS` | 409 | 같은 이메일의 회원이 동시에 생성됨(재시도하면 연결·로그인으로 처리) |
| `SOCIAL_PROVIDER_UNAVAILABLE` | 503 | 공급자 API 타임아웃·5xx·응답 해석 불가, 구글 공개키 조회 실패, 구글 클라이언트 ID 미설정 |

세부 원인(카카오 오류 코드, JWT 예외 메시지)은 서버 로그에만 남긴다.

## 검증 방식

| 공급자 | 토큰 | 검증 | 신원 필드 |
| --- | --- | --- | --- |
| 카카오 (`KakaoIdentityClient`) | access token | `GET https://kapi.kakao.com/v2/user/me` (Bearer). 401·403·400 → 토큰 무효, 5xx·타임아웃 → 공급자 장애. `app.social.kakao.app-id`가 있으면 `GET /v1/user/access_token_info`의 `app_id`를 먼저 대조 | `id` → providerUserId, `kakao_account.email`(+`is_email_valid`·`is_email_verified`), `kakao_account.profile.nickname` 또는 `properties.nickname` |
| 구글 (`GoogleIdentityClient`) | ID token | `NimbusJwtDecoder`(JWK `https://www.googleapis.com/oauth2/v3/certs`)로 서명·exp 검증 후 `iss ∈ {https://accounts.google.com, accounts.google.com}`, `aud ∩ client-ids ≠ ∅` 확인 | `sub` → providerUserId, `email`, `email_verified`, `name` |

공급자 검증기는 `SocialIdentityClient` 인터페이스로 분리되어 있고 `SocialIdentityClients`가 공급자별로 찾는다. 네이버 등 추가 시 구현 빈 하나와 `SocialProvider` 상수를 더한다.

## 저장 (V34 `user_oauth_account`)

ERD 정의 그대로다. 공급자 사용자 ID는 원문을 저장하지 않고 `FieldCryptor.hash`(HMAC-SHA-256, CHAR(64))로만 둔다. 공급자 이메일은 있으면 암호문·해시·마스킹(`do***@example.com`)으로 저장하고 로그인마다 갱신한다.
`(provider, provider_user_id_hash)`·`(user_id, provider)` UNIQUE. `provider` CHECK는 `KAKAO`·`GOOGLE`.

## 설정

| 키 | 환경변수 | 의미 |
| --- | --- | --- |
| `app.social.kakao.app-id` | `KAKAO_APP_ID` | 카카오 개발자 콘솔 **앱 ID**(숫자, REST API 키와 다름). 비우면 "우리 앱에서 발급된 토큰인지" 검사를 생략한다 |
| `app.social.google.client-ids` | `GOOGLE_CLIENT_ID` | ID token `aud`로 허용할 OAuth 클라이언트 ID. Android·웹 클라이언트를 모두 쓰면 쉼표로 이어 적는다. **비어 있으면 구글 로그인은 503** |
| `app.social.connect-timeout` / `read-timeout` | — | 카카오 API 타임아웃(기본 3초/5초) |

`infra/.env.example`의 `KAKAO_CLIENT_SECRET`·`GOOGLE_CLIENT_SECRET`은 이 흐름에서 쓰지 않는다(서버 리다이렉트 방식으로 바꿀 때만 필요).

## 테스트

- `SocialAuthApiIntegrationTest`(H2, 13개): 공급자 검증기를 Mock으로 두고 신규 생성(대체 이메일·실제 이메일·금융망 연결 상태), 재로그인 200, 이메일 일치 연결, 같은 공급자 중복 409, 공급자 2개 연결, 소셜 전용 회원의 비밀번호 로그인 401, 탈퇴 403, 토큰 무효 401, 공급자·토큰 검증 400.
- `KakaoIdentityClientTest`(MockRestServiceServer, 6개): 신원 매핑, 미확인 이메일, 401→무효, 5xx→장애, app-id 대조.
- `GoogleIdentityClientTest`(디코더 대체, 8개): 클레임 매핑, aud·iss 불일치, 만료→무효, JWK 실패→장애, 미설정→장애.
- 실제 카카오·구글 토큰으로의 검증은 개발자 콘솔 앱과 FE SDK 연동이 있어야 가능해 아직 하지 않았다("남은 작업").

## FE·인프라 인계

FE(로그인 화면 `SocialLoginButton` 카카오·구글, 네이버 버튼은 백엔드 미지원):

1. 카카오: 카카오 로그인 SDK(예: `@react-native-seoul/kakao-login`)로 로그인 → `accessToken`을 `POST /api/v1/auth/social/kakao`에 보낸다. 이메일을 받으려면 카카오 콘솔 동의항목에서 이메일을 켜야 하고, 사용자가 거부하면 대체 이메일 회원이 된다.
2. 구글: Google Sign-In(`@react-native-google-signin/google-signin` 또는 `expo-auth-session`)으로 로그인 → **`idToken`**을 `POST /api/v1/auth/social/google`에 보낸다(access token이 아니다). Android 클라이언트 ID로 받은 ID token의 `aud`는 그 클라이언트 ID이므로 서버 `GOOGLE_CLIENT_ID`에 같은 값을 넣는다(웹 클라이언트 ID로 발급받는 구성이면 그 값도 함께).
3. 응답은 이메일 로그인과 같다. 201이면 신규 회원이므로 약관·온보딩 화면으로, 200이면 홈으로.
4. 소셜 전용 회원은 비밀번호가 없어 `POST /auth/login`을 쓸 수 없다. 로그아웃·토큰 갱신·세션 API는 그대로 쓴다.

인프라(`infra/docs/external-integrations.md` 4절 절차 유지):

- 카카오 개발자 콘솔에서 Android 플랫폼(패키지명 `com.ssafy.d105.dodam`, 키 해시) 등록 후 **앱 ID**를 `KAKAO_APP_ID`에(선택), 구글 클라우드 콘솔의 OAuth 클라이언트 ID를 `GOOGLE_CLIENT_ID`에 넣고 backend 컨테이너를 재기동한다.
- 서버는 카카오(`kapi.kakao.com`)·구글(`www.googleapis.com`)로 아웃바운드 HTTPS가 가능해야 한다.

## 남은 작업

`remaining-work.md` "소셜 로그인" 절에 기록한다.
