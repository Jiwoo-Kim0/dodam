# 외부 연동 설정값

각 콘솔에 등록해야 하는 정확한 값과 발급 절차. 최종 갱신 2026-09-10.

관련 문서: [CI/CD](ci-cd.md) · [보안 방침](../todo/security.md)

> **실제 키 값은 이 문서에 적지 않는다.** 값은 EC2의 `infra/.env`(권한 600)와
> `infra/secrets/`에만 존재한다.

---

## 확정된 앱 식별자

FE팀 확정 (2026-09-10). 이 값들이 아래 모든 등록의 기준이 된다.

| 항목 | 값 | 쓰이는 곳 |
| --- | --- | --- |
| Android 패키지명 | `com.ssafy.d105.dodam` | Firebase 앱 등록, Google OAuth, 스토어 식별자 |
| 앱 scheme | `dodam` | 소셜 로그인 Redirect URI |
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

### 발급 절차 (사용자 액션)

1. Firebase 콘솔에서 프로젝트 생성
2. **Android 앱 추가** → 패키지명 **`com.ssafy.d105.dodam`** 입력
3. 프로젝트 설정 → **서비스 계정** → "새 비공개 키 생성" → JSON 다운로드
4. EC2로 옮긴다 (**레포 안에 두지 말 것**)
   ```bash
   scp <다운로드파일>.json ssafy-ec2:/home/ubuntu/app/infra/secrets/fcm-service-account.json
   ssh ssafy-ec2 'chmod 600 ~/app/infra/secrets/fcm-service-account.json'
   ```
5. 인프라 담당에게 알린다 → compose의 FCM 마운트를 활성화하고 백엔드를 재기동한다

### 현재 상태

`docker-compose.yml`에 마운트 설정이 **주석 처리**되어 있다.

키 파일이 없는 상태로 bind mount를 걸면 **Docker가 그 경로에 디렉터리를 생성**해버려,
나중에 파일을 넣어도 꼬인다. 그래서 키를 받은 뒤 활성화한다.

활성화 시 주석을 해제할 두 곳:
- `environment`의 `FCM_SERVICE_ACCOUNT_PATH`
- `volumes`의 `./secrets/fcm-service-account.json` 마운트

컨테이너 안 경로는 `/run/secrets/fcm-service-account.json` 으로 고정.

### FE·백엔드 쪽 남은 작업

- FE: `expo-notifications` 설치됨 ✅ / 토큰 발급 흐름 구현 필요
- 백엔드: `user_device` 테이블(ERD에 정의됨)에 토큰을 저장하는 API가 아직 없다
  → FE와 계약을 맞춰야 함. 인프라는 **서버 키 보관·주입만** 담당

---

## 4. 소셜 로그인 — 등록할 Redirect URI

**네이티브 앱이라 웹 방식(`https://...`)이 아니다.** 커스텀 스킴을 등록한다.

### Kakao Developers

1. 애플리케이션 생성 → **플랫폼 → Android 등록**
   - 패키지명: `com.ssafy.d105.dodam`
   - 키 해시: FE팀이 빌드 시 생성해 등록 (디버그/릴리즈 각각)
2. **카카오 로그인 → Redirect URI**
   ```
   dodam://oauth/kakao
   ```
3. 발급받은 **REST API 키**와 **Client Secret**을 인프라 담당에게 전달
   → `.env`의 `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`

### Google Cloud Console

1. **OAuth 클라이언트 ID 생성 → 애플리케이션 유형: Android**
   - 패키지 이름: `com.ssafy.d105.dodam`
   - SHA-1 인증서 지문: FE팀이 빌드 키에서 추출해 등록
2. 백엔드에서 토큰을 검증하려면 **웹 애플리케이션 유형 클라이언트도 별도로 필요**할 수 있다
   (구현 방식에 따라 다르므로 백엔드팀과 확인)
3. 발급값을 인프라 담당에게 전달 → `.env`의 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`

> **Android OAuth 클라이언트는 Client Secret이 없다.** 패키지명 + SHA-1 지문으로
> 앱을 식별하므로, 백엔드가 secret을 요구하는 흐름이라면 웹 클라이언트를 함께 만들어야 한다.

### 왜 커스텀 스킴인가

앱은 브라우저가 아니라서 로그인 완료 후 돌아올 곳이 URL이 아니다.
`dodam://oauth/kakao` 같은 스킴을 OS가 앱으로 연결해준다.
`app.json`의 `scheme: "dodam"` 값이 이 앞부분과 일치해야 한다.

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

### APK 배포

EAS Build(클라우드) 또는 로컬 빌드 중 FE팀이 선택.
EC2와 무관하므로 인프라가 개입할 부분은 없다.
다만 **APK를 어디에 올려 공유할지**는 팀에서 정할 것.

---

## 발급 현황

| 항목 | 상태 | 담당 |
| --- | --- | --- |
| SSAFY 금융망 API Key | ✅ 설정됨 | 백엔드 |
| OpenAI API Key | ✅ 설정됨 | ? |
| Firebase 서비스 계정 키 | ❌ **미발급** | ? |
| Kakao OAuth | ❌ **미발급** | ? |
| Google OAuth | ❌ **미발급** | ? |

미발급 3건은 **Day 7 연동 작업의 선행조건**이다.
패키지명과 scheme이 확정됐으므로 이제 바로 발급받을 수 있다.
