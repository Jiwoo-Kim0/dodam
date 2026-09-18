import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, shadows, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    ...shadows.softCard,
    backgroundColor: colors.white,
    borderRadius: radius.card,
    padding: spacing[3],
  },
  cardPressed: {
    opacity: 0.65,
  },
  badgeRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  rankBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 2,
  },
  rankBadgeText: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.micro,
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
    fontSize: fontSize.micro,
  },
  titleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    marginTop: spacing[3],
  },
  iconFrame: {
    alignItems: 'center',
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  icon: {
    height: 22,
    width: 22,
  },
  title: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 20,
    minWidth: 0,
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
    fontSize: fontSize.detail,
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
    fontSize: fontSize.detail,
  },
  chevron: {
    marginLeft: spacing[1],
  },
});
