import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[1],
    marginTop: spacing[6],
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[5],
  },
  loanLabel: {
    ...typography.body1,
    color: colors.textSecondary,
  },
  rateRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  rate: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 28,
    lineHeight: 36,
  },
  rateDifference: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  bars: {
    gap: spacing[3],
    marginTop: spacing[3],
  },
  barRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  barLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    width: 66,
  },
  barTrack: {
    backgroundColor: colors.home.goalProgressTrack,
    borderRadius: radius.round,
    flex: 1,
    height: 10,
    overflow: 'hidden',
  },
  referenceBar: {
    backgroundColor: colors.report.scoreLight,
    borderRadius: radius.round,
    height: '100%',
  },
  // 내 금리는 공시보다 높다는 판정이라 증가·주의 색을 쓴다. 공시 중앙값 막대는 기준선이라 중립 초록이다.
  currentBar: {
    backgroundColor: colors.briefing.increaseText,
    borderRadius: radius.round,
    height: '100%',
  },
  barValue: {
    ...typography.body1,
    color: colors.textPrimary,
    minWidth: 62,
    textAlign: 'right',
  },
  balanceNote: {
    ...typography.body2,
    color: colors.textSecondary,
    marginTop: spacing[2],
  },
});
