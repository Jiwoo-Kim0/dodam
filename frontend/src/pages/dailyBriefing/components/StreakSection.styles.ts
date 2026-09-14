import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  streakSection: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
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
    minHeight: 72,
    justifyContent: 'center',
    padding: spacing[2],
  },
  streakLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  streakValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 20,
    marginTop: spacing[1],
  },
});
