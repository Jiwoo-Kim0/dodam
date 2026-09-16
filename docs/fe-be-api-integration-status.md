# 프론트엔드–백엔드 API 연동 호환 현황

프론트엔드(`frontend/src/api/*`)가 호출하는 엔드포인트와 백엔드(`backend/.../controller/*`)가 실제로 제공하는 엔드포인트를 1:1로 대조한 결과다. 명세서가 아니라 **현재 코드 기준**이며, 두 영역이 어긋난 곳과 아직 연결되지 않은 곳을 한 번에 보기 위한 문서다.

- 기준 커밋: `develop` @ `684eb02` (2026-09-16, `origin/develop`과 동일). 2026-09-16 저녁 기준 **병합 대기 MR 8건**(§8)의 변경은 해당 행에 `(MR !nnn)`로 표시했다 — 병합 전에는 develop 코드와 다르다.
- 대조 범위: FE `src/api` 13개 모듈의 호출 84건, BE 컨트롤러 36개의 매핑 215건(!152 병합 후 216건).
- 판정 기준: 경로·HTTP 메서드·쿼리/바디 파라미터 이름·응답 DTO 필드명과 null 가능성·enum 값을 코드에서 직접 비교했다. 실제 요청을 보내 검증한 것은 아니다.

## 1. 요약

| 구분 | 건수 |
| --- | ---: |
| BE 제공 엔드포인트 | 215 (!152 병합 후 216) |
| FE가 호출하는 엔드포인트 | 84 |
| 그중 경로·메서드·파라미터·응답 필드가 모두 일치 | 84 |
| 불일치(호출이 실패하거나 필드가 깨지는 경우) | 0 |
| 참고 사항(동작에는 문제 없으나 FE 타입이 BE보다 좁거나 넓은 경우) | 6 (§4) |
| BE에만 있고 FE가 아직 호출하지 않는 엔드포인트 | 131 (!152 병합 후 132) (§5) |
| FE에서 mock 데이터로 그리는 화면 | 업무 데이터 기준 0건, 표시용 상수 2건 (§6) |

## 2. 공통 계약 호환성

| 항목 | FE | BE | 상태 |
| --- | --- | --- | --- |
| Base URL | `EXPO_PUBLIC_API_BASE_URL`이 `/api`로 끝나야 하며 코드는 `/v1/...`만 붙인다 (`apiClient.buildUrl`) | 모든 컨트롤러가 `/api/v1/...`. Nginx `location /api/` → backend | 일치 |
| 응답 봉투 | `ApiResponse<T> { success, data, message, code?, errors? }`. `success && ok`면 `data`만 반환 | `global/common/ApiResponse` 동일 구조 | 일치 |
| 페이지 응답 | `PageResponse<T> { content, page, size, totalElements, totalPages, last }`, 최대 size 100 | `PageResponse` 동일, `size` `@Max(100)` | 일치 |
| 오류 처리 | `ApiError(status, code, message, errors[])`. `code`로만 분기 | `ErrorCode` + 전역 예외 처리. `errors[] {field, reason}` | 일치 |
| 인증 헤더 | `Authorization: Bearer <accessToken>` | JWT 필터 | 일치 |
| 인증 제외 경로 | `auth: false`로 보내는 곳: login, refresh, social | `permitAll`: `POST signup, login, social/*, refresh`, `GET /api/health` | 일치 (FE는 signup 미구현) |
| 401 처리 | 첫 401 → `POST /v1/auth/refresh` 후 1회 재시도, 다시 401이면 세션 종료 | refresh 토큰 회전 | 일치 |
| 201 분기 | `postWithStatus`로 소셜 로그인만 상태 코드 확인 | `social/{provider}`: 기존 회원 200, 신규 201 | 일치 |
| 날짜·시간 | 모두 `string` | `LocalDate`→`yyyy-MM-dd`, `LocalDateTime`→ISO-8601(타임존 없음), `LocalTime`→`HH:mm:ss` (`dailyBriefingTime`만 `HH:mm`) | 일치 |
| 금액 | `number` | 원 단위 `long`/`Long`, 비율 `BigDecimal`/`double` | 일치 (JS 안전 정수 범위 내) |
| 빈 바디 응답 | `apiClient.delete<void>`/`post<null>` | `ApiResponse<Void>` (`data: null`) | 일치 |

## 3. 엔드포인트 1:1 매칭표

상태 표기: **일치** = 경로·메서드·파라미터·응답 필드 모두 일치. **일치(참고)** = 호출·파싱에는 문제 없으나 §4에 적어 둔 차이가 있음.

### 3.1 인증 (`authApi`, `socialAuthApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `loginWithPassword` | `POST /v1/auth/login` | `AuthController.login` | `{email, password}` = `LoginRequest` | `AuthResponse` = `LoginResponse{tokenType, accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt, user{userId,email,name,role}}` | 일치 |
| `refreshAuthTokens` | `POST /v1/auth/refresh` | `AuthController.refresh` | `{refreshToken}` = `RefreshRequest` | `LoginResponse` | 일치 |
| `logout` | `POST /v1/auth/logout` | `AuthController.logout` | 없음 | `Void` | 일치 |
| `getSession` | `GET /v1/auth/session` | `AuthController.session` | 없음 | `AuthSession` = `SessionResponse{user, sessionId, sessionCreatedAt, lastAccessedAt, sessionExpiresAt}` | 일치 |
| `deleteSessions` | `DELETE /v1/auth/session?sessionId=` | `AuthController.deleteSession` | `sessionId?` (Long) | `SessionDeleteResponse{revokedCount}` | 일치 |
| `loginWithSocialToken` | `POST /v1/auth/social/{provider}` | `AuthController.socialLogin` | `provider ∈ kakao,google`, `{token}` = `SocialLoginRequest` | `LoginResponse`, 200/201 | 일치 |

