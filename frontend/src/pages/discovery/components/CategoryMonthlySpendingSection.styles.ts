import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    gap: spacing[1],
    marginTop: spacing[8],
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
  },
  description: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    marginTop: spacing[3],
    overflow: 'hidden',
    paddingHorizontal: spacing[4],
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[4],
  },
  rowDivider: {
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  iconChip: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  icon: {
    height: 24,
    width: 24,
  },
  merchantCopy: {
    flex: 1,
    gap: spacing[1],
  },
  merchantName: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  count: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
  },
  stateContent: {
    gap: spacing[2],
  },
  stateText: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 20,
    paddingVertical: spacing[5],
  },
  retryRow: {
    paddingBottom: spacing[5],
  },
  retryText: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
});
