import type { DiagnosisIssueApiResponse } from '@/types/discoveryApi.types';

/** 진단이 저장한 대환 추정. 계산하지 못한 경우 사유만 들어온다. */
export interface LoanRefinanceEstimate {
  targetProductName: string | null;
  targetCompanyName: string | null;
  appliedRate: number;
  remainingInstallments: number | null;
  beforeInterest: number;
  afterInterest: number;
  interestSaving: number;
  monthlySaving: number | null;
}

export interface LoanRateDiagnosis {
  accountName: string;
  institutionName: string | null;
  interestRate: number;
  referenceRate: number;
  rateDifference: number;
  balance: number | null;
  overdue: boolean;
  refinance: LoanRefinanceEstimate | null;
  /** 대환 추정을 못 한 이유. 추정이 있으면 null. */
  refinanceUnavailableReason: string | null;
}

export function isLoanHighInterestIssue(issue: DiagnosisIssueApiResponse) {
  return issue.domain === 'LOAN' && issue.type === 'HIGH_INTEREST_RATE';
}

function record(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function numberOf(source: Record<string, unknown> | null, key: string) {
  const value = source?.[key];
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function nonNegative(source: Record<string, unknown> | null, key: string) {
  const value = numberOf(source, key);
  return value !== null && value >= 0 ? value : null;
}

function textOf(source: Record<string, unknown> | null, key: string) {
  const value = source?.[key];
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

/**
 * 대환 추정을 읽는다. `available`이 false면 `unavailableReason`만 들어 있으므로 null을 돌려준다.
 * 금액이 하나라도 비면 전후 비교를 만들 수 없어 같은 취급이다.
 */
function refinanceOf(source: Record<string, unknown> | null) {
  const refinance = record(source?.refinance);
  if (!refinance || refinance.available !== true) return null;

  const appliedRate = numberOf(refinance, 'appliedRate');
  const beforeInterest = nonNegative(refinance, 'beforeInterest');
  const afterInterest = nonNegative(refinance, 'afterInterest');
  const interestSaving = nonNegative(refinance, 'interestSaving');
  if (
    appliedRate === null ||
    beforeInterest === null ||
    afterInterest === null ||
    interestSaving === null ||
    interestSaving <= 0
  ) {
    return null;
  }

  return {
    targetProductName: textOf(refinance, 'targetProductName'),
    targetCompanyName: textOf(refinance, 'targetCompanyName'),
    appliedRate,
    remainingInstallments: nonNegative(refinance, 'remainingInstallments'),
    beforeInterest,
    afterInterest,
    interestSaving,
    monthlySaving: nonNegative(refinance, 'monthlySaving'),
  };
}

/**
 * 진단 시점 스냅샷만 읽어 대출 금리 비교에 필요한 값을 모은다. 현재 금리·잔액으로 재계산하지 않는다.
 *
 * <p>금리는 `HighInterestLoanRule`이 저장한 그대로다 — 내 금리(`interestRate`)와 같은 비교군·기간
 * 밴드·상환방식의 공시 최저금리 중앙값(`referenceRate`), 그 편차(`rateDifference`)다.
 * 기준금리는 실제 평균 대출금리도 승인금리도 아니라서 화면 문구도 "공시금리"로만 쓴다.
 */
export function loanRateDiagnosis(
  issue: DiagnosisIssueApiResponse,
): LoanRateDiagnosis | null {
  const current = record(issue.currentValue);
  const interestRate = numberOf(current, 'interestRate');
  const referenceRate = numberOf(current, 'referenceRate');
  const rateDifference = numberOf(current, 'rateDifference');
  if (
    interestRate === null ||
    referenceRate === null ||
    rateDifference === null ||
    rateDifference <= 0
  ) {
    return null;
  }

  const refinance = refinanceOf(current);
  return {
    accountName: textOf(current, 'accountName') ?? '대출',
    institutionName: textOf(current, 'institutionName'),
    interestRate,
    referenceRate,
    rateDifference,
    balance: nonNegative(current, 'balance'),
    overdue: current?.overdue === true || textOf(current, 'status') === 'OVERDUE',
    refinance,
    refinanceUnavailableReason: refinance
      ? null
      : textOf(record(current?.refinance), 'unavailableReason'),
  };
}

/**
 * 대환 절감액을 계산하지 못한 이유를 사용자 문장으로 옮긴다.
 *
 * <p>사유를 감추지 않는 이유: 셋 중 `LOAN_OVERDUE`는 사용자가 손쓸 수 있는 상태라 알려 줄 값어치가
 * 있고, 나머지도 "왜 절감액이 안 보이나"를 설명하지 않으면 화면이 고장난 것처럼 보인다.
 */
export function refinanceUnavailableMessage(reason: string | null) {
  switch (reason) {
    case 'LOAN_OVERDUE':
      return '연체 중이라 대환 절감액을 계산하지 못했어요. 연체를 먼저 해소해 주세요.';
    case 'NO_ELIGIBLE_PRODUCT':
      return '남은 기간·상환방식에 맞는 대환 상품을 찾지 못했어요.';
    case 'SCHEDULE_NOT_COMPUTABLE':
      return '남은 상환 일정을 계산할 수 없어 절감액을 추정하지 못했어요.';
    default:
      return '대환 절감액을 계산하지 못했어요.';
  }
}

/** 소수점이 있으면 그대로, 없으면 정수로. `7.20` → `7.2`, `5.00` → `5` */
export function formatRate(rate: number) {
  return `${Number(rate.toFixed(2))}`;
}
