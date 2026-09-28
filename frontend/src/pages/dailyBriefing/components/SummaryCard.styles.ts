import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  summaryCard: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 124,
    overflow: 'hidden',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  /** 예전에는 왼쪽 여백을 음수로 깎아 카드 모서리에 붙어 있었다. 카드 패딩 안에 세운다. */
  summaryCharacterFrame: {
    height: 80,
    marginRight: spacing[3],
    position: 'relative',
    width: 80,
  },
  summaryCharacter: {
    height: '100%',
    width: '100%',
  },
  summaryCharacterCornerMask: {
    backgroundColor: colors.briefing.card,
    bottom: 0,
    height: 12,
    position: 'absolute',
    right: 0,
    width: 12,
  },
  summaryContent: {
    flex: 1,
    minWidth: 0,
  },
  summaryHeadingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. 아래 금액이 시선을 가져가게 한다. */
  summaryLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  /**
   * 테두리 없이 배경만 쓴다. 연한 배경 + 진한 테두리 + 진한 글씨는 작은 배지에서 세 겹으로 겹쳐 답답하다.
   * 색은 리포트 지표와 같은 규칙을 따라, 지출이 늘면 주황·줄면 초록이다.
   */
  statusBadge: {
    alignSelf: 'flex-start',
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 3,
  },
  statusBadgeGood: {
    backgroundColor: colors.briefing.statusBackground,
  },
  statusBadgeBad: {
    backgroundColor: colors.briefing.decreaseTag,
  },
  statusBadgeText: {
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
  statusBadgeTextGood: {
    color: colors.briefing.statusText,
  },
  statusBadgeTextBad: {
    color: colors.briefing.increaseText,
  },
  summaryAmount: {
    color: colors.primary.brown,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.headline,
    letterSpacing: -0.6,
    lineHeight: 34,
    marginTop: spacing[2],
  },
  summaryAmountUnit: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
  },
  averageText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    lineHeight: 16,
  },
});
