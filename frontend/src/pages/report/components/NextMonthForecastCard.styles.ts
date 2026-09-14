import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  forecastCard: {
    padding: spacing[4],
  },
  expenseLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    lineHeight: 18,
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  expenseAmount: {
    ...typography.headline,
    color: colors.textPrimary,
    letterSpacing: -0.6,
    lineHeight: 34,
    marginTop: spacing[1],
  },
  expenseAmountUnit: {
    ...typography.body1,
  },
  expenseComparison: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  expenseComparisonValue: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  breakdownBar: {
    alignSelf: 'stretch',
    borderRadius: radius.round,
    flexDirection: 'row',
    height: 10,
    marginTop: spacing[3],
    overflow: 'hidden',
  },
  breakdownBarSegment: {
    height: '100%',
  },
  breakdownLegend: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  breakdownItem: {
    flex: 1,
    minWidth: 0,
  },
  breakdownLabelRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  breakdownDot: {
    borderRadius: radius.round,
    height: 5,
    width: 5,
  },
  breakdownLabel: {
    color: colors.textSecondary,
    flex: 1,
    flexShrink: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
  breakdownValueRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: spacing[1],
    marginTop: 2,
  },
  breakdownAmount: {
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 17,
  },
  breakdownRatio: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
});
