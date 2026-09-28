# Finance Data 머지 전 재현 테스트

2026-09-09 최초 검증에서 두 문제가 재현됐다. **같은 날 수정 커밋 eecb4a7 재검증은 42개 모두 통과했으며, 아래 두 건의 머지 보류 사유는 해소됐다.** 운영 DB·설정·프로덕션 코드는 변경하지 않았다.

## 수정 커밋 재검증 (최신 결과)

- 대상: `eecb4a73af0f1c96ab3cd129b36a74f026ac6dba`, 동일 Finance Data 원격 브랜치.
- 기존 독립 스레드 재현 테스트 패치를 그대로 적용하고, 신규 `FinanceMigrationFixedReviewTest.java`로 일회용 MySQL 8.0에서 검증했다.
- 실행: `./gradlew.bat test --tests '*FinanceDataApiIntegrationTest' --tests '*FinanceMigrationFixedReviewTest' --tests '*FlywayOutOfOrderTest' --console=plain`
- 결과: FinanceDataApiIntegrationTest 37개, MySQL 검증 3개, FlywayOutOfOrderTest 2개, **총 42개 통과, 실패 0개**.
- 전체 342개 통과는 작성자가 전달한 결과다. 이번 재검증에서는 관련 테스트 42개를 실행했으며 전체 테스트·clean build를 다시 실행하지 않았다.

### MySQL 업그레이드

local/prod 각각 별도 DB에 V4 없이 V13까지 실제 SQL을 적용했다. 체크인된 해당 프로필 YAML의 `spring.flyway.out-of-order` 값을 읽어 Flyway에 전달했다. 두 프로필 모두 true이며, V4 1건 추가 적용·Flyway validate·재실행 0건·기존 검증 데이터 보존·finance_sync 생성·V4 성공 이력 확인이 통과했다.

대조 실험으로 outOfOrder=false에서는 기존 V4 검증 오류가 그대로 발생함을 확인했다. 이번 검증은 Flyway 및 프로필 설정값 검증이며, 운영 환경변수에 의한 덮어쓰기·운영 DB 이력·전체 애플리케이션의 MySQL 기동을 확인한 것은 아니다.

### 만료된 실행의 늦은 완료

실제 스레드 A를 대기시킨 뒤 시작 시각을 11분 전으로 변경하고, 스레드 B를 시작해 A를 만료시켰다. B가 대기하는 동안 A만 완료시킨 결과는 다음과 같다.

```text
REVIEW late A: status=FAILED; B=RUNNING; lastSyncedChanged=false
```

수정된 finish는 시작·만료와 같은 연결 행 잠금을 얻고, 이미 종료된 실행이면 상태·last_synced_at을 변경하지 않는다. 기존 독립 재현 테스트가 수정 없이 통과했다. 동기화 경쟁 테스트의 DB는 기존 H2 환경이며 MySQL 동시성 테스트는 아니다.

### 판단과 정리

지적했던 두 건은 해결됐다. 최신 develop 병합 후 결과와 MR 전체 코드 승인을 대신하는 검증은 아니며, 실제 머지·push·배포는 수행하지 않았다. 테스트 원본은 아래 파일과 `FinanceMigrationFixedReviewTest.java`에 보존한다. 전용 MySQL과 임시 worktree는 검증 후 정리한다.

아래는 최초 실패 기록이다.

## 대상과 환경

- 브랜치: `origin/S15P21D105-498-finance-data-api`
- 커밋: `27ad5dc2530da7d3d3319d60bff08b6e11631632`
- 원래 534 작업 디렉터리를 보존하고 별도 detached worktree에서 테스트했다. 최신 develop과 병합한 결과는 이번 검증 대상이 아니다.
- JDK: Microsoft OpenJDK 21.0.11, 저장소 Gradle Wrapper.
- 동기화: 기존 Spring Boot/H2 통합 테스트 환경, 실제 Service·Repository·TransactionTemplate과 제어 가능한 ACCOUNT 핸들러.
- 마이그레이션: 일회용 로컬 MySQL 8.0 컨테이너, `127.0.0.1:33364`. 테스트 전용 비밀번호이며 실제 자격증명을 사용하지 않았다.

실행 명령(검증 checkout의 backend에서):

```powershell
$env:JAVA_HOME='C:/Users/SSAFY/.jdks/ms-21.0.11'
.\gradlew.bat test --tests '*FinanceDataApiIntegrationTest' --tests '*FinanceMigrationUpgradeReviewTest' --console=plain
```