### 3.2 사용자 (`userApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getMe` | `GET /v1/users/me` | `UserController.getMe` | 없음 | `UserProfile` = `UserResponse{userId,email,name,role,financeConnectionStatus,createdAt,updatedAt}` | 일치 |
| `updateMe` | `PATCH /v1/users/me` | `UserController.updateMe` | `{name?}` = `UpdateUserRequest` | `UserResponse` | 일치 |
| `getSettings` | `GET /v1/users/me/settings` | `UserController.getSettings` | 없음 | `UserSettings` = `UserSettingResponse{notificationEnabled,dailyBriefingEnabled,monthlyReportEnabled,dailyBriefingTime(HH:mm),timezone,updatedAt}` | 일치 |
| `updateSettings` | `PATCH /v1/users/me/settings` | `UserController.updateSettings` | 5개 선택 필드 = `UpdateUserSettingRequest` | `UserSettingResponse` | 일치 |
| `getFinancialProfile` | `GET /v1/users/me/financial-profile` | `UserController.getFinancialProfile` | 없음 | `FinancialProfile` = `FinancialProfileResponse{monthlyIncome,annualIncome,payday,fixedExpense,emergencyFundTarget,updatedAt}` (모두 nullable) | 일치 |
| `updateFinancialProfile` | `PATCH /v1/users/me/financial-profile` | `UserController.updateFinancialProfile` | 5개 선택 필드 = `UpdateFinancialProfileRequest` | `FinancialProfileResponse` | 일치 |

### 3.3 금융망 연동 (`financeDataApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getConnection` | `GET /v1/finance-data/connection` | `FinanceDataController.getConnection` | 없음 | `FinanceConnection` = `FinanceConnectionResponse{status,memberEmailMasked,connectedAt,disconnectedAt,lastSyncedAt}` | 일치 |
| `connect` | `POST /v1/finance-data/connection` | `FinanceDataController.connect` (201) | 없음 | `FinanceConnectionResponse` | 일치 |
| `disconnect` | `DELETE /v1/finance-data/connection` | `FinanceDataController.disconnect` | 없음 | `FinanceConnectionResponse` | 일치 |
| `sync` | `POST /v1/finance-data/sync` | `FinanceDataController.sync` (201) | `{scope}` = `FinanceSyncRequest(String scope)` | `FinanceSync` = `FinanceSyncResponse{syncId,scope,status,progress,errorMessage,startedAt,completedAt,results[]{scope,status,syncedCount,message}}` | 일치 |
| `getSyncStatus` | `GET /v1/finance-data/sync/status` | `FinanceDataController.getSyncStatus` | 없음 | `FinanceSyncStatusResult` = `FinanceSyncStatusResponse{inProgress, sync}` | 일치 |
| `getLatestSync` | `GET /v1/finance-data/sync/latest` | `FinanceDataController.getLatestSync` | 없음 | `FinanceSyncLatest` = `FinanceSyncLatestResponse{lastSyncedAt, lastCompletedSync}` | 일치 |

FE `financeErrorCodes` 7개(`FINANCE_CONNECTION_NOT_FOUND`, `FINANCE_ALREADY_CONNECTED`, `FINANCE_NOT_CONNECTED`, `FINANCE_EMAIL_ALREADY_REGISTERED`, `FINANCE_SYNC_IN_PROGRESS`, `FINANCE_API_ERROR`, `FINANCE_API_UNAVAILABLE`)는 `backend/docs/finance-data.md`의 코드와 같다.

