import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

const TRACK_HEIGHT = 6;
const DOT_SIZE = 12;

export const styles = StyleSheet.create({
  list: {
    gap: spacing[3],
  },
  row: {
    gap: spacing[1],
  },
  rowHeader: {
    alignItems: 'baseline',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  rowLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  rowValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.2,
  },
  track: {
    backgroundColor: colors.background,
    borderRadius: radius.round,
    height: TRACK_HEIGHT,
    // 점이 트랙 위아래로 걸치므로 잘리지 않도록 여백을 준다.
    marginVertical: (DOT_SIZE - TRACK_HEIGHT) / 2,
    overflow: 'visible',
  },
  /** 또래 25~75분위. 이 구간 안이면 또래 절반과 비슷하다는 뜻이다. */
  peerBand: {
    backgroundColor: colors.report.chartBar,
    borderRadius: radius.round,
    bottom: 0,
    position: 'absolute',
    top: 0,
  },
  medianTick: {
    backgroundColor: colors.border,
    bottom: -2,
    marginLeft: -0.5,
    position: 'absolute',
    top: -2,
    width: 1,
  },
  userDot: {
    backgroundColor: colors.primary.green,
    borderColor: colors.white,
    borderRadius: DOT_SIZE / 2,
    borderWidth: 2,
    height: DOT_SIZE,
    marginLeft: -DOT_SIZE / 2,
    position: 'absolute',
    top: (TRACK_HEIGHT - DOT_SIZE) / 2,
    width: DOT_SIZE,
  },
  rowCaption: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
});
