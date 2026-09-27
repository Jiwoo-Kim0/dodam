import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  radius,
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
    paddingBottom: spacing[8],
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
  reportEntryCard: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    flexDirection: 'row',
    minHeight: 76,
    paddingHorizontal: spacing[4],
  },
  reportEntryCardPressed: {
    opacity: 0.82,
  },
  reportEntryIcon: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  reportEntryCopy: {
    flex: 1,
    marginLeft: spacing[3],
  },
  reportEntryTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  reportEntryDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    marginTop: spacing[1],
  },
  reportEntryArrow: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 28,
  },
  retryBanner: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: componentSize.touchTarget,
    paddingHorizontal: spacing[4],
  },
  retryMessage: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  retryAction: {
    ...typography.buttonSmall,
    color: colors.primary.green,
  },
  retryBannerPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
