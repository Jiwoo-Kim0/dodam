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
}
