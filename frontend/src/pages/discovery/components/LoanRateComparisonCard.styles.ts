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
  loanLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
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
    fontSize: fontSize.title,
    letterSpacing: -0.6,
    lineHeight: 30,
  },
  rateDifference: {
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
    width: 62,
  },
  barTrack: {
    backgroundColor: colors.home.goalProgressTrack,
    borderRadius: radius.round,
    flex: 1,
    height: 8,
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
    ...typography.body2,
    color: colors.textPrimary,
    minWidth: 58,
    textAlign: 'right',
  },
  balanceNote: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    marginTop: spacing[2],
  },
});
