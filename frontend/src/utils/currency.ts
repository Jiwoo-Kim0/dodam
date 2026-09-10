export function formatNumber(amount: number): string {
  return amount.toLocaleString('ko-KR');
}

export function formatCurrency(amount: number): string {
  return `${formatNumber(amount)}원`;
}
