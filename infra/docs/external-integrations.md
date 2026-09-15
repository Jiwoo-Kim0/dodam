# 외부 연동 설정값

각 콘솔에 등록해야 하는 정확한 값과 발급 절차. 최종 갱신 2026-09-11.

관련 문서: [CI/CD](ci-cd.md) · [보안 방침](../todo/security.md)

> **실제 키 값은 이 문서에 적지 않는다.** 값은 EC2의 `infra/.env`(권한 600)와
> `infra/secrets/`에만 존재한다.

---

## 확정된 앱 식별자

FE팀 확정 (2026-09-10). 이 값들이 아래 모든 등록의 기준이 된다.

| 항목 | 값 | 쓰이는 곳 |
| --- | --- | --- |
| Android 패키지명 | `com.ssafy.d105.dodam` | Firebase 앱 등록, Google OAuth, 스토어 식별자 |
| 앱 scheme | `dodam` | 딥링크 (소셜 로그인에는 쓰이지 않음 — §4 참고) |
| API BaseURL 변수 | `EXPO_PUBLIC_API_BASE_URL` | FE 빌드 시 주입 |
| API BaseURL (운영) | `https://j15D105.p.ssafy.io/api` | 〃 |

> ⚠️ 패키지명은 **한번 정하면 바꾸기 어렵다.** Firebase 앱 등록과 OAuth 클라이언트가
> 이 값에 묶이고, 스토어 배포 시에는 사실상 변경 불가다.

---

## 서버 정보

| 항목 | 값 |
| --- | --- |
| 도메인 | `j15D105.p.ssafy.io` |
| **공인 IP** | `43.203.198.18` |
| API 진입점 | `https://j15D105.p.ssafy.io/api` |

공인 IP는 **금융망이 아웃바운드 화이트리스트를 요구할 경우** 등록할 값이다.

---

## 1. SSAFY 교육용 금융망

**연결성 확인 완료 (2026-09-10)**

| 검사 | 결과 |
| --- | --- |
| EC2 호스트 → `https://finopenapi.ssafy.io` | HTTP 200 |
| backend 컨테이너 → 금융망 | HTTP 200 |

→ **네트워크 차단 없음.** IP 화이트리스트 등록 없이도 호출이 나간다.
(금융망 측이 인증 단계에서 IP를 확인할 가능성은 남아 있으므로, 실제 API 호출이
인증 오류로 막히면 위 공인 IP 등록을 요청할 것)

**설정 위치** — 백엔드 `application.yml`에 이미 정의되어 있다.
```yaml
app:
  ssafy-finance:
    base-url: https://finopenapi.ssafy.io
    connect-timeout: PT3S
    read-timeout: PT5S
    api-key: ${SSAFY_FINANCE_API_KEY}   # application-prod.yml
```
API Key는 `.env`에 설정 완료.

---

## 2. OpenAI

**연결성 확인 완료** — backend 컨테이너 → `api.openai.com` HTTP 401
(인증 없이 호출했으므로 401이 정상. 네트워크는 열려 있음)

API Key는 `.env`에 설정 완료.

> 비용 관리는 OpenAI 콘솔에서 사용량 알림을 설정할 것. 인프라 범위 밖이지만
> 키가 유출되면 과금으로 직결되므로 `.env` 권한(600)과 백업 원칙을 지킬 것.

---

## 3. Firebase (FCM 푸시 알림)

### 발급 절차 (완료 — 키 교체 시 같은 절차를 따른다)

1. Firebase 콘솔에서 프로젝트 생성
2. **Android 앱 추가** → 패키지명 **`com.ssafy.d105.dodam`** 입력
3. 프로젝트 설정 → **서비스 계정** → "새 비공개 키 생성" → JSON 다운로드
4. EC2로 옮기고 **소유권을 컨테이너 유저에게 넘긴다** (**레포 안에 두지 말 것**)
   ```bash
   scp <다운로드파일>.json ssafy-ec2:/home/ubuntu/app/infra/secrets/fcm-service-account.json
   ssh ssafy-ec2 'sudo chown 10001:10001 ~/app/infra/secrets/fcm-service-account.json \
                  && sudo chmod 600 ~/app/infra/secrets/fcm-service-account.json'
   ```
   상세 절차와 주의점: [`infra/secrets/README.md`](../secrets/README.md)

