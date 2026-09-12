import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    backgroundColor: colors.white,
    flexDirection: 'row',
    gap: spacing[1],
    minHeight: 70,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  divider: {
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  rankBadge: {
    alignItems: 'center',
    height: 28,
    justifyContent: 'center',
    position: 'relative',
    width: 24,
  },
  rankAccent: {
    borderRadius: radius.round,
    bottom: 2,
    height: spacing[2],
    position: 'absolute',
    width: 24,
  },
  rankFirst: {
    backgroundColor: colors.goal.reducingCharacterBackground,
  },
  rankSecond: {
    backgroundColor: colors.briefing.increaseTag,
  },
  rankThird: {
    backgroundColor: colors.goal.savingCharacterBackground,
  },
  rankFourth: {
    backgroundColor: colors.home.secondaryRank,
  },
  rankText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.subtitle,
    zIndex: 1,
  },
  iconFrame: {
    alignItems: 'center',
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  icon: {
    height: 36,
    width: 36,
  },
  content: {
    flex: 1,
    minWidth: 0,
  },
  category: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 14,
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 12,
    lineHeight: 16,
    marginTop: 2,
  },
  description: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 14,
    marginTop: 2,
  },
  amountChevronRow: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    flexShrink: 0,
    gap: spacing[1],
    marginTop: spacing[2],
  },
  amountWrap: {
    alignItems: 'flex-end',
    flexShrink: 0,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 10,
  },
  amountUnit: {
    fontFamily: fontFamily.regular,
    fontSize: 8,
  },
  amountLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 6,
    lineHeight: 9,
    marginTop: 2,
  },
});
