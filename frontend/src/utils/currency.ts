export function formatNumber(amount: number): string {
  return amount.toLocaleString('ko-KR');
}

/**
 * 예상 절감액 표시 규칙. 10만 원 미만은 원 단위를 그대로 두고, 그보다 크면 만 원 단위로
 * 반올림한다. 뒤에 붙는 단위 표기가 자리마다 달라 단위는 호출부에서 붙인다.
 */
export function formatRoundedAmount(amount: number): string {
  if (Math.abs(amount) < 100000) return formatNumber(amount);
  return `${formatNumber(Math.round(amount / 10000))}만`;
}

export function formatCurrency(amount: number): string {
  return `${formatNumber(amount)}원`;
}

/**
 * 계좌 잔액 표기. 서버가 원화가 아닌 계좌를 주면 `원`을 붙이면 금액을 잘못 읽게 되므로
 * 통화 코드를 그대로 뒤에 붙인다(`assetApi`의 `AccountApiResponse.currency`).
 */
export function formatAccountBalance(amount: number, currency: string): string {
  if (currency === 'KRW') return formatCurrency(amount);
  return `${formatNumber(amount)} ${currency}`;
}
