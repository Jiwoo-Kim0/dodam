import type { AccountSummaryApiResponse } from '@/types/assetApi.types';
import type { AssetOverview } from '@/types/asset.types';

/** 부채를 차감하지 않고 사용자가 보유한 계좌 잔액만 보여준다. */
export function mapOwnedAssetOverview(
  accountSummary: AccountSummaryApiResponse,
): AssetOverview {
  return {
    netAsset: accountSummary.totalAssets,
    changeFromLastMonth: null,
    composition: [
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
    ],
  };
}
