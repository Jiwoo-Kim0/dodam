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
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    gap: spacing[2],
    marginTop: spacing[6],
    padding: spacing[4],
  },
  adviceHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  adviceImage: {
    height: 64,
    width: 64,
  },
  spendingAdviceSection: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[6],
  },
  spendingAdviceImage: {
    height: 112,
    width: 104,
    zIndex: 1,
  },
  spendingSpeechBubble: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    flex: 1,
    justifyContent: 'center',
    minHeight: 96,
    padding: spacing[4],
  },
  spendingSpeechTail: {
    backgroundColor: colors.primary.lightGreen,
    height: 16,
    left: -8,
    marginTop: -8,
    position: 'absolute',
    top: '50%',
    transform: [{ rotate: '45deg' }],
    width: 16,
  },
  spendingAdviceText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 28,
  },
  spendingAdviceAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.button,
  },
  adviceHeaderCopy: {
    flex: 1,
    gap: spacing[1],
  },
  adviceEyebrow: {
    ...typography.caption,
    color: colors.primary.green,
  },
  adviceHeading: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  adviceText: {
    ...typography.body1,
    color: colors.textPrimary,
    lineHeight: 22,
  },
  disclaimerSection: {
    borderColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    marginTop: spacing[4],
    paddingTop: spacing[3],
  },
  disclaimer: {
    ...typography.caption,
    color: colors.textTertiary,
    lineHeight: 17,
  },
  coachSection: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.card,
    gap: spacing[2],
    marginTop: spacing[6],
    padding: spacing[4],
  },
  coachLabel: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
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
    backgroundColor: colors.white,
    borderColor: colors.divider,
    borderRadius: radius.card,
    borderWidth: StyleSheet.hairlineWidth,
    gap: spacing[3],
    padding: spacing[4],
  },
  recommendedCard: {
    borderColor: colors.primary.green,
    borderWidth: 1,
  },
  recommendationHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  recommendationExplanation: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    marginTop: spacing[2],
    padding: spacing[4],
  },
  recommendationTitle: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 21,
  },
  recommendationDetail: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  recommendedBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
  },
  recommendedBadgeText: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
  },
  savingRow: {
    alignItems: 'center',
    borderColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingTop: spacing[3],
  },
  savingLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  savingValue: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
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
