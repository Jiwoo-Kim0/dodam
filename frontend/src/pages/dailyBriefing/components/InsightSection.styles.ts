import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  insightSection: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /**
   * 예전에는 카드 두 개를 가로로 나눠 놓아, 각 카드가 화면 절반 폭이라 제목이 단어 중간에서 끊겼다.
   * 세로로 펴서 폭을 다 쓰고, 카드 안에 카드를 두던 테두리도 없앴다.
   */
  insightList: {
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  insightDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[1],
  },
  insightRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[2],
  },
  insightIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  insightIcon: {
    height: 24,
    width: 24,
  },
  insightTextContent: {
    flex: 1,
    minWidth: 0,
  },
  insightHeadingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
  },
  insightTitle: {
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.3,
    lineHeight: 19,
  },
  /** 소비가 늘면 주황, 줄면 초록이다. 요약 카드의 상태 배지와 같은 규칙을 쓴다. */
  insightTag: {
    borderRadius: radius.round,
    flexShrink: 0,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  insightTagDecrease: {
    backgroundColor: colors.briefing.statusBackground,
  },
  insightTagIncrease: {
    backgroundColor: colors.briefing.decreaseTag,
  },
  insightTagText: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  insightTagTextDecrease: {
    color: colors.briefing.statusText,
  },
  insightTagTextIncrease: {
    color: colors.briefing.increaseText,
  },
  insightDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
    marginTop: 2,
  },
});
