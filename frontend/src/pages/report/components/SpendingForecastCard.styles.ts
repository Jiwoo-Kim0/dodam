import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, spacing } from '@/styles';

export const CHART_WIDTH = 320;
export const CHART_HEIGHT = 190;
/** y축 금액 라벨이 들어갈 폭 */
export const PLOT_LEFT = 44;
export const PLOT_RIGHT = 312;
export const PLOT_TOP = 30;
export const PLOT_BOTTOM = 140;
export const GRID_LINE_COUNT = 4;

export const styles = StyleSheet.create({
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
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
