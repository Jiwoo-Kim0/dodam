import { financeErrorCodes, isApiError } from '@/api';

/**
 * 동기화가 끝나길 기다리다 한계에 닿았을 때 앱이 만드는 코드다. 서버 코드가 아니다.
 */
export const FINANCE_SYNC_TIMEOUT_CODE = 'FINANCE_SYNC_TIMEOUT';

const DEFAULT_MESSAGE = '문제가 생겼어요. 잠시 후 다시 시도해주세요.';

/**
 * 오류 코드로만 화면 문구를 고른다.
 * 금융망 응답 메시지에는 외부 식별자가 섞일 수 있어 서버 메시지 원문은 화면에 쓰지 않는다.
 */
export function getFinanceErrorMessage(error: unknown): string {
  if (!isApiError(error)) return DEFAULT_MESSAGE;

  switch (error.code) {
    case financeErrorCodes.notConnected:
      return '금융 정보 연결이 필요해요. 연결을 먼저 진행해주세요.';
    case financeErrorCodes.connectionNotFound:
      return '연결된 금융 정보가 없어요.';
    case financeErrorCodes.alreadyConnected:
      return '이미 금융 정보가 연결되어 있어요.';
    case financeErrorCodes.emailAlreadyRegistered:
      return '다른 서비스에 먼저 등록된 계정이에요. 고객센터에 문의해주세요.';
    case financeErrorCodes.syncInProgress:
      return '금융 정보를 가져오는 중이에요. 잠시 후 다시 시도해주세요.';
    case financeErrorCodes.apiError:
    case financeErrorCodes.apiUnavailable:
      return '금융망이 응답하지 않아요. 잠시 후 다시 시도해주세요.';
    case FINANCE_SYNC_TIMEOUT_CODE:
      return '금융 정보를 가져오는 데 시간이 오래 걸리고 있어요. 잠시 후 다시 확인해주세요.';
    case 'NETWORK_ERROR':
      return '서버에 연결할 수 없어요. 네트워크 상태를 확인해주세요.';
    default:
      return error.isUnauthorized
        ? '로그인이 만료됐어요. 다시 로그인해주세요.'
        : DEFAULT_MESSAGE;
  }
}
