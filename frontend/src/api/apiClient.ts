import { getAuthTokens, setAuthTokens } from '@/services/authTokenStorage';
import {
  endAuthSession,
  expireAuthSession,
  getAuthSessionVersion,
  isAuthSessionVersionCurrent,
} from '@/services/authSessionService';
import type { AuthResponse, AuthTokens } from '@/types/auth.types';

import { ApiError } from './ApiError';
import type {
  ApiQuery,
  ApiRequestOptions,
  ApiResponse,
  ApiResult,
} from './api.types';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL?.trim().replace(/\/+$/, '');
const AUTH_REFRESH_PATH = '/v1/auth/refresh';
/** 일반 호출의 응답 대기 상한. AI 코치처럼 오래 걸리는 호출은 `timeoutMs`로 따로 늘린다. */
const DEFAULT_TIMEOUT_MS = 15_000;

let refreshRequest: Promise<AuthTokens> | null = null;

function getApiBaseUrl() {
  if (!API_BASE_URL) {
    throw new ApiError({
      status: 0,
      code: 'API_BASE_URL_MISSING',
      message: 'EXPO_PUBLIC_API_BASE_URL 환경 변수가 설정되지 않았습니다.',
    });
  }

  return API_BASE_URL;
}

function appendQuery(url: string, query?: ApiQuery) {
  if (!query) return url;

  const searchParams = new URLSearchParams();

  Object.entries(query).forEach(([key, rawValue]) => {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];

    values.forEach((value) => {
      if (value !== null && value !== undefined) {
        searchParams.append(key, String(value));
      }
    });
  });

  const queryString = searchParams.toString();
  return queryString ? `${url}?${queryString}` : url;
}

function buildUrl(path: string, query?: ApiQuery) {
  const baseUrl = getApiBaseUrl();
  let normalizedPath = path.startsWith('/') ? path : `/${path}`;

  // 배포 Base URL이 /api로 끝나는 현재 인프라 계약과 전체 endpoint 표기를 모두 허용한다.
  if (baseUrl.endsWith('/api') && normalizedPath.startsWith('/api/')) {
    normalizedPath = normalizedPath.slice(4);
  }

  return appendQuery(`${baseUrl}${normalizedPath}`, query);
}

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  if (!value || typeof value !== 'object') return false;

  const response = value as Partial<ApiResponse<unknown>>;
  return typeof response.success === 'boolean' && 'data' in response;
}

async function parseResponse<T>(response: Response): Promise<T> {
  const responseText = await response.text();
  let payload: unknown = null;

  if (responseText) {
    try {
      payload = JSON.parse(responseText);
    } catch {
      throw new ApiError({
        status: response.status,
        code: 'INVALID_RESPONSE',
        message: '서버 응답을 해석할 수 없습니다.',
      });
    }
  }

  if (isApiResponse(payload)) {
    if (response.ok && payload.success) return payload.data as T;

    throw new ApiError({
      status: response.status,
      code: payload.code ?? 'API_ERROR',
      message: payload.message ?? '요청을 처리하지 못했습니다.',
      errors: payload.errors ?? [],
    });
  }

  if (!response.ok) {
    throw new ApiError({
      status: response.status,
      code: 'HTTP_ERROR',
      message: `서버 요청에 실패했습니다. (${response.status})`,
    });
  }

  throw new ApiError({
    status: response.status,
    code: 'INVALID_RESPONSE',
    message: '서버 응답 형식이 올바르지 않습니다.',
  });
}

