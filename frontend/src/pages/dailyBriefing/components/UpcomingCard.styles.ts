import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  upcomingCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /**
   * 예전에는 카드 제목이 아이콘 오른쪽에 들어가 있어, 이 카드만 제목 줄이 혼자 들여쓰기 된 것처럼
   * 보였다. 제목을 카드 맨 위로 빼고 그 아래에 아이콘과 내용을 둔다.
   */
  upcomingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  upcomingIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  upcomingIcon: {
    height: 20,
    width: 20,
  },
  upcomingContent: {
    flex: 1,
    minWidth: 0,
  },
  upcomingTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    letterSpacing: -0.3,
    lineHeight: 20,
  },
  upcomingDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
    marginTop: 2,
  },
});
