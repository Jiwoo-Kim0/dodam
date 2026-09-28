import { StyleSheet } from 'react-native';

import { colors, componentSize, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    justifyContent: 'center',
    minHeight: 84,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
    position: 'relative',
    width: '100%',
  },
  cardPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  content: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 60,
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
    lineHeight: 30,
  },
  currencyUnit: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
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
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  changeAmount: {
    color: colors.primary.green,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  changePrefix: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  netAssetLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
    marginTop: spacing[1],
  },
  loadingIndicator: {
    alignSelf: 'flex-start',
    height: 30,
  },
  errorText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 30,
  },
  chartGroup: {
    alignItems: 'center',
    flexDirection: 'row',
    flexShrink: 0,
    gap: spacing[3],
    justifyContent: 'center',
    minHeight: 60,
  },
  legend: {
    gap: spacing[1],
    justifyContent: 'center',
    minHeight: 60,
    width: 48,
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
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
});
