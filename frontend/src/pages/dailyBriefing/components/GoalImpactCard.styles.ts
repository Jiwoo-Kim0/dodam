import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  goalCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /**
   * 예전에는 세 항목을 가로 3칸으로 나눠, 한 칸이 96px뿐이라 `이번 달 예상 절약` 같은 라벨이
   * 잘렸다. 세로로 펴서 라벨은 왼쪽, 값은 오른쪽에 정렬한다.
   */
  goalList: {
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  goalDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[1],
  },
  goalRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[2],
  },
  goalIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  goalIcon: {
    height: 20,
    width: 20,
  },
  goalLabel: {
    color: colors.textSecondary,
    flex: 1,
    flexShrink: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  goalValue: {
    color: colors.textPrimary,
    flexShrink: 0,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    letterSpacing: -0.3,
    lineHeight: 20,
  },
  /** 목표보다 빠르면 좋은 소식, 늦으면 주의 신호다. 리포트 지표와 같은 색 규칙을 쓴다. */
  goalValueGood: {
    color: colors.primary.green,
  },
  goalValueBad: {
    color: colors.primary.orange,
  },
});
