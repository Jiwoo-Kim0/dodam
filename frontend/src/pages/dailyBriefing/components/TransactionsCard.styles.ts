import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  transactionsCard: {
    paddingHorizontal: spacing[4],
    paddingTop: spacing[3],
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
  transactionTime: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    letterSpacing: -0.2,
    width: 58,
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
    fontSize: 11,
    lineHeight: 15,
  },
  transactionAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 15,
    letterSpacing: -0.3,
    marginLeft: spacing[2],
  },
  expandButton: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    height: 42,
    justifyContent: 'center',
    marginTop: spacing[1],
  },
  expandButtonText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
  },
});