function createRequestBody(body: unknown, headers: Headers) {
  if (body === undefined) return undefined;

  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;
  const isBlob = typeof Blob !== 'undefined' && body instanceof Blob;
  const isRawBody =
    typeof body === 'string' ||
    isFormData ||
    isBlob ||
    body instanceof ArrayBuffer ||
    ArrayBuffer.isView(body);

  if (isRawBody) return body as BodyInit;

  if (!headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  return JSON.stringify(body);
}

async function fetchOnce<T>(
  method: HttpMethod,
  path: string,
  options: ApiRequestOptions,
  accessToken?: string,
) {
  const {
    auth: _auth,
    body: requestBody,
    headers: requestHeaders,
    query,
    retryOnUnauthorized: _retryOnUnauthorized,
    signal: callerSignal,
    timeoutMs = DEFAULT_TIMEOUT_MS,
    ...requestInit
  } = options;
  const headers = new Headers(requestHeaders);
  headers.set('Accept', 'application/json');

  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`);
  } else {
    headers.delete('Authorization');
  }

  const body = createRequestBody(requestBody, headers);

  /*
   * 타임아웃이 없으면 응답이 영영 안 오는 요청을 끊을 수 없다. 화면은 로딩에 갇히고
   * 사용자는 재시도조차 못 누른다. 호출자가 준 signal도 함께 존중해야 해서 둘을 묶는다.
   */
  const timeoutController = new AbortController();
  const timeoutId = setTimeout(() => timeoutController.abort(), timeoutMs);
  const abortByCaller = () => timeoutController.abort();
  callerSignal?.addEventListener('abort', abortByCaller);

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      ...requestInit,
      method,
      headers,
      body,
      signal: timeoutController.signal,
    });
  } catch (error) {
    // 호출자가 끊은 것과 시간이 다 된 것은 사용자에게 다른 말로 알려야 한다.
    const isTimeout = timeoutController.signal.aborted && callerSignal?.aborted !== true;
    throw new ApiError({
      status: 0,
      code: isTimeout ? 'TIMEOUT' : 'NETWORK_ERROR',
      message: isTimeout
        ? '서버 응답이 너무 오래 걸려요. 잠시 후 다시 시도해주세요.'
        : '서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.',
      cause: error,
    });
  } finally {
    clearTimeout(timeoutId);
    callerSignal?.removeEventListener('abort', abortByCaller);
  }

  return { response, data: () => parseResponse<T>(response) };
}

function toAuthTokens(response: AuthResponse): AuthTokens {
  return {
    tokenType: response.tokenType,
    accessToken: response.accessToken,
    accessTokenExpiresAt: response.accessTokenExpiresAt,
    refreshToken: response.refreshToken,
    refreshTokenExpiresAt: response.refreshTokenExpiresAt,
  };
}

export async function refreshAuthTokens(): Promise<AuthTokens> {
  if (refreshRequest) return refreshRequest;

  refreshRequest = (async () => {
    const sessionVersion = getAuthSessionVersion();
    let currentTokens: AuthTokens | null;
    try {
      currentTokens = await getAuthTokens();
    } catch (error) {
      await expireAuthSession();
      throw error;
    }

    if (!currentTokens) {
      await expireAuthSession();
      throw new ApiError({
        status: 401,
        code: 'UNAUTHENTICATED',
        message: '저장된 로그인 정보가 없습니다.',
      });
    }

    let response: AuthResponse;
    try {
      const request = await fetchOnce<AuthResponse>('POST', AUTH_REFRESH_PATH, {
        auth: false,
        body: { refreshToken: currentTokens.refreshToken },
      });
      response = await request.data();
    } catch (error) {
      await expireAuthSession();
      throw error;
    }

    if (!isAuthSessionVersionCurrent(sessionVersion)) {
      throw new ApiError({
        status: 401,
        code: 'AUTH_SESSION_ENDED',
        message: '로그인 세션이 종료되었습니다.',
      });
    }

    const nextTokens = toAuthTokens(response);
    try {
      await setAuthTokens(nextTokens);
    } catch (error) {
      await expireAuthSession();
      throw error;
    }

    if (!isAuthSessionVersionCurrent(sessionVersion)) {
      await endAuthSession();
      throw new ApiError({
        status: 401,
        code: 'AUTH_SESSION_ENDED',
        message: '로그인 세션이 종료되었습니다.',
      });
    }

    return nextTokens;
  })().finally(() => {
    refreshRequest = null;
  });

  return refreshRequest;
}

async function requestWithStatus<T>(
  method: HttpMethod,
  path: string,
  options: ApiRequestOptions = {},
): Promise<ApiResult<T>> {
  const requiresAuth = options.auth !== false;
  const tokens = requiresAuth ? await getAuthTokens() : null;
  const firstRequest = await fetchOnce<T>(
    method,
    path,
    options,
    tokens?.accessToken,
  );

  if (firstRequest.response.status !== 401 || !requiresAuth) {
    return {
      data: await firstRequest.data(),
      status: firstRequest.response.status,
    };
  }

  if (!tokens || options.retryOnUnauthorized === false) {
    await expireAuthSession();
    return {
      data: await firstRequest.data(),
      status: firstRequest.response.status,
    };
  }

  const latestTokens = await getAuthTokens();
  const refreshedTokens =
    latestTokens && latestTokens.accessToken !== tokens.accessToken
      ? latestTokens
      : await refreshAuthTokens();
  const retryRequest = await fetchOnce<T>(
    method,
    path,
    { ...options, retryOnUnauthorized: false },
    refreshedTokens.accessToken,
  );

  if (retryRequest.response.status === 401) {
    await expireAuthSession();
  }

  return {
    data: await retryRequest.data(),
    status: retryRequest.response.status,
  };
}

async function request<T>(
  method: HttpMethod,
  path: string,
  options: ApiRequestOptions = {},
): Promise<T> {
  const { data } = await requestWithStatus<T>(method, path, options);
  return data;
}

export const apiClient = {
  get: <T>(path: string, options?: ApiRequestOptions) =>
    request<T>('GET', path, options),
  post: <T>(path: string, options?: ApiRequestOptions) =>
    request<T>('POST', path, options),
  /** 200·201처럼 상태 코드로 분기해야 하는 POST에 쓴다. */
  postWithStatus: <T>(path: string, options?: ApiRequestOptions) =>
    requestWithStatus<T>('POST', path, options),
  put: <T>(path: string, options?: ApiRequestOptions) =>
    request<T>('PUT', path, options),
  patch: <T>(path: string, options?: ApiRequestOptions) =>
    request<T>('PATCH', path, options),
  delete: <T>(path: string, options?: ApiRequestOptions) =>
    request<T>('DELETE', path, options),
};
