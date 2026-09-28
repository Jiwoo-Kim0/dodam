export function calculateProgressPercent(
  currentAmount: number,
  targetAmount: number,
) {
  if (
    !Number.isFinite(currentAmount) ||
    !Number.isFinite(targetAmount) ||
    targetAmount <= 0
  ) {
    return 0;
  }

  const percentage = Math.floor((currentAmount / targetAmount) * 100);
  return Math.min(100, Math.max(0, percentage));
}
