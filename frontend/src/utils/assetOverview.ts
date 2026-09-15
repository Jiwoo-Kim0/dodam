import type {
  AccountSummaryApiResponse,
  LoanSummaryApiResponse,
} from '@/types/assetApi.types';
import type { AssetOverview } from '@/types/asset.types';

export function mapAssetOverview(
  accountSummary: AccountSummaryApiResponse,
  loanSummary: LoanSummaryApiResponse,
): AssetOverview {
  const savingAmount =
    accountSummary.depositBalance + accountSummary.savingsBalance;
  const checkingAmount = accountSummary.demandDepositBalance;
  const loanAmount =
    loanSummary.totalBalance + accountSummary.minusUsedAmount;

  return {
    netAsset: accountSummary.netAssets - loanSummary.totalBalance,
    changeFromLastMonth: null,
    composition: [
      {
        label: '예·적금',
        amount: savingAmount,
        tone: 'strong',
      },
      {
        label: '입출금',
        amount: checkingAmount,
        tone: 'medium',
      },
      {
        label: '대출',
        amount: loanAmount,
        tone: 'light',
      },
    ],
  };
}
