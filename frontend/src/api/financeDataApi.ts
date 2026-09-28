import type {
  FinanceConnection,
  FinanceSync,
  FinanceSyncLatest,
  FinanceSyncRequest,
  FinanceSyncScope,
  FinanceSyncStatusResult,
} from '@/types/finance.types';

import { apiClient } from './apiClient';
import { isApiError } from './ApiError';

const FINANCE_DATA_PATH = '/v1/finance-data';

/**
 * 서버가 돌려주는 금융망 오류 코드(backend/docs/finance-data.md).
 * 화면 문구는 이 코드로만 고르고 서버 메시지 원문은 쓰지 않는다.
 */
export const financeErrorCodes = {
  connectionNotFound: 'FINANCE_CONNECTION_NOT_FOUND',
  alreadyConnected: 'FINANCE_ALREADY_CONNECTED',
  notConnected: 'FINANCE_NOT_CONNECTED',
  emailAlreadyRegistered: 'FINANCE_EMAIL_ALREADY_REGISTERED',
  syncInProgress: 'FINANCE_SYNC_IN_PROGRESS',
  apiError: 'FINANCE_API_ERROR',
  apiUnavailable: 'FINANCE_API_UNAVAILABLE',
} as const;

export function getConnection() {
  return apiClient.get<FinanceConnection>(`${FINANCE_DATA_PATH}/connection`);
}

/** 금융망 사용자 등록. 이미 CONNECTED면 409 FINANCE_ALREADY_CONNECTED를 던진다. */
export function connect() {
  return apiClient.post<FinanceConnection>(`${FINANCE_DATA_PATH}/connection`);
}

/** 우리 쪽 연결만 해제한다. 진행 중인 동기화가 있으면 409다. */
export function disconnect() {
  return apiClient.delete<FinanceConnection>(`${FINANCE_DATA_PATH}/connection`);
}

/**
 * 온보딩에서 쓰는 연결. 회원가입 때 이미 연결된 계정(409)도 성공으로 본다.
 * 소셜 가입은 공급자가 이메일을 준 경우 가입 시점에 이미 CONNECTED가 된다.
 */
export async function ensureConnected(): Promise<FinanceConnection> {
  try {
    return await connect();
  } catch (error) {
    if (isApiError(error) && error.code === financeErrorCodes.alreadyConnected) {
      return getConnection();
    }

    throw error;
  }
}

/**
 * 금융 데이터 갱신. 서버가 요청 스레드에서 동기 실행하고 결과를 바로 돌려주므로
 * 계좌 수에 따라 응답까지 수십 초가 걸릴 수 있다.
 */
export function sync(scope: FinanceSyncScope = 'ALL') {
  const body: FinanceSyncRequest = { scope };
  return apiClient.post<FinanceSync>(`${FINANCE_DATA_PATH}/sync`, { body });
}

export function getSyncStatus() {
  return apiClient.get<FinanceSyncStatusResult>(`${FINANCE_DATA_PATH}/sync/status`);
}

export function getLatestSync() {
  return apiClient.get<FinanceSyncLatest>(`${FINANCE_DATA_PATH}/sync/latest`);
}

export const financeDataApi = {
  getConnection,
  connect,
  disconnect,
  ensureConnected,
  sync,
  getSyncStatus,
  getLatestSync,
};
