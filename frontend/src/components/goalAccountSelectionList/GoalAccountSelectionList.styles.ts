import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  sectionTitle: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    marginBottom: spacing[3],
    color: colors.textPrimary,
  },
  accountList: {
    gap: spacing[2],
  },
  accountCard: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.default,
    backgroundColor: colors.white,
    overflow: 'hidden',
  },
  selectedAccountCard: {
    borderColor: colors.primary.green,
  },
  accountRow: {
    minHeight: componentSize.bottomSheet.optionMinHeight,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[4],
    gap: spacing[3],
  },
  pressedRow: {
    backgroundColor: colors.background,
  },
  bankLogo: {
    width: componentSize.field.selectorHeight,
    height: componentSize.field.selectorHeight,
    resizeMode: 'contain',
  },
  accountInfo: {
    flex: 1,
    minWidth: 0,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[2],
  },
  bankName: {
    ...typography.body1,
    fontFamily: fontFamily.medium,
    color: colors.textPrimary,
  },
  accountNumber: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  checkCircle: {
    width: spacing[5],
    height: spacing[5],
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.round,
    backgroundColor: colors.background,
  },
  selectedCheckCircle: {
    backgroundColor: colors.primary.green,
  },
  checkmark: {
    ...typography.caption,
    color: colors.white,
    opacity: 0,
  },
  selectedCheckmark: {
    opacity: 1,
  },
});
