import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const CHART_WIDTH = 320;
export const CHART_HEIGHT = 190;
/** y축 금액 라벨이 들어갈 폭 */
export const PLOT_LEFT = 44;
export const PLOT_RIGHT = 312;
export const PLOT_TOP = 30;
export const PLOT_BOTTOM = 140;
/** 눈금 5개 = 구간 4개. 축 최댓값을 4로 나눠 떨어지게 잡아야 67만·133만 같은 눈금이 안 생긴다 */
export const GRID_LINE_COUNT = 5;

export const styles = StyleSheet.create({
  forecastCard: {
    paddingBottom: spacing[4],
    paddingHorizontal: spacing[4],
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  titleGroup: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: spacing[1],
  },
  unit: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
  },
  summary: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    marginTop: spacing[1],
  },
  summaryAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
  },
  headerCaption: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.caption,
  },
  chart: {
    marginTop: spacing[2],
  },
  legend: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[4],
    marginTop: spacing[1],
  },
  legendItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  legendSolid: {
    backgroundColor: colors.report.scoreStrong,
    height: 2,
    width: 14,
  },
  legendDashed: {
    borderColor: colors.report.scoreStrong,
    borderStyle: 'dashed',
    borderTopWidth: 2,
    width: 14,
  },
  legendBand: {
    backgroundColor: colors.report.forecastLiving,
    borderRadius: 2,
    height: 10,
    width: 14,
  },
  legendLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.detail,
  },
  basis: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.default,
    gap: spacing[1],
    marginTop: spacing[3],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  basisTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    marginBottom: spacing[1],
  },
  basisRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  basisLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.caption,
  },
  basisValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
  },
  basisCaption: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    marginTop: spacing[1],
  },
  note: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    lineHeight: 16,
    marginTop: spacing[2],
  },
  emptyBody: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 20,
    marginTop: spacing[2],
  },
});
