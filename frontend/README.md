# 도담 Frontend

도담은 사용자의 자산과 소비 흐름을 분석하고, 목표 달성을 방해하는 금융 비효율을 발견해 다음 행동을 제안하는 청년 금융 코치 앱입니다.

프론트엔드는 **React Native와 Expo 기반의 Android 우선 모바일 앱**으로 구현되어 있습니다. 금융 데이터 조회부터 목표 관리, 또래 비교, 시뮬레이션과 금융 코치 채팅까지 하나의 앱 흐름으로 제공합니다.

## 주요 기능

### 인증과 금융 연결

- Google 로그인 및 내부 시연용 로그인
- 약관 동의와 본인 확인
- 금융망 연결, 인증서 선택과 자산 동기화
- 로그인 세션 복원과 Access Token 자동 재발급
- 알림 권한 요청과 읽지 않은 알림 표시

### 홈과 목표

- 자산·월간 소비·다가오는 출금 일정 요약
- 데일리 브리핑과 주요 소비 항목 확인
- 모으기·줄이기 목표 생성, 수정과 삭제
- 목표 진행률, 예상 달성일과 주간·월간 변화 확인
- 목표 연결 계좌 관리와 도담 계좌 금액 배분

### 가계부

- 월간·주간 달력과 날짜별 거래 내역
- 거래 검색, 추가, 수정, 삭제와 숨김 처리
- 카테고리별 소비와 월별 소비 흐름 분석
- 거래가 있는 날짜로 이동하는 스크롤 내비게이션
- 직접 입력 거래의 금액·날짜·시간 검증

### 자산

- 입출금·예적금·대출·카드·할부 자산 조회
- 자산과 부채 비율 시각화
- 마스킹된 계좌 정보 표시
- 도담 계좌와 일반 입출금 계좌 간 이체
- 이체 중 중복 제출 방지와 완료 후 잔액 갱신

### 발견과 시뮬레이션

- 금융 비효율 진단과 우선 확인 항목 제공
- 소비 유형과 또래 군집 비교
- 소비·저축 시뮬레이션과 대출 갈아타기 효과 비교
- 진단 항목별 개선 효과와 근거 확인
- 데이터 부족, 진단 대기와 오류 상태 구분

### 토리 금융 코치

- 금융 요약, 진단 항목과 시뮬레이션 결과 기반 질의
- 추천 질문과 후속 질문 제공
- 응답 대기, 재시도와 새 대화 시작 지원

### 리포트

- 월간 금융 효율 점수와 항목별 관리 점수
- 금융 패턴과 또래 비교
- 주요 금융 지표와 최근 월별 소비
- 과거 소비와 향후 예상 소비 그래프
- 추세·계절성 등 소비 예측 근거 표시

## 화면 구성

하단 내비게이션은 다음 다섯 영역으로 구성됩니다.

| 탭 | 내용 |
| --- | --- |
| 홈 | 목표, 소비, 자산, 브리핑과 리포트 진입 |
| 가계부 | 달력, 거래 내역과 소비 분석 |
| 토리 | 금융 코치 채팅 |
| 자산 | 계좌·카드·대출·예적금 조회와 이체 |
| 발견 | 금융 비효율 진단, 또래 비교와 시뮬레이션 |

리포트, 알림, 데일리 브리핑과 목표 상세 화면은 각 기능의 진입 카드에서 열립니다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Framework | Expo SDK 57, React Native 0.86 |
| UI | React 19, React Native SVG, Expo Image, Expo Symbols |
| Language | TypeScript 6 (`strict`) |
| Routing | Expo Router, Typed Routes |
| Animation | React Native Animated, Reanimated |
| Device | Expo Notifications, Device, SecureStore |
| Styling | React Native StyleSheet, 공통 디자인 토큰, Pretendard |
| Build | EAS Build |
| Quality | ESLint 9, TypeScript Compiler |

별도의 전역 상태 관리 라이브러리 없이 화면별 Hook과 서비스 계층을 중심으로 상태를 관리합니다.

## 프로젝트 구조

```text
frontend/
├─ assets/                   이미지, 아이콘, 캐릭터와 폰트
├─ src/
│  ├─ app/                   Expo Router 라우트와 레이아웃
│  ├─ api/                   API 모듈과 공통 HTTP 클라이언트
│  ├─ components/            여러 화면에서 사용하는 공통 컴포넌트
│  ├─ constants/             라우트 등 전역 상수
│  ├─ hooks/                 공통 Custom Hook
│  ├─ layout/                Safe Area 등 공통 화면 레이아웃
│  ├─ mocks/                 로고 메타데이터와 입력 초기값
│  ├─ pages/                 화면 UI와 화면 전용 로직
│  ├─ services/              인증 토큰과 플랫폼 서비스
│  ├─ store/                 전역 상태 확장 위치
│  ├─ styles/                색상·폰트·간격·반경·그림자 토큰
│  ├─ types/                 API 및 도메인 타입
│  └─ utils/                 포맷팅·검증·매핑 유틸리티
├─ .env.example              로컬 환경변수 예시
├─ app.json                  Expo 앱 설정
├─ eas.json                  EAS 빌드 프로필
└─ package.json              의존성과 실행 스크립트
```