### 3.4 자산 (`assetApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getAccounts` | `GET /v1/accounts` | `AccountController.getAccounts` | FE는 필터 없이 호출 (BE `accountType?`, `status?`) | `AccountApiResponse[]` = `AccountResponse` 13필드 | 일치 |
| `getAccountSummary` | `GET /v1/accounts/summary` | `AccountAnalysisController.getSummary` | 없음 | `AccountSummaryResponse` 14필드 | 일치 |
| `getAccountBalanceSummary` | `GET /v1/accounts/balance-summary` | `AccountAnalysisController.getBalanceSummary` | 없음 | `{totalCash, accountCount, accounts[]{accountId,bankName,accountNoMasked,accountName,status,balance,sharePercent}}` | 일치 |
| `getInactiveAccounts` | `GET /v1/accounts/inactive` | `AccountAnalysisController.getInactiveAccounts` | FE는 기본값 사용 (BE `inactiveMonths?` 1~120, `maxBalance?`) | `{criteria{inactiveMonths,maxBalance,asOfDate}, count, totalBalance, accounts[]}` | 일치 |
| `getCards` | `GET /v1/cards` | `CardQueryController.getCards` | FE는 필터 없이 호출 | `CardApiResponse[]` = `CardResponse` 10필드 + `withdrawalAccount{bankCode,bankName,accountName,accountNoMasked,accountType}`. MR !150 병합 후 `cardId` 추가 | 일치(참고 §4-6) |
| `getCardPaymentSchedules` | `GET /v1/cards/payment-schedules` | `CardQueryController.getPaymentSchedules` | `startDate?`, `endDate?` (ISO date) | `{startDate,endDate,totalScheduledAmount,schedules[]{cardNoMasked,cardName,billingMonth,billingDate,scheduledAmount,status}}` | 일치 |
| `getLoans` | `GET /v1/loans` | `LoanQueryController.getLoans` | FE는 필터 없이 호출 | `LoanApiResponse[]` = `LoanResponse` 13필드. MR !155 병합 후 `loanId` 추가 | 일치(참고 §4-6) |
| `getLoanSummary` | `GET /v1/loans/summary` | `LoanQueryController.getSummary` | 없음 | `{loanCount,totalOriginalPrincipal,totalBalance,monthlyRepayment,weightedAverageRate,nextRepaymentDate,loanTypes[]{loanType,loanCount,balance}}` | 일치 |
| `getDepositSavings` | `GET /v1/deposit-savings` | `DepositSavingQueryController.list` | `type? ∈ DEPOSIT,SAVINGS`, `status? ∈ ACTIVE,INACTIVE,CLOSED`, `page`, `size`(FE 기본 100) | `PageResponse<DepositSavingResponse>` 12필드 | 일치 |
| `getDepositSavingSummary` | `GET /v1/deposit-savings/summary` | `DepositSavingQueryController.summary` | 없음 | `{totalCount,totalBalance,depositCount,depositBalance,savingsCount,savingsBalance,reason}` | 일치 |
| `getInstallments` | `GET /v1/installments` | `InstallmentQueryController.getInstallments` | `cardId?`, `status? ∈ ACTIVE,COMPLETED,CANCELED,OVERDUE`, `page`, `size`(FE 기본 100). BE는 빈 문자열 값을 400으로 거부하지만 FE `appendQuery`는 `undefined`를 생략하므로 해당 없음 | `PageResponse<InstallmentResponse>` 10필드 | 일치 |
| `getInstallmentSchedule` | `GET /v1/installments/monthly-schedule` | `InstallmentQueryController.getSchedule` | `startMonth?`, `endMonth?` | `{asOfDate,startMonth,endMonth,complete,excludedInstallmentCount,exclusions[]{reason,count},months[]{yearMonth,scheduledAmount,installmentCount}}` | 일치 |

### 3.5 가계부·소비 분석 (`ledgerApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getTransactions` | `GET /v1/transactions` | `TransactionController.getTransactions` | `startDate?`, `endDate?`, `category?`, `paymentMethod?`, `hidden?`, `page`, `size`(FE 기본 100) | `PageResponse<TransactionResponse>` 20필드 | 일치 |
| `getTransactionCategories` | `GET /v1/transactions/categories` | `TransactionController.getCategories` | 없음 | `TransactionCategoryResponse[]{categoryId,code,name,defaultSpendingType}` | 일치 |
| `getTransaction` | `GET /v1/transactions/{id}` | `TransactionController.getTransaction` | path `transactionId`(string) | `TransactionResponse` | 일치 |
| `updateTransactionCategory` | `PATCH /v1/transactions/{id}/category` | `TransactionController.updateCategory` | `{categoryId}` | `TransactionResponse` | 일치 |
| `updateTransactionMemo` | `PATCH /v1/transactions/{id}/memo` | `TransactionController.updateMemo` | `{memo}` | `TransactionResponse` | 일치 |
| `updateTransactionVisibility` | `PATCH /v1/transactions/{id}/visibility` | `TransactionController.updateVisibility` | `{hidden}` | `TransactionResponse` | 일치 |
| `createManualTransaction` | `POST /v1/transactions` | `TransactionController.createManual` | `CreateManualTransactionRequest` 8필드 (필수: transactionDate, transactionType, amount) | `TransactionResponse` | 일치 |
| `updateManualTransaction` | `PUT /v1/transactions/{id}` | `TransactionController.updateManual` | `Partial<CreateManualTransactionRequest>` = `UpdateManualTransactionRequest` | `TransactionResponse` | 일치 |
| `deleteManualTransaction` | `DELETE /v1/transactions/{id}` | `TransactionController.deleteManual` | path | `Void` | 일치 |
| `getMonthlySummary` | `GET /v1/spending/monthly-summary` | `SpendingController.getMonthlySummary` | `yearMonth?` | `SpendingMonthlySummaryResponse` 10필드 | 일치 |
| `getDailySpending` | `GET /v1/spending/daily` | `SpendingController.getDaily` | `startDate?`, `endDate?` | `{startDate,endDate,totalSpending,transactionCount,dailyAverage,days[]{date,totalSpending,transactionCount}}` | 일치 |
| `getCategorySpending` | `GET /v1/spending/categories` | `SpendingController.getByCategory` | `yearMonth?` | `{yearMonth,totalSpending,transactionCount,categories[]{categoryId,code,name,totalSpending,transactionCount,sharePercent}}` | 일치 |
| `getCategoryTrend` | `GET /v1/spending/categories/trend` | `SpendingController.getCategoryTrend` | `startMonth`, `endMonth` (BE는 `category?`도 받음, FE 미사용) | `{startMonth,endMonth,category,totalSpending,monthlyAverage,months[]{yearMonth,totalSpending,transactionCount,categories[]}}` | 일치 |
| `getSavingRatio` | `GET /v1/spending/saving-ratio` | `SpendingController.getSavingRatio` | `yearMonth` | `SavingRatioResponse` 11필드 | 일치 |
| `getFixedSpending` | `GET /v1/spending/fixed` | `SpendingController.getFixed` | `startMonth`, `endMonth` | `SpendingTypeBreakdownResponse` 11필드 | 일치 |
| `getVariableSpending` | `GET /v1/spending/variable` | `SpendingController.getVariable` | `startMonth`, `endMonth` | `SpendingTypeBreakdownResponse` | 일치 |
| `getPaymentMethodSpending` | `GET /v1/spending/payment-methods` | `SpendingController.getByPaymentMethod` | `yearMonth` | `{yearMonth,totalSpending,transactionCount,paymentMethods[]{paymentMethod,totalSpending,transactionCount,sharePercent}}` | 일치 |
| `getSpendingAnomalies` | `GET /v1/spending/anomalies` | `SpendingController.getAnomalies` | `startMonth`, `endMonth` (BE `category?` FE 미사용) | `SpendingAnomalyResponse{recentPeriod,baselinePeriod,thresholdRatePercent,minimumIncrease,category,evaluatedCategories,anomalies[]}` | 일치 |

