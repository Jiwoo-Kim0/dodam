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
