# Day 7 — 실제 진행 기록

계획: `infra/todo/day-07.md`, 설정값 문서: `infra/docs/external-integrations.md`

## 2026-09-10

### 착수 방침 — 인프라가 막고 있는 것부터 치운다
Day 7은 항목 대부분이 **외부 콘솔 등록(사용자 액션)** 이라 인프라가 단독으로 끝낼 수 있는 게 적다.
그래서 순서를 이렇게 잡았다.

1. **선행조건 해소** — FE 앱 식별자 확정 (이게 없으면 어느 콘솔도 등록 못 한다)
2. **실측** — Day 1부터 미확인이던 아웃바운드 연결성
3. **보관 구조 준비** — 키가 오기 전에 받을 자리를 만들어 둔다
4. 키 수령 후 활성화

### 1. FE 앱 식별자 확정 (09:56, `8d1b044`)
FE 현황을 먼저 실측했다 — `app.json`에 `android.package` 없음, `scheme` 기본값, API 주소 설정 없음.
**확정 없이는 Firebase 앱 등록도 OAuth 클라이언트도 만들 수 없다.** FE팀에 요청해 회신값을 받았다.

| 항목 | 값 |
| --- | --- |
| Android 패키지명 | `com.ssafy.d105.dodam` |
| 앱 scheme | `dodam` |
| API BaseURL | `EXPO_PUBLIC_API_BASE_URL=https://j15D105.p.ssafy.io/api` |

**패키지명은 사실상 되돌릴 수 없다** — Firebase 앱과 OAuth 클라이언트가 이 값에 묶이고, 스토어 배포 후에는 변경 불가다. 그래서 문서 맨 앞에 경고로 올렸다.

이 과정에서 **소셜 로그인 Redirect URI가 웹 방식이 아니라는 점**을 확인했다. 네이티브 앱은 로그인 후 돌아올 곳이 URL이 아니므로 커스텀 스킴 `dodam://oauth/kakao`를 등록해야 한다. 계획서에 `frontend://`로 적혀 있던 예시값을 실제 `scheme`에 맞춰 교정했다.

또 하나 걸러낸 함정: **Android 유형 OAuth 클라이언트에는 Client Secret이 없다.** 패키지명 + SHA-1로 앱을 식별하기 때문이다. 백엔드가 secret을 요구하는 흐름이면 웹 클라이언트를 따로 만들어야 하므로 문서에 남겼다.

### 2. 아웃바운드 연결성 실측 — Day 1부터의 미확인 항목 해소
막혀 있는지 **추측하지 않고 양쪽에서 직접 쳐봤다.**

| 경로 | 결과 |
| --- | --- |
| EC2 호스트 → `finopenapi.ssafy.io` | HTTP 200 |
| backend 컨테이너 → `finopenapi.ssafy.io` | HTTP 200 |
| backend 컨테이너 → `api.openai.com` | HTTP 401 (인증 없는 호출이므로 정상 = 네트워크 열림) |

→ **네트워크 차단 없음. IP 화이트리스트 등록 없이 호출이 나간다.**
다만 금융망이 **인증 단계에서** IP를 볼 가능성은 남아 있으므로, 공인 IP `43.203.198.18`을 문서에 기록해 두었다. 실제 호출이 인증 오류로 막히면 이 값을 등록 요청하면 된다.

### 3. FCM 키 보관 구조 — 마운트를 일부러 주석으로 뒀다
`infra/secrets/` 생성, 발급·배치 절차 README 작성, `.gitignore`에 차단 추가.
차단은 말로 끝내지 않고 `git check-ignore`로 실제 차단됨을 확인했다.

**compose의 마운트 설정은 주석 상태로 준비했다.** 이유:
키 파일이 없는 경로에 bind mount를 걸면 **Docker가 그 자리에 디렉터리를 만들어버린다.** 나중에 파일을 넣어도 꼬여서, 원인을 모르면 한참 헤맨다. 그래서 "키 수령 후 해제"를 주석에 사유까지 적어 남겼다.

### 4. EAS Build 확정 및 SHA-1 조회 절차 문서화 (10:43, `a6fe573`)
FE팀과 협의해 APK 빌드를 **EAS Build(클라우드)** 로 확정했다.

**판단 근거는 keystore 분실 위험이다.** 로컬 빌드면 릴리즈 keystore를 잃는 순간 앱을 **영구히 업데이트할 수 없다** — 스토어가 서명 키로 앱을 식별하므로 새 키는 "다른 앱"이 된다. EAS는 Expo가 클라우드에서 keystore를 관리하고, 팀원 누구나 같은 키로 빌드한다.

