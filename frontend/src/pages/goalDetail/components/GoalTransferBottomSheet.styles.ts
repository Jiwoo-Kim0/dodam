import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  backdrop: {
    backgroundColor: colors.overlay,
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.sheet,
    borderTopRightRadius: radius.sheet,
    /** 계좌가 여러 개여도 시트가 화면을 다 덮지 않게 한다. */
    maxHeight: componentSize.bottomSheet.maxHeight,
    paddingBottom: spacing[4],
    paddingHorizontal: spacing[5],
  },
  handle: {
    alignSelf: 'center',
    backgroundColor: colors.divider,
    borderRadius: radius.round,
    height: 4,
    marginTop: spacing[2],
    width: 36,
  },
  title: {
    ...typography.title,
    color: colors.textPrimary,
    marginTop: spacing[3],
  },
  body: {
    marginTop: spacing[3],
  },
  fieldLabel: {
    ...typography.body2,
    color: colors.textSecondary,
    marginBottom: spacing[2],
    marginTop: spacing[3],
  },
  /** 고를 수 없는 쪽. 선택지와 달리 테두리를 주지 않아 누를 수 없음을 드러낸다. */
  fixedAccount: {
    alignItems: 'center',
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.default,
    flexDirection: 'row',
    gap: spacing[2],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[3],
  },
  accountOptions: {
    gap: spacing[2],
  },
  accountOption: {
    alignItems: 'center',
    borderColor: colors.divider,
    borderRadius: radius.default,
    borderWidth: 1,
    flexDirection: 'row',
    gap: spacing[2],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[3],
  },
  accountOptionSelected: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.primary.green,
  },
  logo: {
    height: 28,
    resizeMode: 'contain',
    width: 28,
  },
  accountText: {
    flex: 1,
    flexShrink: 1,
  },
  accountName: {
    ...typography.body1,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  accountNumber: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: 2,
  },
  accountBalance: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  emptyText: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  amountRow: {
    alignItems: 'center',
    borderBottomColor: colors.border,
    borderBottomWidth: 1,
    flexDirection: 'row',
    gap: spacing[2],
    paddingBottom: spacing[2],
  },
  /** 잔액을 넘으면 입력줄에서 바로 알린다. 버튼만 꺼두면 이유를 알 수 없다. */
  amountRowError: {
    borderBottomColor: colors.primary.orange,
  },
  amountInput: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.headline,
    padding: 0,
    textAlign: 'right',
  },
  amountUnit: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.subtitle,
  },
  helperText: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing[2],
    textAlign: 'right',
  },
  helperTextError: {
    color: colors.primary.orange,
  },
  quickAmounts: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  quickAmount: {
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.round,
    flex: 1,
    paddingVertical: spacing[2],
  },
  quickAmountLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    textAlign: 'center',
  },
  errorText: {
    ...typography.body2,
    color: colors.primary.orange,
    lineHeight: 19,
    marginTop: spacing[3],
  },
  submitButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    justifyContent: 'center',
    marginTop: spacing[4],
    minHeight: componentSize.button.compact,
  },
  submitButtonDisabled: {
    backgroundColor: colors.disabledBackground,
  },
  submitLabel: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