### 현재 상태 — 활성화 완료 (2026-09-10)

키를 수령해 마운트를 활성화했다. 컨테이너 경로는 `/run/secrets/fcm-service-account.json`.

**설치 중 걸린 문제 두 가지 (다음에 키를 교체할 때 참고)**

1. **키가 없는 상태로 마운트를 걸면 안 된다** — Docker가 해당 경로에 **디렉터리를 생성**해버려
   나중에 파일을 넣어도 꼬인다. 그래서 키 수령 전까지 compose 설정을 주석으로 두었다.

2. **파일 소유권을 옮겨야 한다** — backend는 비루트(`uid 10001`)로 실행되는데,
   `scp`로 올린 파일은 `ubuntu`(uid 1000) 소유 600이라 **컨테이너가 읽지 못한다.**
   마운트 자체는 성공하고 컨테이너도 healthy로 뜨기 때문에 겉으로는 정상으로 보이고,
   **푸시 발송 코드가 붙은 뒤에야 런타임에서 실패**한다.
   → `sudo chown 10001:10001` 후 `docker exec backend head -c 25 <경로>` 로 실제 읽힘을 확인할 것.

### FE·백엔드 쪽 남은 작업

- FE: `expo-notifications` 설치됨 ✅ / 토큰 발급 흐름 구현 필요
- 백엔드: `user_device` 테이블(ERD에 정의됨)에 토큰을 저장하는 API가 아직 없다
  → FE와 계약을 맞춰야 함. 인프라는 **서버 키 보관·주입만** 담당

---

## 4. 소셜 로그인 (Kakao / Google)

### 인증 흐름 — 2026-09-11 백엔드 확정

**앱이 네이티브 SDK로 로그인하고, 서버는 받은 토큰만 검증한다.**

```
앱 ──네이티브 SDK 로그인──> Kakao / Google
앱 ──POST /api/v1/auth/oauth/{provider} (토큰)──> 서버
                                                 서버 ──토큰 검증──> provider
                                                 user_oauth_account 매핑
앱 <──────── 우리 JWT (access/refresh) ────────── 서버
```

- 앱이 받는 것: 카카오 access token 또는 OIDC `id_token` / 구글 `id_token`
- 서버 검증: 카카오 `GET https://kapi.kakao.com/v2/user/me` 또는 `id_token` 검증,
  구글 `id_token` 서명·`aud`·만료 검증 (RestClient로 직접 호출)
- 매핑: `user_oauth_account`(provider + `provider_user_id_hash`) → 기존 JWT 발급

**서버 Redirect 방식을 쓰지 않는 이유**: RN 앱에서는 웹뷰·딥링크 왕복이 필요해 UX가 나쁘고,
ERD도 provider 토큰을 저장하지 않는 구조다.

> `build.gradle`에 `spring-boot-starter-oauth2-client`가 있지만 **이 흐름에서는 쓰지 않는다.**
> 초기 세팅 때 향후 확장용으로 넣은 것이다. 이 의존성만 보고 "서버 Redirect 방식"으로
> 오해하지 말 것.

### ⚠️ 이 흐름에서는 Redirect URI를 등록하지 않는다

네이티브 SDK는 **콘솔의 "Redirect URI" 항목을 쓰지 않는다.** 그 필드는 REST API / JS SDK
흐름용이다. SDK는 자체 고정 스킴으로 돌아온다.

| 값 | 정체 |
| --- | --- |
| `kakao{네이티브앱키}://oauth` | **카카오 SDK가 쓰는 값.** 고정이며 선택할 수 없다 |
| `dodam://oauth/kakao` | `app.json`의 `scheme` 기반. **이 흐름에서는 쓰이지 않는다** |

