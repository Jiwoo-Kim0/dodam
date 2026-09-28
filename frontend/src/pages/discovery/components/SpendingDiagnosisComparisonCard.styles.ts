import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[1],
    marginTop: spacing[6],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  /** 다른 화면과 같은 카드 제목 규칙이다. */
  categoryLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  amountRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    letterSpacing: -0.6,
    lineHeight: 30,
  },
  changePercent: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  bars: {
    gap: spacing[2],
    marginTop: spacing[3],
  },
  barRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  barLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    width: 56,
  },
  barTrack: {
    backgroundColor: colors.home.goalProgressTrack,
    borderRadius: radius.round,
    flex: 1,
    height: 8,
    overflow: 'hidden',
  },
  previousBar: {
    backgroundColor: colors.report.scoreLight,
    borderRadius: radius.round,
    height: '100%',
  },
  recentBar: {
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: '100%',
  },
  barAmount: {
    ...typography.body2,
    color: colors.textPrimary,
    minWidth: 72,
    textAlign: 'right',
  },
});
