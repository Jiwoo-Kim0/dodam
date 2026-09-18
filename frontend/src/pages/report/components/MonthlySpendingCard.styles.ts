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

/** 막대가 그려지는 세로 영역이다. 평균선 위치 계산도 이 높이를 기준으로 한다. */
export const BAR_AREA_HEIGHT = 84;
/** 월 라벨이 차지하는 높이(위 여백 + 줄 높이)로, 평균선을 막대 바닥에 맞출 때 쓴다. */
export const MONTH_LABEL_BLOCK_HEIGHT = 22;

export const styles = StyleSheet.create({
  spendingCard: {
    padding: spacing[4],
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  headerCaption: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  heroValueRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: 2,
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  heroValue: {
    ...typography.headline,
    color: colors.textPrimary,
    letterSpacing: -0.6,
    lineHeight: 32,
  },
  heroValueUnit: {
    ...typography.body1,
    color: colors.textPrimary,
  },
  /** 지출이 평균보다 늘어난 방향은 주요 금융 지표의 `bad` 색과 같게 쓴다. */
  heroComparison: {
    ...typography.caption,
    lineHeight: 18,
    marginTop: 2,
  },
  heroComparisonBad: {
    color: colors.primary.orange,
  },
  heroComparisonGood: {
    color: colors.primary.green,
  },
  heroComparisonNeutral: {
    color: colors.textTertiary,
  },
  /** 선택한 달의 금액이 뜨는 자리다. 비어 있을 때도 높이를 차지해 차트가 흔들리지 않는다. */
  selectionSlot: {
    height: 18,
    justifyContent: 'center',
    marginTop: spacing[3],
  },
  selectionText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    lineHeight: 18,
  },
  selectionMonth: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
  },
  chart: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[2],
    position: 'relative',
  },
  column: {
    alignItems: 'center',
    flex: 1,
  },
  barTrack: {
    alignItems: 'center',
    height: BAR_AREA_HEIGHT,
    justifyContent: 'flex-end',
    width: '100%',
  },
  /** 폭을 고정해 얇게 세운다. 열 너비를 다 채우면 막대가 아니라 블록처럼 보인다. */
  bar: {
    backgroundColor: colors.report.chartBar,
    borderTopLeftRadius: radius.small,
    borderTopRightRadius: radius.small,
    minHeight: 2,
    width: 18,
  },
  barReportMonth: {
    backgroundColor: colors.primary.green,
  },
  /** 색을 바꾸지 않고 한 단계씩 진하게만 한다. 회색은 진한 회색, 초록은 진한 초록이다. */
  barSelected: {
    backgroundColor: colors.report.chartBarStrong,
  },
  barReportMonthSelected: {
    backgroundColor: colors.report.scoreStrong,
  },
  monthLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    marginTop: spacing[2],
  },
  monthLabelReport: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  monthLabelSelected: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  /** 막대 뒤에 깔리는 기준선이다. 라벨은 차트 밖 범례로 빼서 막대와 겹치지 않게 했다. */
  averageLine: {
    backgroundColor: colors.divider,
    height: 1,
    left: 0,
    position: 'absolute',
    right: 0,
  },
  legend: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  legendLine: {
    backgroundColor: colors.divider,
    height: 1,
    width: 12,
  },
  legendLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginTop: spacing[3],
  },
  sectionLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
    marginTop: spacing[4],
  },
  categoryList: {
    gap: spacing[3],
    marginTop: spacing[3],
  },
  categoryRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  categoryIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 32,
    justifyContent: 'center',
    width: 32,
  },
  categoryIcon: {
    height: 18,
    width: 18,
  },
  categoryContent: {
    flex: 1,
    gap: spacing[1],
    minWidth: 0,
  },
  categoryTextRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  categoryName: {
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  categoryAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  categoryBarTrack: {
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 5,
    overflow: 'hidden',
    width: '100%',
  },
  categoryBar: {
    backgroundColor: colors.primary.green,
    height: '100%',
  },
  /** 1위만 진하게 두고 나머지는 옅게 해서 순위가 읽히게 한다. */
  categoryBarMuted: {
    backgroundColor: colors.report.scoreLight,
  },
  categoryShare: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    minWidth: 26,
    textAlign: 'right',
  },
});