### 3.6 현금흐름 (`cashflowApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getEvents` | `GET /v1/cashflow/events` | `CashflowQueryController.getEvents` | `startDate?`, `endDate?` (BE `type?` FE 미사용) | `{forecast{12필드}, startDate, endDate, type, events[]{eventId,eventDate,type,name,direction,amount,sourceType,sourceId}}` | 일치 |
| `getRisks` | `GET /v1/cashflow/risks` | `CashflowQueryController.getRisks` | 없음 (BE `type?`, `severity?` FE 미사용) | `{forecast, type, severity, risks[]{riskId,riskDate,type,severity,message,expectedBalance}}` | 일치 |

### 3.7 목표 (`goalApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getGoals` | `GET /v1/goals` | `GoalController.getGoals` | `status?`, `type?`, `page`, `size` | `PageResponse<GoalResponse>` 13필드 | 일치 |
| `createGoal` | `POST /v1/goals` | `GoalController.create` (201) | `CreateSavingGoalRequest{name,type:'SAVING',targetAmount,targetDate}` / `CreateSpendingGoalRequest{name,type:'SPENDING',monthlyBudget}` ⊂ `CreateGoalRequest{name,type,targetAmount?,targetDate?,monthlyBudget?}` | `GoalResponse` | 일치 |
| `getGoal` | `GET /v1/goals/{goalId}` | `GoalController.getGoal` | path | `GoalResponse` | 일치 |
| `updateGoal` | `PATCH /v1/goals/{goalId}` | `GoalController.update` | `{name?,targetAmount?,targetDate?,monthlyBudget?,status?}` = `UpdateGoalRequest` | `GoalResponse` | 일치 |
| `deleteGoal` | `DELETE /v1/goals/{goalId}` | `GoalController.delete` | path | `Void` | 일치 |
| `getGoalAccounts` | `GET /v1/goals/{goalId}/accounts` | `GoalController.getAccounts` | `page`, `size` | `GoalAccountsResponse{goalId,linkedCount,totalBalance,accounts[],page,size,totalPages,last}` | 일치(참고 §4-4) |
| `replaceGoalAccounts` | `PUT /v1/goals/{goalId}/accounts` | `GoalController.replaceAccounts` | `{accountIds: number[]}` = `UpdateGoalAccountsRequest` | `GoalAccountsResponse` | 일치(참고 §4-4) |
| `getProgress` | `GET /v1/goals/{goalId}/progress` | `GoalController.getProgress` | path | `GoalProgressResponse` 16필드 (`paceStatus ∈ AHEAD,ON_TRACK,BEHIND,ACHIEVED`) | 일치 |
| `getRequiredSaving` | `GET /v1/goals/{goalId}/required-saving` | `GoalController.getRequiredSaving` | path | `RequiredSavingResponse` 15필드 (`feasibility ∈ ACHIEVED,SUFFICIENT,INSUFFICIENT,DEADLINE_PASSED`) | 일치 |
| `getForecast` | `GET /v1/goals/{goalId}/forecast` | `GoalController.getForecast` | `baseDate?`(ISO date), `months?`(1~120) | `GoalForecastResponse{…, projection[]{yearMonth,projectedAmount,achieved}}` | 일치 |
| `getSpendingProgress` | `GET /v1/goals/{goalId}/spending-progress` | `GoalController.getSpendingProgress` | path | `SpendingGoalProgressResponse` 16필드 | 일치 |
| `getRisk` | `GET /v1/goals/{goalId}/risk` | `GoalController.getRisk` | path | `GoalRiskResponse{goalId,type,goalStatus,riskLevel,factors[],metrics{8필드 nullable}}` | 일치 |
| `getHistory` | `GET /v1/goals/{goalId}/history` | `GoalController.getHistory` | `startDate?`, `endDate?`, `page`, `size`(FE 기본 100) | `PageResponse<GoalHistoryResponse{historyId,recordedDate,currentAmount,progressRatePercent,forecastDate,event,createdAt}>` | 일치 |