라우트 파일에는 화면 연결과 파라미터 전달만 두고, 실제 화면과 비즈니스 로직은 `src/pages`에서 관리합니다. 화면 전용 컴포넌트와 Hook은 해당 페이지 폴더에 함께 둡니다.

## 데이터 흐름

```text
Expo Router
    ↓
Screen
    ↓
화면 전용 Hook
    ↓
도메인 API 모듈
    ↓
공통 apiClient
    ↓
Backend API
```

공통 API 클라이언트는 다음 동작을 담당합니다.

- Base URL과 API 경로 결합
- JSON 요청과 공통 응답 형식 처리
- Access Token 자동 첨부
- `401` 응답 시 토큰 재발급 후 한 번 재시도
- 기본 15초 타임아웃과 네트워크 오류 구분
- 화면 이탈 시 요청 취소 지원

인증 토큰은 Android의 `SecureStore`에 저장하며 기기가 잠금 해제된 동안에만 접근할 수 있도록 설정합니다.

## 실행 환경

- Node.js LTS
- npm
- Java/JDK 21
- Android Studio와 Android Emulator 또는 Android 실기기
- EAS Build 사용 시 Expo 계정

명령어는 `frontend/` 디렉터리에서 실행합니다.

## 환경변수

로컬 환경변수 파일을 생성합니다.

```bash
cp .env.example .env
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
Copy-Item .env.example .env
```

| 환경변수 | 설명 | 필수 여부 |
| --- | --- | --- |
| `EXPO_PUBLIC_API_BASE_URL` | 백엔드 API Base URL. `/api`로 끝나는 주소를 사용합니다. | 필수 |
| `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID` | Google 로그인의 웹 애플리케이션 OAuth Client ID | Google 로그인 사용 시 필수 |
| `EXPO_PUBLIC_ENABLE_DEMO_LOGIN` | 릴리스 빌드에서 내부 시연용 로그인을 표시할지 결정합니다. | Preview APK에서 데모 로그인 사용 시 필수 |

`EXPO_PUBLIC_` 환경변수는 빌드 결과물에서 확인할 수 있는 공개 설정입니다. Client Secret, Access Token, Refresh Token, 비밀번호와 실제 개인정보는 넣지 않습니다.

## 설치와 실행

의존성을 설치합니다.

```bash
npm ci
```

개발 서버를 실행합니다.

```bash
npm start
```

Android 실행 명령은 다음과 같습니다.

```bash
npm run android
```

그 밖의 실행 스크립트는 다음과 같습니다.

| 명령어 | 설명 |
| --- | --- |
| `npm start` | Expo 개발 서버 실행 |
| `npm run android` | Android에서 앱 열기 |
| `npm run ios` | iOS에서 앱 열기 |
| `npm run web` | Web 개발 서버 실행 |
| `npm run lint` | ESLint 검사 |

도담은 Android 환경을 우선하여 개발하고 검증했습니다. 플랫폼에 따라 Google 로그인, 알림과 일부 네이티브 기능의 동작이 다를 수 있습니다.

> `npm run android`는 Expo Go로 앱을 실행합니다. Google 로그인처럼 네이티브 모듈이 필요한 기능은 Expo Go에서 제한될 수 있으므로 전체 기능 검증에는 `preview` APK를 사용합니다.

### 로그인

개발 실행(`npm start`)에서는 로그인 화면 하단에 개발용 로그인이 항상 표시됩니다. `preview` APK에서는 `EXPO_PUBLIC_ENABLE_DEMO_LOGIN=true`로 빌드된 경우에만 개발용 로그인이 표시되며, `production` 프로필에서는 숨깁니다.

Google 로그인에는 `EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID`와 Google Cloud Console의 Android OAuth 클라이언트 등록이 필요합니다. Android OAuth 클라이언트에는 앱의 패키지명과 빌드에 사용하는 키스토어의 SHA-1을 등록해야 합니다.

네이버 로그인은 현재 준비 중 안내만 표시하며, 카카오 소셜 로그인은 제공하지 않습니다. 시연 계정 정보는 보안상 저장소에 포함하지 않고 팀 내부 채널에서 공유합니다.

