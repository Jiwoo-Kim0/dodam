import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing } from '@/styles';

/** 막대가 그려지는 세로 영역이다. */
export const BAR_AREA_HEIGHT = 76;
/** 월 라벨이 차지하는 높이(위 여백 + 줄 높이)로, 평균선 위치 계산에 쓴다. */
export const MONTH_LABEL_BLOCK_HEIGHT = 20;

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
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: 10,
    lineHeight: 14,
  },
  chart: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
    position: 'relative',
  },
  column: {
    alignItems: 'center',
    flex: 1,
  },
  amountLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 16,
    marginBottom: spacing[1],
  },
  amountLabelCurrent: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  barTrack: {
    height: BAR_AREA_HEIGHT,
    justifyContent: 'flex-end',
    width: '100%',
  },
  bar: {
    backgroundColor: colors.report.chartBar,
    borderRadius: radius.small,
    width: '100%',
  },
  barCurrent: {
    backgroundColor: colors.primary.green,
  },
  monthLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 16,
    marginTop: spacing[1],
  },
  monthLabelCurrent: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  averageLine: {
    height: 1,
    left: 0,
    position: 'absolute',
    right: 0,
  },
  summary: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 16,
    marginTop: spacing[2],
  },
});
