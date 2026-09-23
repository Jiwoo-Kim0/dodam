import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  cardShadow: {
    backgroundColor: colors.divider,
    borderRadius: radius.default,
    paddingBottom: 2,
  },
  row: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.default,
    flexDirection: 'row',
    gap: spacing[3],
    minHeight: 72,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  rowPressed: {
    opacity: 0.65,
  },
  /** 다른 화면 목록과 같은 회색 원형 프레임이다. 원색 아이콘의 채도를 눌러 준다. */
  iconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  icon: {
    height: 24,
    width: 24,
  },
  content: {
    flex: 1,
    minWidth: 0,
  },
  categoryRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  category: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
  /** K-Means 군집 비교가 근거에 섞였다는 표시다. 판정이 아니라 근거 출처라 연하게 둔다. */
  clusterBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.primary.green,
    borderRadius: radius.round,
    borderWidth: StyleSheet.hairlineWidth,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  clusterBadgeText: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.3,
    lineHeight: 18,
    marginTop: 2,
  },
  /** 금액이 10px, 라벨이 9px이라 거의 읽히지 않았다. 행의 결론에 해당하니 크기를 올린다. */
  amountChevronRow: {
    alignItems: 'center',
    flexDirection: 'row',
    flexShrink: 0,
    gap: spacing[1],
  },
  amountWrap: {
    alignItems: 'flex-end',
    flexShrink: 0,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.3,
    lineHeight: 18,
  },
  amountUnit: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
  },
  amountLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    marginTop: 1,
  },
});
