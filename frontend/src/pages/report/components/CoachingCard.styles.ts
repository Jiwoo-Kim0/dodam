import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  coachingCard: {
    backgroundColor: colors.report.patternSurface,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  coachingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  character: {
    height: 56,
    width: 56,
  },
  message: {
    ...typography.body2,
    color: colors.textPrimary,
    flex: 1,
    lineHeight: 20,
    minWidth: 0,
  },
  disclaimer: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 15,
    marginTop: spacing[3],
  },
});