### 3.8 시뮬레이션 (`simulationApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `runSpendingSimulation` | `POST /v1/simulations/spending` | `SimulationController.spending` (200 재사용/201 신규) | `{category?, monthlyReduction, durationMonths?}` = `SpendingSimulationRequest` | `SimulationRunResponse{simulation, created}` | 일치 |
| `runSavingSimulation` | `POST /v1/simulations/saving` | `SimulationController.saving` | `{accountNo?, monthlySavingAmount, durationMonths?}` ⊂ `SavingSimulationRequest` | `SimulationRunResponse` | 일치(참고 §4-1). 루트 명세 17 반영은 MR !153 |
| `getSimulations` | `GET /v1/simulations` | `SimulationController.list` | `type?`, `page`, `size` | `PageResponse<SimulationSummaryResponse>` 7필드 | 일치 |
| `getSimulation` | `GET /v1/simulations/{id}` | `SimulationController.detail` | path | `SimulationResponse` (`request/before/after/comparison/cashflow`는 BE `JsonNode`, FE는 `backend/docs/simulation.md` §9 구조로 타입 정의) | 일치(참고 §4-2) |
| `getSimulationComparison` | `GET /v1/simulations/{id}/comparison` | `SimulationController.comparison` | path | `{items[], assets[], debts[], costs[]}` 각 `{key,label,before,after,delta}` | 일치 |

### 3.9 진단·추천 (`diagnosisApi`, `recommendationApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `diagnosisApi.run` | `POST /v1/diagnoses` | `DiagnosisController.run` (200 캐시/201 신규) | `{forceRefresh}` ⊂ `Request{goalId?, forceRefresh?}` | `DiagnosisRunApiResponse{diagnosis, cached}` = `DiagnosisRunner.Result` | 일치 |
| `diagnosisApi.getLatest` | `GET /v1/diagnoses/latest` | `DiagnosisController.latest` | 없음 (BE `goalId?` FE 미사용) | `DiagnosisApiResponse{diagnosisId,goalId,issueCount,completedAt}` ⊂ `DiagnosisResponse` 10필드 | 일치(참고 §4-5) |
| `diagnosisApi.getIssues` | `GET /v1/diagnoses/{id}/issues` | `DiagnosisController.issues` | `page`, `size`(FE 고정 100) (BE `domain?`, `type?`, `severity?` FE 미사용) | `PageResponse<DiagnosisIssueApiResponse{issueId,diagnosisId,domain,type,severity,title,description}>` ⊂ `DiagnosisIssueResponse` 14필드 | 일치(참고 §4-5) |
| `diagnosisApi.getIssue` | `GET /v1/diagnoses/{id}/issues/{issueId}` | `DiagnosisController.issue` | path | `DiagnosisIssueResponse` | 일치(참고 §4-5) |
| `recommendationApi.generate` | `POST /v1/recommendations/generate` | `RecommendationController.generate` | `{diagnosisId}` ⊂ `GenerateRecommendationRequest{diagnosisId, goalId?}` | `RecommendationGenerationApiResponse{diagnosisId,created,reused}` ⊂ `RecommendationGenerationResponse{…, goalId, items[], skipped[]}` | 일치(참고 §4-5) |
| `recommendationApi.getRecommendations` | `GET /v1/recommendations` | `RecommendationController.list` | `page`, `size`(FE 고정 100) | `PageResponse<RecommendationApiResponse>` | 일치(참고 §4-5) |
| `recommendationApi.getRecommendation` | `GET /v1/recommendations/{id}` | `RecommendationController.detail` | path | `RecommendationApiResponse{recommendationId,diagnosisId,sourceIssueId,domain,type,title,description,priority,status,effect,simulation?}` ⊂ `RecommendationResponse`(+`goalId`, `createdAt`, `updatedAt`); `effect{available,monthlyCostSaving,annualCostSaving,interestSaving,advancedDays}` ⊂ `RecommendationEffectResponse` 11필드; `simulation` = `RecommendationSimulationPreset{available,type?,endpoint?,request?,unavailableReason?,assumptions[]}`. MR !155 병합 후 대환 프리셋 `request`에 `loanId`가 실리고 `ACCOUNT_NO_REQUIRED_BY_CLIENT`는 더 나오지 않음 | 일치(참고 §4-5·§4-6) |

### 3.10 AI 코치 (`coachApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getFinancialSummary` | `POST /v1/coach/financial-summary` | `CoachController.financialSummary` | `{diagnosisId?}` = `FinancialSummaryRequest` (BE는 바디 생략도 허용) | `CoachMessageResponse{topic,source,degradedReason,model,generatedAt,subject,message,highlights[],facts{topic,subject,sections[]{title,facts[]{label,value}},notes[]},disclaimer}` | 일치 |
| `getInefficiencyExplanation` | `POST /v1/coach/inefficiency-explanation` | `CoachController.inefficiency` | `{diagnosisId, issueId}` = `InefficiencyExplanationRequest` | `CoachMessageResponse` | 일치 |
| `getRecommendationExplanation` | `POST /v1/coach/recommendation-explanation` | `CoachController.recommendation` | `{recommendationId}` = `RecommendationExplanationRequest` | `CoachMessageResponse` | 일치 |

