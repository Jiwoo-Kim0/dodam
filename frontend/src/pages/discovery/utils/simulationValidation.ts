import type {
  SavingSimulationRequest,
  SpendingSimulationRequest,
} from '@/types/simulationApi.types';

export type BasicSimulationInput =
  | { type: 'SPENDING'; request: SpendingSimulationRequest }
  | { type: 'SAVING'; request: SavingSimulationRequest };

export type BasicSimulationField =
  | 'category'
  | 'monthlyReduction'
  | 'accountNo'
  | 'monthlySavingAmount'
  | 'durationMonths';

export type BasicSimulationValidationErrors = Partial<
  Record<BasicSimulationField, string>
>;

function isValidMoney(value: number) {
  return Number.isSafeInteger(value);
}

function validateDuration(
  durationMonths: number | undefined,
  errors: BasicSimulationValidationErrors,
) {
  if (durationMonths === undefined) return;

  if (!Number.isInteger(durationMonths) || durationMonths < 1 || durationMonths > 120) {
    errors.durationMonths = '기간은 1개월에서 120개월 사이로 입력해주세요.';
  }
}

export function validateBasicSimulationInput(
  input: BasicSimulationInput,
): BasicSimulationValidationErrors {
  const errors: BasicSimulationValidationErrors = {};
  validateDuration(input.request.durationMonths, errors);

  if (input.type === 'SPENDING') {
    if (
      !isValidMoney(input.request.monthlyReduction) ||
      input.request.monthlyReduction <= 0
    ) {
      errors.monthlyReduction = '월 소비 감소액을 1원 이상 입력해주세요.';
    }

    if ((input.request.category?.trim().length ?? 0) > 50) {
      errors.category = '카테고리는 50자 이내로 입력해주세요.';
    }

    return errors;
  }

  if (
    !isValidMoney(input.request.monthlySavingAmount) ||
    input.request.monthlySavingAmount < 0
  ) {
    errors.monthlySavingAmount = '변경할 월 저축액을 0원 이상 입력해주세요.';
  }

  const accountNo = input.request.accountNo?.trim();
  if (accountNo && !/^[0-9-]{8,32}$/.test(accountNo)) {
    errors.accountNo = '계좌번호는 숫자와 하이픈을 포함해 8~32자로 입력해주세요.';
  }

  return errors;
}

export function normalizeBasicSimulationInput(
  input: BasicSimulationInput,
): BasicSimulationInput {
  if (input.type === 'SPENDING') {
    const category = input.request.category?.trim();

    return {
      type: input.type,
      request: {
        ...input.request,
        category: category || undefined,
      },
    };
  }

  const accountNo = input.request.accountNo?.trim();

  return {
    type: input.type,
    request: {
      ...input.request,
      accountNo: accountNo || undefined,
    },
  };
}