## 결과

총 37개 중 35개 통과, 2개 실패. 기존 Finance Data 통합 테스트 34개는 모두 통과했다. 신규 동기화 재현 1개 실패, 신규 MySQL 업그레이드 2개 중 기본 설정 실패·outOfOrder 명시 실험 통과다. 전체 테스트·clean build·실제 금융망 호출은 실행하지 않았다.

### 1. V13 적용 후 V4 추가: 실패

1. MR의 V1/V2/V3/V10/V11/V12/V13을 빈 테스트 DB에 적용했다.
2. 현재 버전이 13임을 assertion으로 확인했다.
3. V4를 포함한 전체 마이그레이션을 Flyway 기본 설정으로 적용했다.

결과:

```text
FlywayValidateException: Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 4.
```

별도 테스트 DB에서 같은 준비 과정을 거친 뒤 `outOfOrder(true)`를 명시하면 V4 적용이 성공했다. 이는 해결 방향 확인용 실험이며 저장소나 운영 환경에 설정을 적용한 것이 아니다.

조치: 실제 대상 DB의 적용 이력과 환경 설정을 확인하고, 번호 할당 정책에 맞는 마이그레이션 전략을 정한 후 기존 DB 업그레이드 테스트를 통과시켜야 한다. 단순히 V4를 무시하는 설정은 테이블 생성 문제를 해결하지 않는다. 운영 DB에 V13이 실제 적용됐는지는 이번 검증에서 조회하지 않았다.

### 2. 만료 실행 A의 늦은 완료: 실패

`FinanceSyncService.java:160`의 finish는 실행 ID로 조회한 뒤 현재 상태를 확인하지 않고 complete/fail을 수행한다. 같은 메서드의 markSynced도 만료 여부와 무관하게 호출된다.

재현 순서:

1. 실제 스레드 A에서 ACCOUNT 동기화를 시작하고 핸들러를 latch로 대기시켰다.
2. A의 DB started_at을 11분 전으로 변경했다. 실제로 10분을 기다리는 대신 만료 조건만 만들었다.
3. 실제 스레드 B에서 동기화를 시작했다. A가 FAILED로 만료됐고 B가 핸들러에서 대기 중임을 확인했다.
4. A만 완료시킨 뒤, B를 계속 대기시킨 상태에서 DB를 확인했다.

관측 결과:

```text
REVIEW late A: status=COMPLETED; B=RUNNING; lastSyncedChanged=true
Expired A must remain FAILED: expected FAILED, actual COMPLETED
Expired A must not change last_synced_at: expected null, actual completion timestamp
```

B 행은 RUNNING을 유지했다. 문제는 **A의 만료 이력이 성공으로 덮이고, B 실행 중에도 A가 공통 마지막 동기화 시각을 갱신한다는 것**이다. 마지막에는 B도 해제하고 두 스레드 종료를 기다렸다.

조치: 시작/만료 처리와 완료 처리를 같은 사용자 잠금 규칙으로 직렬화하고, 해당 실행이 아직 RUNNING인 경우에만 마감과 last_synced_at 갱신을 허용해야 한다. 단순 상태 조회 후 갱신만 추가하면 조회와 갱신 사이 경쟁이 남을 수 있다. 조건부 갱신을 택하면 성공 행 수에 따라 후속 갱신을 제한한다. 핸들러 내부 도메인 데이터 쓰기의 만료 차단은 별도 확장 계약 검토 대상이며 이 테스트로 검증한 범위가 아니다.

## 재현 코드

- `stale-sync-regression.patch`: 대상 커밋의 기존 FinanceDataApiIntegrationTest에 추가한 회귀 테스트. 검증 checkout 루트에서 `git apply`로 적용한다.
- `FinanceMigrationUpgradeReviewTest.java`: 검증 checkout의 `backend/src/test/java/com/ssafy/dodam/domain/finance/`에 복사한다.
- 마이그레이션 테스트는 전용 MySQL 33364 포트와 신규 `review_default`, `review_out_of_order` DB를 사용한다. 재실행은 새 일회용 컨테이너로 수행한다. 기존 서비스 DB에는 실행하지 않는다.

테스트는 문제를 감지하기 위해 의도적으로 정상 기대값을 assertion하므로 현재 대상 커밋에서는 실패한다. 테스트를 통과시키기 위해 기대값을 잘못된 동작으로 변경하지 않는다. 이번 작업에는 프로덕션 수정·커밋·push·머지가 포함되지 않는다.