## APK 빌드

내부 QA와 시연용 APK는 `preview` 프로필로 생성합니다.

```bash
npx eas-cli@latest build \
  --platform android \
  --profile preview \
  --message "최종 QA APK"
```

EAS 빌드 프로필은 다음과 같이 구분합니다.

| 프로필 | 용도 | 결과물 | 시연용 로그인 |
| --- | --- | --- | --- |
| `development` | 개발용 내부 배포 설정 | 별도 `expo-dev-client` 구성 필요 | 표시 |
| `preview` | QA·시연용 내부 배포 | APK | 표시 |
| `production` | 최종 배포용 빌드 | APK | 숨김 |

빌드가 완료되면 터미널의 Build details 링크 또는 Expo Dashboard의 프로젝트 `Builds` 메뉴에서 APK를 설치하거나 내려받을 수 있습니다.

## 트러블슈팅

### 릴리스 빌드에서 `splashscreen_logo not found`가 발생하는 경우

`expo-splash-screen`에 유효한 Android drawable이 없으면 릴리스 빌드에서 해당 오류가 발생할 수 있습니다.

도담은 GIF가 나오기 전에 정적 로고가 노출되지 않도록 `assets/splash/transparent_splash.xml`을 `app.json`의 `android.drawable.icon`으로 지정합니다. 스플래시 설정을 변경할 때는 다음 설정과 XML 파일을 함께 유지합니다.

```json
{
  "plugins": [
    [
      "expo-splash-screen",
      {
        "backgroundColor": "#FFFFFF",
        "android": {
          "drawable": {
            "icon": "./assets/splash/transparent_splash.xml"
          }
        }
      }
    ]
  ]
}
```

### Android Emulator가 `offline`으로 표시되는 경우

Android Studio의 Device Manager에서 해당 Emulator를 Cold Boot한 후 다시 실행합니다. 계속 연결되지 않으면 Emulator를 종료하고 ADB와 Android Studio를 차례로 다시 실행합니다.

### Android에서 키보드가 입력창을 가리는 경우

edge-to-edge 환경에서는 `adjustResize`만으로 화면 높이가 줄지 않을 수 있습니다. 입력 화면을 `KeyboardAvoidingView`로 감싸고 Android의 `behavior`를 `height`로 설정합니다.

```tsx
<KeyboardAvoidingView
  behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
  style={styles.keyboardArea}
>
  {children}
</KeyboardAvoidingView>
```

## 코드 품질 검사

변경 사항을 올리기 전에 다음 검사를 실행합니다.

```bash
npm run lint -- --no-cache
npx tsc --noEmit
```

현재 별도의 자동화 테스트 스크립트는 없습니다. 대신 ESLint와 TypeScript 정적 검사, Android 실기기·Emulator 수동 시나리오 검증, `qa_report`의 재현 기록으로 주요 사용자 흐름을 확인합니다.

### Android 확인 항목

- Safe Area와 Status Bar
- 키보드 노출 시 입력창과 화면 크기
- 스크롤, 새로고침과 날짜 이동
- 연속 터치에 의한 중복 요청
- 로딩·빈 결과·오류·재시도 상태
- 데이터 변경 후 최신 상태 재조회
- 작은 화면에서 텍스트와 카드 잘림
- 계좌 및 금융정보 마스킹

## 디자인 원칙

- 공통 색상, 타이포그래피, 간격과 그림자는 `src/styles`의 디자인 토큰을 사용합니다.
- 도담의 기본 글꼴은 Pretendard입니다.
- 핵심 금액과 결론을 먼저 보여 주고 부가 설명은 짧게 배치합니다.
- 데이터가 없는 상태와 API 오류를 구분하여 표시합니다.
- 서버에 없는 점수나 절감 효과를 프론트에서 임의로 만들지 않습니다.

## 보안 원칙

- 실제 `.env`, 인증서, 비밀번호, API Secret과 사용자 금융 데이터는 Git에 올리지 않습니다.
- 로그인 토큰은 코드나 로그에 출력하지 않고 `SecureStore`에 저장합니다.
- 화면에는 서버가 제공한 마스킹 계좌번호만 표시합니다.
- API 오류 메시지는 사용자에게 필요한 범위로 변환하여 표시합니다.
- 시연용 로그인은 `development`와 `preview` 프로필에서 표시하고 `production` 프로필에서는 숨깁니다.

## 관련 문서

- API 명세: `../docs/API명세서/`
- ERD: `../docs/ERD/`
- Backend 문서: `../backend/docs/`
- QA 기록: `../qa_report/`
