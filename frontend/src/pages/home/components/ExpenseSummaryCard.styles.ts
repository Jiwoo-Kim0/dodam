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
  label: {
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
});
