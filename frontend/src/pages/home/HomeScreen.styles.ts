import { StyleSheet } from 'react-native';

import { colors, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingBottom: spacing[5],
  },
  goalSection: {
    overflow: 'hidden',
    paddingBottom: spacing[4],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  goalSectionBackground: StyleSheet.absoluteFill,
  treeIllustration: {
    left: 0,
    position: 'absolute',
    top: 0,
  },
  greeting: {
    marginTop: spacing[2],
  },
  greetingText: {
    ...typography.body1,
    color: colors.textSecondary,
  },
  greetingHeadline: {
    ...typography.headline,
    color: colors.textPrimary,
    letterSpacing: -0.8,
    lineHeight: 32,
    marginTop: spacing[1],
  },
  body: {
    gap: spacing[5],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[4],
  },
});
