import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  transactionsCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  sectionHeadingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  transactionCount: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  transactionList: {
    marginTop: spacing[3],
  },
  transactionRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 48,
  },
  /** 행이 여러 개 붙어 있으면 어디까지가 한 건인지 읽기 어려워 얇은 선으로 끊는다. */
  separatedRow: {
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  /** 시간은 보조 정보라 가맹점 이름보다 뒤로 물러나게 둔다. */
  transactionTime: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    letterSpacing: -0.2,
    width: 44,
  },
  paymentIconFrame: {
    alignItems: 'center',
    height: 30,
    justifyContent: 'center',
    marginRight: spacing[2],
    width: 30,
  },
  paymentIcon: {
    height: 30,
    width: 30,
  },
  transactionInfo: {
    flex: 1,
    minWidth: 0,
  },
  transactionMerchant: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 19,
  },
  transactionCategory: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
  transactionAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    letterSpacing: -0.3,
    marginLeft: spacing[2],
  },
  expandButton: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    height: 40,
    justifyContent: 'center',
    marginTop: spacing[1],
  },
  expandButtonText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
});