둘은 별개의 intent-filter이고 `scheme: "dodam"`과 무관하다.
`dodam://oauth/kakao`를 콘솔에 등록해도 무해하지만 **아무 역할도 하지 않는다.**

**대신 FE가 해야 하는 일**: AndroidManifest에 `kakao{앱키}://oauth` intent-filter가 들어가야
한다. 빠지면 **로그인 후 앱으로 복귀하지 못한다.** Expo prebuild 환경이므로 카카오 로그인
라이브러리의 config plugin 또는 `app.json`의 `android.intentFilters`로 넣는다.
(2026-09-11 현재 `app.json`에 없음)

구글도 같다 — Android·웹 클라이언트 **어느 쪽도 이 흐름에서 redirect URI를 쓰지 않는다.**
Credential Manager가 처리한다.

### Client Secret은 양쪽 모두 필요 없다

서버가 authorization code를 토큰으로 교환하지 않으므로 secret을 쓸 자리가 없다.

- **Kakao**: 콘솔에서 Client Secret을 **켜지 않는다.** 토큰 발급을 네이티브 SDK가 대신하므로
  켤 이유가 없고 흐름이 어긋날 소지만 생긴다.
- **Google**: 웹 클라이언트는 **ID만** 쓴다. Secret은 발급돼도 **어디에도 배포하지 않는다.**

### Kakao Developers — 등록할 것

1. 애플리케이션 생성
2. **플랫폼 → Android 등록** ← 이 흐름에서 콘솔에 반드시 필요한 것
   - 패키지명: `com.ssafy.d105.dodam`
   - 키 해시: SHA-1을 Base64로 변환한 값 (§5의 조회 절차)
   - **debug keystore를 쓰는 팀원의 키 해시도 각각 등록**
3. 카카오 로그인 **활성화 ON** (기본 OFF)
4. 동의항목 — 백엔드가 쓸 항목만 (닉네임/이메일 등)
5. OIDC `id_token`을 쓸 경우 **OpenID Connect 활성화 ON** (기본 OFF, 안 켜면 발급되지 않음)

**서버에 필요한 값** — 검증 방식에 따라 다르다.

| 방식 | 서버에 필요한 카카오 값 |
| --- | --- |
| `GET /v2/user/me` + 사용자 access token | **없음** (사용자 토큰만 Bearer로 실음) |
| OIDC `id_token` 검증 | 앱 키 1개 (`aud` 비교용) → `.env`의 `KAKAO_APP_KEY` |

> ⚠️ 카카오 앱 키는 **4종**(네이티브 앱 키 / REST API 키 / JavaScript 키 / Admin 키)이다.
> `aud`에 어느 것이 들어오는지는 **실제 발급된 `id_token`을 디코드해 확인**하고 넣을 것.
> 잘못 넣으면 "aud 불일치"로만 보여서 원인이 드러나지 않는다.
> FE의 SDK 초기화에는 **네이티브 앱 키**를 쓴다.

### Google Cloud Console — 등록할 것

> 💡 **새 프로젝트를 만들지 않는다.** FCM 때 만든 **Firebase 프로젝트가 곧 GCP 프로젝트**다.

**먼저 확인할 것 — 자동 생성된 클라이언트가 이미 있을 수 있다.**
Firebase 콘솔에서 Android 앱에 SHA-1을 추가하면 **Android OAuth 클라이언트가 자동 생성**되고,
Google 로그인을 활성화하면 **웹 클라이언트도 `Web client (auto created by Google Service)`
이름으로 자동 생성**된다.
→ 수동 생성 전에 **GCP → API 및 서비스 → 사용자 인증 정보** 목록을 먼저 볼 것.
중복 생성하면 어느 ID를 `serverClientId`에 넣어야 하는지 헷갈린다.
SHA-1 추가 후 `google-services.json`은 **재다운로드**해야 한다.

필요한 것 3가지:

