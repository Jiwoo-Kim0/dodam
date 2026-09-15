/**
 * API 명세 4. Finance Data 응답 타입.
 * 서버는 금융망 `userKey`와 이메일 원문을 어떤 응답에도 싣지 않는다. 화면에서도 마스킹된 값만 쓴다.
 */

/** `finance_connection.status`. 연결 행이 아직 없는 사용자는 PENDING으로 온다. */
export type FinanceConnectionStatus =
  | 'PENDING'
  | 'CONNECTED'
  | 'DISCONNECTED'
  | 'FAILED';

export interface FinanceConnection {
  status: FinanceConnectionStatus;
  /** 금융망에 등록된 이메일의 마스킹 값. 미연결이면 null이다. */
  memberEmailMasked: string | null;
  connectedAt: string | null;
  disconnectedAt: string | null;
  lastSyncedAt: string | null;
}

export type FinanceSyncScope = 'ALL' | 'ACCOUNT' | 'CARD' | 'LOAN' | 'SAVING';

/** 실제로 동기화를 수행하는 도메인. ALL은 아래 네 가지를 차례로 실행한다. */
export type FinanceSyncDomainScope = Exclude<FinanceSyncScope, 'ALL'>;

export type FinanceSyncStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export type FinanceSyncScopeStatus = 'COMPLETED' | 'SKIPPED' | 'FAILED';

/**
 * scope별 실행 결과. 서버에 handler가 등록되지 않은 scope는 SKIPPED로 돌아온다
 * (2026-09-15 기준 ACCOUNT만 등록되어 CARD·LOAN·SAVING은 SKIPPED).
 */
export interface FinanceSyncScopeResult {
  scope: FinanceSyncDomainScope;
  status: FinanceSyncScopeStatus;
  syncedCount: number;
  message: string | null;
}

/** 동기화 실행 1건. `results`는 실행 직후 응답에만 있고 상태·이력 조회에서는 null이다. */
export interface FinanceSync {
  syncId: number;
  scope: FinanceSyncScope;
  status: FinanceSyncStatus;
  progress: number | null;
  /** 서버 로그용 원문이라 화면에 그대로 노출하지 않는다. */
  errorMessage: string | null;
  startedAt: string;
  completedAt: string | null;
  results: FinanceSyncScopeResult[] | null;
}

export interface FinanceSyncStatusResult {
  /** 만료된 RUNNING 행은 진행 중으로 보지 않는다. */
  inProgress: boolean;
  /** 가장 최근 동기화. 이력이 없으면 null이다. */
  sync: FinanceSync | null;
}

export interface FinanceSyncLatest {
  lastSyncedAt: string | null;
  lastCompletedSync: FinanceSync | null;
}

export interface FinanceSyncRequest {
  scope: FinanceSyncScope;
}
