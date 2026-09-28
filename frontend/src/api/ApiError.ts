import type { ApiFieldError } from './api.types';

interface ApiErrorOptions {
  status: number;
  code: string;
  message: string;
  errors?: readonly ApiFieldError[];
  cause?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly errors: readonly ApiFieldError[];

  constructor({ status, code, message, errors = [], cause }: ApiErrorOptions) {
    super(message, { cause });
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.errors = errors;
  }

  get isUnauthorized() {
    return this.status === 401;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
