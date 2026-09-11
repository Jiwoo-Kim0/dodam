import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    marginTop: spacing[6],
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing[3],
  },
  title: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    color: colors.textPrimary,
  },
  manageButton: {
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
    borderRadius: radius.round,
        backgroundColor: colors.divider,
  },
  manageButtonPressed: {
    opacity: 0.7,
  },
  manageButtonLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  card: {
    overflow: 'hidden',
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  accountRow: {
    minHeight: spacing[20],
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[4],
  },
  accountDivider: {
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.divider,
  },
  logo: {
    width: spacing[8],
    height: spacing[8],
    marginRight: spacing[3],
  },
  accountInformation: {
    flex: 1,
    minWidth: 0,
    gap: spacing[1],
  },
  bankName: {
    ...typography.body1,
    fontFamily: fontFamily.medium,
    color: colors.textPrimary,
  },
  productName: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  amount: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    marginLeft: spacing[3],
    color: colors.textPrimary,
  },
});