### 3.11 알림 (`notificationApi`)

| FE 함수 | 요청 | BE 핸들러 | 파라미터 | 응답 | 상태 |
| --- | --- | --- | --- | --- | --- |
| `getNotifications` | `GET /v1/notifications` | `NotificationQueryController.list` | `isRead?`, `page`, `size` (BE `type?`, `fromDate?`, `toDate?` FE 미사용) | `PageResponse<NotificationResponse{notificationId,type,title,message,sourceType,sourceId,isRead,createdAt,readAt}>` | 일치 |
| `getUnreadCount` | `GET /v1/notifications/unread-count` | `NotificationQueryController.unreadCount` | 없음 | `UnreadCountResponse{unreadCount}` | 일치 |
| `getNotification` | `GET /v1/notifications/{id}` | `NotificationQueryController.detail` | path | `NotificationResponse` | 일치 |
| `markRead` | `PATCH /v1/notifications/{id}/read` | `NotificationCommandController.read` | path | `NotificationResponse` | 일치 |
| `markAllRead` | `PATCH /v1/notifications/read-all` | `NotificationCommandController.readAll` | 없음 | `ReadAllResponse{updatedCount, unreadCount}` | 일치 |
| `deleteNotification` | `DELETE /v1/notifications/{id}` | `NotificationCommandController.delete` | path | `Void` | 일치 |

## 4. 참고 사항 (동작은 되지만 알아 둘 차이)

1. **저축 시뮬레이션 `goalId`**: BE `SavingSimulationRequest`는 619 작업으로 `goalId?`를 받고, 저장·응답 `request` 스냅샷에도 `goalId` 키가 들어간다. FE `SavingSimulationRequest`에는 `goalId?`가 반영됐지만 응답용 `SavingSimulationRequestSnapshot`에는 아직 없다. 추가 필드라 파싱에는 문제없다.
2. **시뮬레이션 `goalImpacts[].status/reasons`**: BE는 `@JsonInclude(NON_NULL)`이라 spending/saving 결과에는 키 자체가 없고, 585 계열 유형에서만 나온다. FE 타입은 `status?`, `reasons?`로 선언돼 있어 일치한다. FE가 spending/saving 외 유형을 연결할 때 `SimulationApiResponse`의 `before/after/cashflow` 확장 필드(`details`, `currentMonth`, `truncated` 등)를 추가로 다뤄야 한다.
3. **FE가 보내지 않는 선택 파라미터**: `GET /v1/accounts`(`accountType`, `status`), `GET /v1/cards`, `GET /v1/loans`(각 필터), `GET /v1/accounts/inactive`(`inactiveMonths`, `maxBalance`), `GET /v1/cashflow/events`(`type`), `GET /v1/cashflow/risks`(`type`, `severity`), `GET /v1/spending/categories/trend`·`anomalies`(`category`), `GET /v1/diagnoses/latest`(`goalId`), `GET /v1/diagnoses/{id}/issues`(`domain`, `type`, `severity`), `POST /v1/recommendations/generate`(`goalId`), `GET /v1/notifications`(`type`, `fromDate`, `toDate`). 모두 BE 기본값으로 동작하며 필요 시 FE 인터페이스에 추가하면 된다.
4. **목표 연결 계좌 626 배분 필드**: BE `GoalAccountsResponse.Item`은 `allocatedToThisGoal`, `allocatedElsewhere`, `availableToAllocate`, `shortfallAmount`, `allocationStatus`를 추가로 내려준다. FE `GoalAccountApiResponse`는 `contributionAmount`까지만 선언돼 있어 배분 현황을 화면에 쓰려면 타입을 넓혀야 한다.
5. **FE 타입이 BE 응답의 부분집합인 경우**: 진단(`DiagnosisResponse`의 `createdAt`, `ruleVersion`, `overallStatus`, `highSeverityCount`, `coverage`, `assumptions`), 진단 문제(`currentValue`, `benchmarkValue`, `evidence`, `detectionSource`, `modelVersion`, `clusterContext`, `detectedAt`), 추천(`goalId`, `createdAt`, `updatedAt`, `effect`의 `riskReduction`·`currentForecastDate`·`improvedForecastDate`·`contributionAmount`·`measuredAt`·`assumptions`), 추천 생성(`goalId`, `items[]`, `skipped[]`). FE가 쓰는 필드만 선언한 것이라 동작에는 영향 없다. `RecommendationSimulationApiResponse.unavailableReason`은 FE가 4개 값만 열거했지만 BE 상수는 7개(`REDUCTION_APPLIES_TO_TOTAL_SPENDING`, `TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT`, `ACCOUNT_NO_REQUIRED_BY_CLIENT`는 `assumptions`용)라 문자열로 넓혀 두는 편이 안전하다.

