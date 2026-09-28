import { toDateKey } from '@/utils/date';

export function getTransactionAmountError(value: string): string | null {
  if (!value) return '금액을 입력해 주세요.';

  const amount = Number(value);
  if (!Number.isSafeInteger(amount)) return '입력 가능한 금액 범위를 초과했어요.';
  if (amount <= 0) return '금액은 1원 이상 입력해 주세요.';
  return null;
}

export function isValidTransactionDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) return false;

  const [, yearText, monthText, dayText] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const date = new Date(year, month - 1, day);

  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day &&
    value <= toDateKey(new Date())
  );
}

export function isValidTransactionTime(value: string): boolean {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  if (!match) return false;

  const hour = Number(match[1]);
  const minute = Number(match[2]);
  return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
}
