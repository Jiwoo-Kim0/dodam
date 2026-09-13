import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    elevation: 1,
    padding: spacing[3],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 3,
  },
  cardPressed: {
    opacity: 0.65,
  },
  badgeRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  rankBadge: {
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  rankFirst: {
    backgroundColor: colors.primary.brown,
  },
  rankSecond: {
    backgroundColor: colors.primary.orange,
  },
  rankThird: {
    backgroundColor: colors.primary.yellow,
  },
  rankFourth: {
    backgroundColor: colors.primary.green,
  },
  rankBadgeText: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: 10,
  },
  categoryBadge: {
    backgroundColor: colors.background,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  categoryBadgeText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: 10,
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 20,
    marginTop: spacing[2],
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[2],
  },
  amountRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  amountLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
  },
  amountValueRow: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  amountValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
  },
  amountUnit: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
  },
  chevron: {
    marginLeft: spacing[1],
  },
});
