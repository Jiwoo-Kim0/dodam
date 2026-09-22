import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

const TRACK_HEIGHT = 6;
const DOT_SIZE = 12;
/** 말풍선을 점 가운데에 맞추려고 고정 폭을 주고 절반만큼 왼쪽으로 민다. */
const TOOLTIP_WIDTH = 64;

export const styles = StyleSheet.create({
  list: {
    gap: spacing[3],
  },
  row: {
    gap: spacing[2],
  },
  rowHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  /** 또래와 견준 결과. 항목 이름과 같은 줄 오른쪽에 둬 한 줄로 읽히게 한다. */
  badge: {
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 3,
  },
  /** 또래보다 나은 쪽. 앱의 긍정 색을 그대로 쓴다. */
  goodBadge: {
    backgroundColor: colors.primary.lightGreen,
  },
  /** 살펴볼 쪽. 경고가 아니라 눈여겨보라는 정도라 노란 계열로 둔다. */
  watchBadge: {
    backgroundColor: colors.report.checkBadge,
  },
  /** 또래와 비슷해 좋고 나쁨을 말하지 않는 상태. 색을 빼서 판정처럼 보이지 않게 한다. */
  neutralBadge: {
    backgroundColor: colors.ledger.softSurface,
  },
  badgeText: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  goodBadgeText: {
    color: colors.report.scoreStrong,
  },
  watchBadgeText: {
    color: colors.primary.brown,
  },
  neutralBadgeText: {
    color: colors.textTertiary,
  },
  rowLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
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
  /** 점을 눌렀을 때만 뜨는 수치. 트랙 위로 띄워 막대를 가리지 않는다. */
  tooltipAnchor: {
    alignItems: 'center',
    bottom: DOT_SIZE + 2,
    marginLeft: -(TOOLTIP_WIDTH / 2),
    position: 'absolute',
    width: TOOLTIP_WIDTH,
  },
  tooltip: {
    backgroundColor: colors.textPrimary,
    borderRadius: radius.small,
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
  },
  tooltipText: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.detail,
    letterSpacing: -0.2,
    lineHeight: 14,
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
});
