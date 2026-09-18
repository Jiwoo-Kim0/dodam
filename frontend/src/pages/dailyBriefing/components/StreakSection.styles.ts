import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  streakSection: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /** 흰 카드 안에 회색 상자를 또 두던 구조를 없애고, 같은 화면의 다른 카드와 같은 행 목록으로 맞춘다. */
  streakList: {
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  streakDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[1],
  },
  streakRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[2],
  },
  streakIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    height: 34,
    justifyContent: 'center',
    width: 34,
  },
  streakIcon: {
    height: 20,
    width: 20,
  },
  streakLabel: {
    color: colors.textSecondary,
    flex: 1,
    flexShrink: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  streakValue: {
    color: colors.primary.green,
    flexShrink: 0,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    letterSpacing: -0.3,
    lineHeight: 20,
  },
});