6. **병합 대기 MR로 생기는 FE 타입 갭(§8)**: !150 `CardApiResponse.cardId`, !155 `LoanApiResponse.loanId`·`RecommendationSimulationRequestApiResponse.loanId`, !156 `RecommendationSummary`(FE 미호출)의 `discovery`, !151 적금 예상금액(FE 미호출)의 `tax`·`expectedNetInterest`와 `expectedAmount` 의미 변경(세전→세후), !152 `GET /installments/burden`(FE 미호출), !157 마이너스통장 쓰기 `Idempotency-Key` 헤더(FE 미호출). 모두 추가 필드·추가 헤더라 지금 FE 호출을 깨뜨리지 않는다. FE 타입에 `cardId`·`loanId`를 넣으면 리볼빙·대환 프리셋 실행 경로가 열린다.

FE 타입 union과 BE enum은 다음을 모두 확인했고 값이 같다: `AccountType`, `AccountStatus`, `InstallmentStatus`, `FinanceConnectionStatus`, `FinanceSyncScope/Status`, `SimulationType/Status`, `GoalType/Status`, `RiskSeverity`, `IssueSeverity`, `RecommendationStatus`, `CoachTopic/Source/DegradedReason`, `EventDirection`, `UserRole`, `TransactionType`, `SpendingType`, `TransactionSourceType`(FE는 `SSAFY|MANUAL|SEED`, BE 동일).

## 5. BE에만 있고 FE가 아직 호출하지 않는 엔드포인트 (131, !152 병합 후 132)

FE 화면 연동 시 그대로 쓸 수 있는 API다. 계약은 각 `backend/docs/*.md`를 따른다. 경로는 `/api/v1` 접두어를 생략했다.

| 도메인 | 미연동 엔드포인트 | 관련 문서 |
| --- | --- | --- |
| Auth | `POST /auth/signup` | `auth-social.md` |
| User | `DELETE /users/me` (탈퇴) | — |
| Finance Data | `GET /finance-data/member`, `/summary`, `/credit-rating`, `/credit-rating/criteria` | `finance-data.md` |
| Account | `GET /accounts/{accountNo}`, `/{accountNo}/balance`, `/{accountNo}/transactions`, `/{accountNo}/transactions/{transactionId}`, `/{accountNo}/monthly-summary`, `/{accountNo}/trend` | `account-query.md` |
| Card | `GET /cards/summary`, `/{cardNo}`, `/{cardNo}/transactions`, `/{cardNo}/payment-schedule`, `/{cardNo}/billing-statements`, `/{cardNo}/limit`, `/{cardNo}/monthly-summary`, `/{cardNo}/categories`, `/{cardNo}/benefits`, `/{cardNo}/benefit-usage`, `/payment-burden`, `/low-usage`; `PATCH /cards/{cardNo}/payment-settings` | `card-*.md` |
| Loan | `GET /loans/{accountNo}`, `/{accountNo}/repayments`, `/{accountNo}/repayment-schedule`, `/{accountNo}/interest-forecast`, `/{accountNo}/rate-comparison`, `/monthly-repayments`, `/repayment-burden`, `/interest-summary`, `/cost-comparison`, `/high-interest`, `/repayment-priority` | `loan-*.md` |
| Minus Account | `GET /loans/minus-accounts`, `/{accountNo}`, `/{accountNo}/interest`, `/{accountNo}/transactions`, `/{accountNo}/alerts`; `POST /loans/minus-accounts/{accountNo}/repayments`, `/{accountNo}/interest-payments` | `minus-account.md` |
| Deposit | `GET /deposits/{accountNo}`, `/{accountNo}/early-termination`, `/{accountNo}/maturity-forecast`, `/{accountNo}/rate-comparison` | `deposit-detail.md` |
| Savings | `GET /savings/{accountNo}`, `/{accountNo}/payments`, `/{accountNo}/early-termination`, `/{accountNo}/maturity-forecast`, `/{accountNo}/rate-comparison`, `/payment-schedules`, `/monthly-amount`, `/cashflow-burden`, `/disposable-balance` | `savings.md` |
| Deposit/Savings 통합 | `GET /deposit-savings/maturities`, `/rate` | `deposit-savings.md` |
| Installment | `GET /installments/{installmentId}`, `/forecast`, `/burden`(MR !152) | `installment-query.md` |
| Spending | `GET /spending/progress`, `/goal-comparison` | `spending-aggregation.md` |
| Cashflow | `POST /cashflow/forecasts`; `GET /cashflow/forecasts/latest`, `/daily-balances`, `/available-today` | `cashflow*.md` |
| Goal | `GET /goals/summary` | `goal.md` |
| Subscription | `GET /subscriptions`, `/summary`, `/{id}`, `/{id}/history`, `/candidates`, `/services`, `/categories`; `PATCH /subscriptions/{id}`, `/{id}/status`; `DELETE /subscriptions/{id}` | `subscription.md` |
| Pattern | `POST /patterns/analyze`; `GET /patterns/me`, `/me/spending`, `/me/cashflow`, `/me/priority-profile` | `pattern.md` |
| Diagnosis | `GET /diagnoses`(이력), `/{id}`, `/{id}/summary`, `/{id}/goal-impact`, `/{id}/changes` | `diagnosis*.md` |
| Recommendation | `POST /recommendations/{id}/accept`, `/{id}/reject`, `/{id}/complete`, `/{id}/defer`; `GET /recommendations/summary`, `/priorities`, `/history`, `/{id}/reasons`, `/{id}/effect`, `/{id}/goal-impact` | `recommendation*.md` |
| Simulation | `POST /simulations/saving-product`, `/loan/new`, `/loan/prepayment`, `/loan/refinance`, `/installment`, `/revolving`, `/cash-advance`, `/combined`; `GET /simulations/{id}/cashflow`, `/{id}/goal-impact`, `/{id}/assets`, `/{id}/debts`, `/{id}/costs`; `DELETE /simulations/{id}` | `simulation.md` |
| Product | `GET /products/companies`, `/deposits`, `/deposits/{id}`, `/savings`, `/savings/{id}`, `/loans/personal`, `/loans/mortgage`, `/loans/jeonse`, `/loans/{id}`, `/cards`, `/cards/{id}`, `/cards/recommended`; `POST /products/deposit-savings/compare`, `/loans/compare`, `/cards/compare` | `product-comparison-card-recommendation.md` |
| Notification | `GET /notifications/devices`; `DELETE /notifications/devices/{deviceId}`; `POST /notifications/devices`, `/events/run` | `notification.md` |
| Health | `GET /api/health` (비인증) | — |

