import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    minHeight: 120,
    padding: spacing[4],
    position: 'relative',
    width: '100%',
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  content: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  copy: {
    flex: 1,
    minWidth: 0,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 24,
    letterSpacing: -0.6,
    lineHeight: 30,
  },
  currencyUnit: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    letterSpacing: -0.2,
  },
  changeRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 2,
    marginTop: 1,
  },
  changeArrow: {
    color: colors.primary.green,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 14,
  },
  changeAmount: {
    color: colors.primary.green,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 14,
  },
  changePrefix: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 14,
  },
  chartGroup: {
    alignItems: 'center',
    flexDirection: 'row',
    flexShrink: 0,
    gap: spacing[3],
  },
  legend: {
    gap: spacing[1],
    width: 42,
  },
  legendItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: 3,
  },
  legendDot: {
    borderRadius: radius.round,
    height: 5,
    width: 5,
  },
  legendLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 8,
    lineHeight: 11,
  },
});
