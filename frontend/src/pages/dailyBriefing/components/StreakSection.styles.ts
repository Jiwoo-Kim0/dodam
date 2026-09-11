import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  streakSection: {
    padding: spacing[4],
  },
  streakRow: {
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: spacing[3],
  },
  streakCard: {
    alignItems: 'center',
    backgroundColor: colors.briefing.mutedCard,
    borderRadius: radius.card,
    flex: 1,
    minHeight: 88,
    justifyContent: 'center',
    padding: spacing[3],
  },
  streakLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  streakValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
    lineHeight: 24,
    marginTop: spacing[2],
  },
});
