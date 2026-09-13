import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

const CHART_SIZE = 80;
/** 링 굵기(19)를 뺀 안쪽 지름과 맞춘다. */
const CENTER_SIZE = 42;

export const styles = StyleSheet.create({
  chartFrame: {
    alignItems: 'center',
    height: CHART_SIZE,
    justifyContent: 'center',
    marginLeft: spacing[2],
    width: CHART_SIZE,
  },
  center: {
    alignItems: 'center',
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.round,
    height: CENTER_SIZE,
    justifyContent: 'center',
    position: 'absolute',
    width: CENTER_SIZE,
  },
  centerLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 15,
  },
  centerValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 18,
  },
});