| 항목 | 내용 |
| --- | --- |
| **웹 애플리케이션 유형 클라이언트** | Android SDK(Credential Manager)가 `id_token`을 받으려면 `serverClientId`로 이것이 필요하다. **ID만** 쓰고 Secret은 배포하지 않는다 → `.env`의 `GOOGLE_OAUTH_CLIENT_ID` |
| **Android 유형 클라이언트** | 패키지명 `com.ssafy.d105.dodam` + SHA-1 |
| **OAuth 동의 화면** | User Type External, 앱 이름/지원 이메일 + **테스트 사용자에 팀원 계정 전부** |

> **미검증 앱은 테스트 사용자만 로그인된다.** 테스트 사용자 등록을 빠뜨리면 등록 안 된
> 팀원 계정에서만 로그인이 막혀서, 원인을 찾기 전까지 "특정 사람만 안 된다"로 보인다.

**서버 검증**: `id_token`의 서명·만료를 확인하고 **`aud`에 우리 웹 클라이언트 ID가 있는지**만
보면 된다. Client Secret은 쓰지 않는다.

### `.env`에 들어가는 값

Secret은 **0개**다.

```
KAKAO_APP_KEY=              # OIDC 검증 방식일 때만 (aud 비교용)
GOOGLE_OAUTH_CLIENT_ID=     # 웹 클라이언트 ID (aud 비교용)
```

> ⚠️ 백엔드가 이 값을 참조하기 시작하면 `docker-compose.yml`의 backend environment와
> `application-prod.yml` **양쪽에** 추가해야 한다. 한쪽만 넣으면 `check-env.sh`가 잡아낸다
> (2026-09-08 `JWT_SECRET` 유실 장애와 같은 유형).
> 2026-09-11 현재 두 파일 모두 이 변수를 참조하지 않는다.

---

## 5. FE 빌드 설정

### 환경변수

FE가 `EXPO_PUBLIC_API_BASE_URL`로 API 주소를 읽는다.
```
EXPO_PUBLIC_API_BASE_URL=https://j15D105.p.ssafy.io/api
```

`EXPO_PUBLIC_` 접두사가 붙은 변수는 **빌드 시 앱 번들에 그대로 포함된다.**
즉 APK를 뜯으면 값이 보인다. **API 주소처럼 공개돼도 되는 값만** 넣을 것.
API 키나 시크릿은 절대 이 방식으로 넣으면 안 된다.

### CORS 불필요

네이티브 앱은 브라우저가 아니므로 CORS가 적용되지 않는다.
다만 `.env`의 `CORS_ALLOWED_ORIGINS` 변수 자체는 **비워두더라도 반드시 있어야 한다** —
`application-prod.yml`이 이 변수를 참조하므로, 없으면 백엔드가 기동하지 않는다.

### HTTPS는 선택이 아니라 필수

Android는 targetSdk 28+부터 평문 HTTP 통신을 기본 차단한다.
Day 3에 적용한 Let's Encrypt 인증서는 공개 신뢰 체인이므로 앱에서 별도 설정 없이 동작한다.
(자체 서명 인증서였다면 앱에 인증서를 심어야 했다)

### APK 빌드 — EAS Build 사용 (2026-09-10 확정)

클라우드 빌드라 EC2와 무관하다. 인프라가 서버 쪽에서 할 일은 없지만,
**소셜 로그인 등록에 필요한 SHA-1이 여기서 나오므로** 절차를 정리해둔다.

**EAS를 택한 이유**
- keystore를 Expo가 클라우드에서 관리한다 → **분실 위험이 없다**
  로컬 빌드였다면 릴리즈 keystore를 잃는 순간 앱을 영영 업데이트할 수 없다
  (스토어가 서명 키로 앱을 식별하므로 새 키는 "다른 앱"이 된다)
- 팀원 누구나 같은 키로 빌드한다
- SHA-1 조회가 명령 하나

**현재 상태 (2026-09-11 재확인)**: `frontend/eas.json` 없음, `frontend/android/` 없음(prebuild 전).
빌드 방식만 EAS로 확정된 상태이고 **설정은 아직 안 되어 있다** — 아래 절차는 FE팀 대기.

