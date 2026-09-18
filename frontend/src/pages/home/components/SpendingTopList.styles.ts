import { StyleSheet } from 'react-native';

import { colors, componentSize, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    minHeight: 236,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  cardPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
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
  rankBadge: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: 20,
    justifyContent: 'center',
    width: 20,
  },
  secondaryRankBadge: {
    backgroundColor: colors.home.secondaryRank,
  },
  rankLabel: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  categoryLabel: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 18,
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
