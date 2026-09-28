import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderTopColor: colors.home.goalProgressTrack,
    borderTopWidth: 1,
    borderRadius: radius.card,
    elevation: 3,
    minHeight: 202,
    padding: spacing[4],
    shadowColor: colors.home.goalCardShadow,
    shadowOffset: { height: -2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 6,
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  goalIconBackground: {
    alignItems: 'center',
    borderRadius: radius.round,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  goalIcon: {
    height: 26,
    width: 26,
  },
  goalIdentity: {
    flex: 1,
    marginLeft: spacing[2],
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.body1,
    lineHeight: 19,
  },
  dueDate: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  statusRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 76,
  },
  statusCopy: {
    flex: 1,
  },
  dDay: {
    color: colors.textPrimary,
    fontFamily: fontFamily.bold,
    fontSize: 36,
    letterSpacing: -1,
    lineHeight: 44,
  },
  amountRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
  },
  currentAmount: {
    fontFamily: fontFamily.bold,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  targetAmount: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 18,
  },
  reducingLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 18,
  },
  reducingAmount: {
    color: colors.primary.brown,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.caption,
    lineHeight: 18,
  },
  characterImage: {
    height: 76,
    marginRight: spacing[2],
    transform: [{ translateY: -6 }],
    width: 76,
  },
  progressRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
  },
  progressTrack: {
    backgroundColor: colors.home.goalProgressTrack,
    borderRadius: radius.round,
    flex: 1,
    height: 8,
    overflow: 'hidden',
  },
  progressFill: {
    borderRadius: radius.round,
    height: '100%',
  },
  progressPercent: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
    width: 32,
  },
  insightBanner: {
    alignItems: 'center',
    borderRadius: radius.default,
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
    minHeight: 36,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  leafIcon: {
    height: 18,
    width: 18,
  },
  messageText: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  chevron: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.subtitle,
    lineHeight: 20,
  },
});
