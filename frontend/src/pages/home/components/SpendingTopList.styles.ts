import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    minHeight: 236,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. 제목은 작고 연하게, 값이 눈에 먼저 들어오게 한다. */
  title: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    paddingVertical: spacing[3],
  },
  firstRow: {
    marginTop: 0,
  },
  separatedRow: {
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  categoryIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  categoryIcon: {
    height: 19,
    width: 19,
  },
  /** 무엇에 썼는지를 카드에서 먼저 읽게 한다. 금액과 같은 굵기로 한 단계 크게 둔다. */
  categoryLabel: {
    color: colors.textPrimary,
    flex: 1,
    flexShrink: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 22,
  },
  categoryAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  amountGroup: {
    alignItems: 'flex-end',
    gap: spacing[1],
  },
  changeGroup: {
    alignItems: 'center',
    flexDirection: 'row',
    flexShrink: 0,
    gap: 2,
  },
  changeLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  status: {
    marginVertical: spacing[4],
  },
  statusText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    marginVertical: spacing[4],
    textAlign: 'center',
  },
});