#### 준비 절차 (FE팀)

```bash
npm install -g eas-cli
eas login                 # Expo 계정 필요 — 팀 공용 계정 권장
cd frontend && eas init
```

#### `eas.json` 참고 템플릿

`EXPO_PUBLIC_API_BASE_URL`을 프로파일별로 주입한다.

```json
{
  "cli": { "version": ">= 5.0.0" },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "env": { "EXPO_PUBLIC_API_BASE_URL": "https://j15D105.p.ssafy.io/api" }
    },
    "preview": {
      "distribution": "internal",
      "android": { "buildType": "apk" },
      "env": { "EXPO_PUBLIC_API_BASE_URL": "https://j15D105.p.ssafy.io/api" }
    },
    "production": {
      "android": { "buildType": "apk" },
      "env": { "EXPO_PUBLIC_API_BASE_URL": "https://j15D105.p.ssafy.io/api" }
    }
  }
}
```

> ⚠️ **`buildType: "apk"`를 명시해야 한다.**
> EAS의 production 프로파일은 기본이 **AAB**(Play 스토어 업로드용)라서, 그대로 두면
> 팀원에게 바로 설치시킬 수 있는 APK가 나오지 않는다.
> 스토어 배포가 아니라 시연·내부 공유가 목적이므로 APK로 지정한다.

#### 빌드

```bash
eas build -p android --profile preview     # 팀 공유용 APK
```
빌드가 끝나면 Expo가 다운로드 링크를 준다. 그 링크를 팀에 공유하면 된다.

#### SHA-1 지문 조회 (소셜 로그인 등록용)

```bash
eas credentials -p android
```
→ 빌드 프로파일 선택 → Keystore 정보에 SHA-1 / SHA-256이 표시된다.

**카카오 키 해시**는 SHA-1을 Base64로 변환한 값이다.
```bash
# SHA-1이 AA:BB:CC... 형태로 나오면
echo AA:BB:CC:... | xxd -r -p | openssl base64
```

> ⚠️ **debug keystore는 개발자 PC마다 다르다.**
> `expo run:android`처럼 로컬에서 디버그 빌드로 테스트하는 사람이 있다면,
> **그 사람들의 debug SHA-1도 각각 콘솔에 등록**해야 한다.
> 안 그러면 "나는 되는데 쟤는 소셜로그인이 안 된다"가 된다.
> ```bash
> keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
>         -storepass android -keypass android | grep SHA1
> ```

#### 시크릿 관점

EAS가 keystore를 관리하므로 **인프라가 보관해야 할 서명 키는 없다.**
대신 **Expo 계정**이 그 자리를 대신한다 — 계정을 잃으면 같은 키로 빌드할 수 없다.
팀 공용 계정으로 만들고 접근 가능한 사람을 정해둘 것.

`eas.json`의 `env`에 들어가는 값은 **APK에 그대로 포함된다.** API 주소만 넣고,
키나 시크릿은 절대 넣지 말 것.

---

## 발급 현황

| 항목 | 상태 | 담당 |
| --- | --- | --- |
| SSAFY 금융망 API Key | ✅ 설정됨 | 백엔드 |
| OpenAI API Key | ✅ 설정됨 | ? |
| Firebase 서비스 계정 키 | ✅ 발급·마운트 완료 (2026-09-10) | 인프라 |
| Kakao 네이티브 앱 키 + Android 플랫폼 등록 | ❌ **미발급** | ? |
| Google 웹/Android 클라이언트 + 동의화면 | ❌ **미발급** | ? |

미발급 2건(Kakao / Google OAuth)은 **로그인 기능의 선행조건**이다.
패키지명과 scheme이 확정됐고 SHA-1 조회 절차도 정리됐으므로 바로 발급받을 수 있다.
발급되면 `.env`에 넣고 backend를 재기동하면 끝난다 — 인프라 쪽 추가 작업은 없다.
