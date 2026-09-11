import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const styles = StyleSheet.create({
  upcomingCard: {
    alignItems: 'center',
    flexDirection: 'row',
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[4],
  },
  upcomingIcon: {
    height: 36,
    marginRight: spacing[4],
    width: 36,
  },
  upcomingContent: {
    flex: 1,
    minWidth: 0,
  },
  upcomingLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
  },
  upcomingTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
    marginTop: 2,
  },
  upcomingDescription: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
  },
});