인프라는 EC2와 무관하니 개입할 게 없지만, **소셜 로그인 등록에 필요한 SHA-1이 이 빌드 keystore에서 나온다.** 그래서 조회 절차를 인프라 문서에 함께 남겼다.
- `eas credentials -p android` → 프로파일 선택 → SHA-1 / SHA-256
- 카카오 키 해시는 SHA-1을 Base64로 변환한 값

문서에 박아둔 두 가지 함정:
1. **`buildType: "apk"`를 명시해야 한다** — production 프로파일 기본값은 **AAB**(스토어 업로드용)라서 그대로 두면 팀원에게 바로 설치시킬 APK가 안 나온다. 우리 목적은 시연·내부 공유다.
2. **debug keystore는 개발자 PC마다 다르다** — 로컬 디버그 빌드로 테스트하는 사람의 SHA-1도 각각 콘솔에 등록해야 한다. 누락하면 *"나는 되는데 쟤는 소셜로그인이 안 된다"* 가 된다.

**시크릿 관점**: 인프라가 보관할 서명 키는 **없다.** 대신 **Expo 계정**이 그 자리를 대신한다 — 계정을 잃으면 같은 키로 빌드할 수 없으므로 팀 공용 계정으로 만들고 접근자를 정하도록 했다.

`eas.json`은 `frontend/` 소유 영역이라 **템플릿만 문서로 제공하고 직접 커밋하지 않았다.** 인프라 파일을 여러 MR이 동시에 고쳐 변경이 유실된 전례(Day 3)를 고려한 판단이다.

### 5. Firebase 프로젝트·Android 앱 등록 (FE, MR !61)
FE가 Firebase 콘솔에서 프로젝트와 Android 앱(`com.ssafy.d105.dodam`)을 등록하고, `google-services.json`을 `app.json`에 연결한 뒤 `.gitignore`로 차단했다. 레포에 키가 섞이지 않는 방식이라 그대로 두었다.

### 6. 서비스 계정 키 수령 → 마운트 활성화 (16:15, `9d34a7c`)
키를 받아 EC2 `infra/secrets/`에 배치하고 compose 마운트와 `FCM_SERVICE_ACCOUNT_PATH`를 해제했다.
컨테이너 경로는 `/run/secrets/fcm-service-account.json`.

**먼저 키 형식을 값 출력 없이 검증했다** — JSON 파싱 성공, `type=service_account`, `project_id` 확인, `private_key` 존재, 필수 필드 누락 없음. 잘못된 파일을 마운트하고 원인을 찾는 것보다 싸다.

**그리고 여기서 이번 Day의 가장 위험한 결함을 만났다.**

```
docker exec backend head -c 25 /run/secrets/fcm-service-account.json
→ Permission denied
```

`scp`로 올린 파일은 `ubuntu`(uid 1000) 소유 `600`이다. backend는 **비루트(uid 10001)** 로 돌기 때문에 **읽지 못한다.**

이 결함이 위험한 이유는 **아무 증상이 없다는 것이다.** 마운트 자체는 성공하고, 컨테이너는 `healthy`로 뜨고, 배포도 통과한다. 겉으로는 전부 정상이다. **푸시 발송 코드가 붙은 뒤 런타임에서야 처음 터진다** — 그때는 원인이 인프라 설정이라는 것조차 떠올리기 어렵다.

```bash
sudo chown 10001:10001 ~/app/infra/secrets/fcm-service-account.json
sudo chmod 600 ~/app/infra/secrets/fcm-service-account.json
```
→ backend 재기동 → `healthy`, 컨테이너 내부에서 **실제 읽힘까지 확인**했다.

**교훈을 일반화해 문서에 남겼다**: 앞으로 **파일 형태 시크릿을 추가할 때마다** 소유권 이전이 필요하다. `infra/secrets/README.md`와 `external-integrations.md` 양쪽에 절차와 확인 방법(`docker exec`으로 실제 읽기)을 적었다. "마운트 성공 ≠ 읽기 가능"이 핵심이다.

검증: compose 문법 통과, `check-env.sh` 통과(필수 변수 15개).

## 2026-09-11

### 7. 문서·체크리스트 상태 동기화
09-10 작업 이후 **문서의 상태 표가 실제와 어긋나 있었다.** `external-integrations.md`의 발급 현황 표는 Firebase 키를 아직 `❌ 미발급`으로 표시하고 있었다 — 이미 발급받아 마운트까지 끝난 항목이다.

