import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    marginTop: spacing[6],
  },
  title: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    marginBottom: spacing[3],
    color: colors.textPrimary,
  },
  card: {
    overflow: 'hidden',
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  categoryRow: {
    minHeight: spacing[12],
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[4],
  },
  categoryDivider: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.divider,
  },
  categoryIcon: {
    width: spacing[8],
    height: spacing[8],
    marginRight: spacing[3],
  },
  categoryLabel: {
    ...typography.body1,
    flex: 1,
    color: colors.textPrimary,
  },
  amount: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    marginLeft: spacing[3],
    color: colors.textPrimary,
  },
});
