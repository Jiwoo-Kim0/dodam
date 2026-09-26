import type { AccountSummaryApiResponse } from '@/types/assetApi.types';
import type { AssetComposition, AssetOverview } from '@/types/asset.types';

/**
 * 총자산은 부채를 차감하지 않은 계좌 잔액 합계다. 도넛에는 대출 잔액을 한 조각으로 더해
 * 보유 자산 대비 빚의 비중을 함께 보여준다. 대출 금액은 자산 탭 대출 섹션과 같은
 * `/v1/loans/summary`의 `totalBalance`를 쓰며, 조회하지 못했거나 0이면 조각을 뺀다.
 */
export function mapOwnedAssetOverview(
  accountSummary: AccountSummaryApiResponse,
  loanBalance: number | null,
): AssetOverview {
  const composition: AssetComposition[] = [
    {
      label: '예·적금',
      amount: accountSummary.depositBalance + accountSummary.savingsBalance,
      tone: 'strong',
    },
    {
      label: '입출금',
      amount: accountSummary.demandDepositBalance,
      tone: 'medium',
    },
  ];

  if (loanBalance !== null && loanBalance > 0) {
    composition.push({ label: '대출', amount: loanBalance, tone: 'debt' });
  }

  return {
    netAsset: accountSummary.totalAssets,
    changeFromLastMonth: null,
    composition,
  };
}
