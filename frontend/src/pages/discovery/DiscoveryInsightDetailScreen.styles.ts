import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  titleSection: {
    marginTop: spacing[4],
  },
  categoryBadge: {
    alignSelf: 'flex-start',
    backgroundColor: colors.goal.savingCharacterBackground,
    borderRadius: radius.round,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  categoryBadgeText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
  title: {
    ...typography.headline,
    color: colors.textPrimary,
    lineHeight: 34,
    marginTop: spacing[4],
  },
  adviceSection: {
    alignItems: 'center',
    flexDirection: 'row',
    marginTop: spacing[6],
  },
  adviceImage: {
    height: 104,
    width: 90,
  },
  adviceBubble: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    flex: 1,
    marginLeft: spacing[2],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  adviceText: {
    ...typography.body1,
    color: colors.textPrimary,
    lineHeight: 21,
  },
  disclaimer: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: spacing[2],
  },
  coachSection: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.card,
    marginTop: spacing[6],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  coachLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    marginBottom: spacing[2],
  },
  recommendationSection: {
    marginTop: spacing[6],
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
  },
  sectionDescription: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
    marginTop: spacing[2],
  },
  recommendationList: {
    gap: spacing[2],
    marginTop: spacing[4],
  },
  recommendationCard: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderColor: colors.primary.green,
    borderRadius: radius.card,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    minHeight: 72,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  recommendedCard: {
    borderWidth: 1,
  },
  recommendationCopy: {
    flex: 1,
  },
  recommendationExplanation: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    marginTop: spacing[2],
    padding: spacing[4],
  },
  recommendationTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  recommendationDetail: {
    ...typography.body2,
    color: colors.textTertiary,
    lineHeight: 19,
    marginTop: spacing[2],
  },
  recommendedBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    marginLeft: spacing[3],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  recommendedBadgeText: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  emptyState: {
    alignItems: 'center',
    paddingTop: semanticSpacing.screenTitleTop,
  },
  emptyTitle: {
    ...typography.title,
    color: colors.textPrimary,
  },
  emptyAction: {
    ...typography.body1,
    color: colors.primary.green,
    marginTop: spacing[5],
  },
});
