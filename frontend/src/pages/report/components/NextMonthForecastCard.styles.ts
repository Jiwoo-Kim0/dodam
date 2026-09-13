import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  forecastCard: {
    padding: spacing[5],
  },
  expenseLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    lineHeight: 18,
    marginTop: spacing[5],
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
    borderRadius: radius.round,
    flexDirection: 'row',
    height: 10,
    marginTop: spacing[3],
    overflow: 'hidden',
    width: '100%',
  },
  breakdownBarSegment: {
    height: '100%',
  },
  breakdownLegend: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[6],
  },
  breakdownItem: {
    flex: 1,
    minWidth: 0,
  },
  breakdownLabelRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
  },
  breakdownDot: {
    borderRadius: radius.round,
    height: 7,
    width: 7,
  },
  breakdownLabel: {
    ...typography.body2,
    color: colors.textSecondary,
    flexShrink: 1,
    lineHeight: 18,
  },
  breakdownAmount: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    lineHeight: 18,
    marginTop: spacing[1],
  },
  breakdownRatio: {
    ...typography.caption,
    color: colors.textSecondary,
    lineHeight: 17,
  },
});
