import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  patternCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  patternList: {
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  patternDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[1],
  },
  patternRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[2],
  },
  /**
   * 아이콘 배경이 상태 표시를 겸한다. 기본은 좋은 상태, 주의가 필요하면 노란 배경이다.
   * 자료가 없을 때는 색을 빼서 '위험 없음'과 섞이지 않게 한다.
   */
  patternIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  patternIconFrameCheck: {
    backgroundColor: colors.report.checkBadge,
  },
  patternIconFrameUnavailable: {
    backgroundColor: colors.ledger.softSurface,
  },
  /** 판단할 자료가 없는 줄. 결론이 아니라 빈 상태라 글자도 한 단계 낮춘다. */
  patternIconUnavailable: {
    opacity: 0.45,
  },
  patternIcon: {
    height: 24,
    width: 24,
  },
  patternTextContent: {
    flex: 1,
    minWidth: 0,
  },
  patternTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.3,
    lineHeight: 19,
  },
  patternDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
    marginTop: 2,
  },
  /** 판정 근거 기간. 결론이 아니라 각주라 가장 낮은 단계로 둔다. */
  patternPeriod: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    marginTop: spacing[1],
  },
});
