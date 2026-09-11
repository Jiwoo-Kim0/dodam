import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    minHeight: 86,
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
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 24,
    letterSpacing: -0.6,
    lineHeight: 30,
    marginTop: spacing[1],
  },
  currencyUnit: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    letterSpacing: -0.2,
  },
});
