import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.white,
  },
  scroll: {
    backgroundColor: colors.background,
  },
  scrollContent: {
    backgroundColor: colors.background,
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
    marginTop: spacing[3],
  },
  greetingText: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    color: colors.textSecondary,
    lineHeight: 21,
  },
  greetingName: {
    color: colors.primary.brown,
    fontFamily: fontFamily.semiBold,
  },
  greetingHeadline: {
    ...typography.headline,
    color: colors.textPrimary,
    letterSpacing: -0.8,
    lineHeight: 32,
    marginTop: spacing[2],
  },
  body: {
    gap: spacing[4],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[4],
  },
});