Day 3에서 겪은 "파일과 실제 상태가 어긋난 채 방치"와 같은 패턴이라 바로 정리했다.
- 발급 현황 표: Firebase → `✅ 발급·마운트 완료`, 미발급 3건 → **2건(Kakao/Google)**
- `day-07.md` 체크리스트: 완료된 FCM 3건, FE 빌드 4건 반영 + **진행 상태 요약표** 추가
- EAS 설정 현황 재확인 — `frontend/eas.json` 없음, `frontend/android/` 없음(prebuild 전)은 **오늘도 그대로**
- `expo-notifications` 설치 확인 (`~57.0.17`) — 계획서의 "아직 없음"은 해소됨
- `app.json`의 `scheme: "dodam"`, `android.package` 일치 확인

### 8. 커밋 Jira 키 정정 (-473 → -474)
이 브랜치의 커밋이 모두 `-473`으로 들어가 있었다. 브랜치는 `-474`이므로 키를 맞췄다.

**push 여부로 나눠서 처리했다** — 히스토리 재작성 범위를 최소화하는 판단이다.

| 커밋 | 상태 | 처리 |
| --- | --- | --- |
| `8d1b044`, `a6fe573` | 이미 `origin/474`에 push됨 | **`-473` 그대로 유지** (force-push 회피) |
| `9d34a7c` → `5dc9ae6` | 로컬 전용 | `-474`로 정정 |
| `61d2ed8` → `88b7319` | 로컬 전용 | `-474`로 정정 |

이미 push된 2건을 고치려면 공유 원격 브랜치에 force-push가 필요하다. 브랜치를 받아간 팀원이나 열려 있는 MR이 있으면 영향이 가므로 **그대로 두고 Jira에서 티켓을 직접 연결**하는 쪽을 택했다.

정정 방법: `8eb6e1a`로 리셋 후 두 커밋을 cherry-pick하며 메시지만 amend. 원래 author date는 유지된다.
검증: 정정 전 상태를 `backup-before-key-fix` 태그로 보관하고 `git diff --stat backup-before-key-fix HEAD`가 **빈 출력**임을 확인 — 메시지만 바뀌고 내용은 동일하다.

## 현재 외부 연동 상태

| 연동 | 상태 | 남은 것 |
| --- | --- | --- |
| SSAFY 금융망 | ✅ 키 설정 + 아웃바운드 200 | 타임아웃/재시도 정책 담당 협의 |
| OpenAI | ✅ 키 설정 + 연결 확인(401) | 사용량 한도·결제 담당자, 사용량 알림 (**사용자 액션**) |
| FCM | ✅ 프로젝트·앱 등록 + 서버 키 마운트·읽기 확인 | FE 토큰 발급 흐름, 백엔드 `user_device` 저장 API |
| Kakao OAuth | ❌ 미발급 | 콘솔 등록 → 키/Secret 전달 (**사용자 액션**) |
| Google OAuth | ❌ 미발급 | 콘솔 등록 → 값 전달 (**사용자 액션**) |
| FE 빌드 | ✅ EAS 확정 + SHA-1 절차 문서화 | `eas init`/`eas.json`, APK 배포처 (FE팀) |

## 남은 것
- [ ] Kakao Developers 등록 → `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` 전달 (**사용자 액션**)
- [ ] Google Cloud Console OAuth 클라이언트 → `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` 전달 (**사용자 액션**)
      SHA-1은 `eas credentials -p android`로 조회 — EAS 설정이 선행
- [ ] OpenAI 사용량 한도·결제 담당자 확인 (**사용자 액션**)
- [ ] 금융망 호출 타임아웃/재시도 정책을 어디서 관리할지 백엔드팀과 협의
      (`application.yml`에 `connect-timeout: PT3S` / `read-timeout: PT5S`가 이미 있음 — 인프라는 아웃바운드 네트워크만 보장)
- [ ] 브랜치 `S15P21D105-474-exterior-connection` 푸시 및 MR
      (원격에 이미 존재하고 로컬이 **2건 ahead**. 이 세션에서는 자격증명이 없어 푸시 실패 —
      `git push -u origin S15P21D105-474-exterior-connection`을 직접 실행할 것)

> **인프라가 단독으로 끝낼 수 있는 작업은 남아 있지 않다.** OAuth 2건은 콘솔 등록이 선행이고,
> 등록 후 인프라가 할 일은 `.env`에 값을 넣고 backend를 재기동하는 것뿐이다.
> → 다음은 Day 8 (통합 리허설 · 장애 대응 · 문서화). **인프라에 온전히 쓸 수 있는 마지막 날.**
