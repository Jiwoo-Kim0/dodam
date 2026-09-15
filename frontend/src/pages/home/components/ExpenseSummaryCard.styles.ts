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
});