## 6. FE에서 mock 데이터를 쓰는 화면

`frontend/src/mocks/*`를 import하는 화면 중 업무 데이터를 mock으로 그리는 곳은 없다. 남은 import는 표시용 상수뿐이다.

| import 위치 | mock 파일 | 용도 |
| --- | --- | --- |
| `GoalAccountSelectionList`, `AssetListItem`, `GoalLinkedAccounts`, `AssetSyncScreen`, `BankLogoCloud` | `financeConnection.FINANCIAL_INSTITUTIONS` | 은행 로고·이름 표시. 대응 BE API 없음 |
| `LedgerScreen` | `ledger.EMPTY_LEDGER_DRAFT` | 수동 거래 입력 폼 초기값 |

`mocks/assets.ts`, `discovery.ts`, `goalAccounts.ts`, `goalDetail.ts`, `home.ts`는 화면에서 더 이상 import하지 않는 잔여 파일이다.

푸시 토큰 등록(`POST /notifications/devices`)은 FE `notificationPermission.ts`가 OS 권한만 요청하고 서버 등록은 아직 호출하지 않는다.

## 7. 갱신 방법

BE 매핑 목록과 FE 호출 목록은 다음으로 다시 뽑아 이 문서와 대조한다.

```bash
# BE: 컨트롤러별 경로·메서드
grep -rnE "@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)" backend/src/main/java --include=*Controller.java

# FE: apiClient 호출 경로
grep -rnE "apiClient\.(get|post|postWithStatus|put|patch|delete)<" frontend/src/api
```

응답 필드는 `frontend/src/types/*Api.types.ts`와 `backend/.../dto/response/*.java`를, enum은 `backend/.../entity/*.java`를 직접 비교한다.

## 8. 병합 대기 MR (2026-09-16 저녁 기준)

아래가 병합되면 §1·§3·§5의 `(MR !nnn)` 표기를 지우고 수치를 확정한다. 모두 `develop` `684eb02` 기준 브랜치이며 각각 전체 테스트를 통과했다.

| MR | 내용 | FE에 미치는 영향 |
| --- | --- | --- |
| !150 | `GET /cards` 목록·상세에 `cardId` | `CardApiResponse`에 `cardId: number` 추가 권장 — `GET /installments?cardId=`·리볼빙 시뮬레이션 입력 |
| !151 | 예·적금 이자소득세·중도해지이율 정책 통합 | 적금 `early-termination`·`maturity-forecast` 응답 변경(`tax`, `expectedNetInterest` 추가, `expectedAmount` 세후). FE 미호출 |
| !152 | `GET /installments/burden` 신규 | 미호출. 응답 `{scheduledAmount, availableAmount, availableAmountBasis{…}, burdenRatePercent, unavailableReason}` |
| !153 | 루트 명세 17 `goalId?`·8 `cardId`·18-1 범위 제외 | 문서 |
| !154 | 545 군집 제외·532 보류 결정 기록 | 문서 |
| !155 | 대환·추가상환 본문 `loanId?`, `GET /loans` `loanId`, 프리셋에 `loanId` | `LoanApiResponse.loanId`, `RecommendationSimulationRequestApiResponse.loanId` 타입 추가 권장 — 대환 프리셋을 그대로 POST 가능 |
| !156 | `GET /recommendations/summary`에 `discovery{diagnosisId, issueCount, openRecommendationCount, expectedAnnualCostSaving, expectedGoalAdvanceDays, notes[]}` | 미호출. 발견 화면 통합 수치용, 형태 확인 필요 |
| !157 | 마이너스통장 상환·이자납부 `Idempotency-Key` 헤더 | 미호출. 연동 시 헤더 항상 전송 |

명세 미반영: `GET /loans` `loanId`(명세 10), `discovery`(명세 16), `GET /installments/burden` 응답(명세 9는 파라미터만 정의)은 FE 확인 후 반영한다.
