import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    position: 'relative',
    width: '100%',
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  content: {
    alignItems: 'center',
    flexDirection: 'row',
    paddingRight: spacing[8],
  },
  copy: {
    flex: 1,
    minWidth: 0,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    letterSpacing: -0.6,
    lineHeight: 28,
    marginTop: spacing[1],
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
    marginTop: spacing[1],
  },
  changeArrow: {
    color: colors.primary.green,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  changeAmount: {
    color: colors.primary.green,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  changePrefix: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  netAssetLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    marginTop: spacing[1],
  },
  loadingIndicator: {
    alignSelf: 'flex-start',
    marginTop: spacing[2],
  },
  errorText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    marginTop: spacing[2],
  },
  chartGroup: {
    alignItems: 'center',
    flexDirection: 'row',
    flexShrink: 0,
    gap: spacing[2],
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
    fontSize: fontSize.tiny,
    lineHeight: 11,
  },
});
