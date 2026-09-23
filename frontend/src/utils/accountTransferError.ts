import { financeErrorCodes, isApiError } from '@/api';

/**
 * 계좌 이체 실패를 화면 문구로 바꾼다. 오류표는 docs/goal-transfer-plan.md §3-3이다.
 *
 * 금융망 응답 메시지에는 외부 식별자가 섞일 수 있어 서버 메시지 원문은 쓰지 않고
 * 코드로만 문구를 고른다(`financeErrorMessage`와 같은 규칙).
 */

/** 이체에서만 나오는 코드. 금융 연동 공통 코드는 `financeErrorCodes`에 있다. */
export const transferErrorCodes = {
  insufficientBalance: 'TRANSFER_INSUFFICIENT_BALANCE',
  limitExceeded: 'TRANSFER_LIMIT_EXCEEDED',
} as const;

const DEFAULT_MESSAGE = '이체하지 못했어요. 잠시 후 다시 시도해주세요.';

/** 금융망 연결이 끊겨 연결 화면으로 보내야 하는 실패인지. */
export function isFinanceNotConnected(error: unknown): boolean {
  return isApiError(error) && error.code === financeErrorCodes.notConnected;
}

/** 계좌 목록이 실제와 달라 다시 불러와야 하는 실패인지. */
export function needsAccountReload(error: unknown): boolean {
  return isApiError(error) && error.status === 404;
}

export function getAccountTransferErrorMessage(error: unknown): string {
  if (!isApiError(error)) return DEFAULT_MESSAGE;

  switch (error.code) {
    case transferErrorCodes.insufficientBalance:
      return '잔액이 부족해요. 금액을 줄여서 다시 시도해주세요.';
    /*
     * 한도는 계좌마다 달라 문구에 고정 금액을 쓰지 않는다(§3-3). "1회 100만원"처럼 적으면
     * 다른 한도를 가진 계좌에서 틀린 안내가 된다.
     */
    case transferErrorCodes.limitExceeded:
      return '이 계좌의 이체 한도를 넘었어요. 금액을 줄여서 다시 시도해주세요.';
    case financeErrorCodes.notConnected:
      return '금융 정보 연결이 필요해요. 연결을 먼저 진행해주세요.';
    case financeErrorCodes.apiError:
      return '금융망이 오류를 보냈어요. 잠시 후 다시 시도해주세요.';
    case financeErrorCodes.apiUnavailable:
      return '금융망에 연결할 수 없어요. 잠시 후 다시 시도해주세요.';
    case 'INVALID_REQUEST':
      return '보낼 금액이나 계좌를 다시 확인해주세요.';
    case 'RESOURCE_NOT_FOUND':
      return '계좌 정보가 바뀌었어요. 화면을 새로고침한 뒤 다시 시도해주세요.';
    case 'NETWORK_ERROR':
      return '서버에 연결할 수 없어요. 네트워크 상태를 확인해주세요.';
    default:
      return error.isUnauthorized
        ? '로그인이 만료됐어요. 다시 로그인해주세요.'
        : DEFAULT_MESSAGE;
  }
}
