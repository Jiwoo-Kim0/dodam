import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    marginTop: spacing[6],
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing[3],
  },
  title: {
    ...typography.body1,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  refreshLabel: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  stateBox: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[2],
    justifyContent: 'center',
    minHeight: 84,
    padding: spacing[4],
  },
  stateText: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  errorText: {
    ...typography.body2,
    color: colors.ledger.expense,
    textAlign: 'center',
  },
  retryButton: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  retryLabel: {
    ...typography.caption,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  list: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    overflow: 'hidden',
    paddingHorizontal: spacing[4],
  },
  item: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 68,
    paddingVertical: spacing[3],
  },
  itemBorder: {
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  itemCopy: {
    flex: 1,
    minWidth: 0,
  },
  itemTitle: {
    ...typography.body1,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  itemSummary: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: spacing[1],
  },
  chevron: {
    ...typography.subtitle,
    color: colors.textTertiary,
    marginLeft: spacing[3],
  },
  pressed: {
    opacity: 0.65,
  },
});
