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
  description: {
    ...typography.body1,
    color: colors.textTertiary,
    marginTop: spacing[2],
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
  resultSection: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    marginTop: spacing[8],
  },
  resultCard: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    flex: 1,
    padding: spacing[4],
  },
  resultTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[3],
  },
  resultRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing[2],
  },
  resultLabel: {
    ...typography.body2,
    color: colors.textPrimary,
  },
  savingValue: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  dayValue: {
    color: colors.primary.brown,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  noticeBox: {
    alignItems: 'flex-start',
    backgroundColor: colors.goal.savingCharacterBackground,
    borderColor: colors.primary.brown,
    borderRadius: radius.default,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    marginTop: spacing[2],
    padding: spacing[2],
  },
  noticeIcon: {
    color: colors.primary.brown,
    fontSize: fontSize.body1,
    marginRight: spacing[2],
  },
  noticeText: {
    ...typography.caption,
    color: colors.primary.brown,
    flex: 1,
    lineHeight: 17,
  },
  resultImage: {
    height: 92,
    marginLeft: -spacing[2],
    width: 76,
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
