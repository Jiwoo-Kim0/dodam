export interface ApiFieldError {
  field: string;
  reason: string;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
  code?: string | null;
  errors?: ApiFieldError[] | null;
}

/** 상태 코드로 분기해야 하는 API(예: 소셜 로그인 200 기존 회원 / 201 신규 회원)를 위한 결과 타입. */
export interface ApiResult<T> {
  data: T;
  status: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export type ApiQueryValue = string | number | boolean | null | undefined;

export type ApiQuery = Record<
  string,
  ApiQueryValue | readonly ApiQueryValue[]
>;

export interface ApiRequestOptions
  extends Omit<RequestInit, 'body' | 'headers' | 'method'> {
  auth?: boolean;
  body?: unknown;
  headers?: HeadersInit;
  query?: ApiQuery;
  retryOnUnauthorized?: boolean;
  /**
   * 응답을 이만큼(ms) 기다렸는데도 안 오면 끊는다. 생략하면 기본값을 쓴다.
   * AI 응답처럼 오래 걸리는 호출에서만 늘려 잡는다.
   */
  timeoutMs?: number;
}
