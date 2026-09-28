import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  section: {
    marginTop: spacing[8],
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    padding: spacing[4],
  },
  /** 상세 화면의 다른 섹션 제목과 같은 크기다. */
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 21,
  },
  description: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
    marginTop: spacing[2],
  },
  field: {
    marginTop: spacing[4],
  },
  fieldLabel: {
    ...typography.body2,
    color: colors.textSecondary,
    marginBottom: spacing[2],
  },
  fieldHint: {
    ...typography.caption,
    color: colors.textTertiary,
    lineHeight: 17,
    marginBottom: spacing[2],
    marginTop: -spacing[1],
  },
  amountInputRow: {
    alignItems: 'center',
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.default,
    flexDirection: 'row',
    paddingHorizontal: spacing[3],
  },
  amountInput: {
    ...typography.subtitle,
    color: colors.textPrimary,
    flex: 1,
    minHeight: 48,
    paddingVertical: spacing[2],
    textAlign: 'right',
  },
  amountUnit: {
    ...typography.body1,
    color: colors.textPrimary,
    marginLeft: spacing[1],
  },
  durationRow: {
    flexDirection: 'row',
    gap: spacing[2],
  },
  durationButton: {
    alignItems: 'center',
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.round,
    flex: 1,
    justifyContent: 'center',
    minHeight: 38,
  },
  durationButtonSelected: {
    backgroundColor: colors.primary.lightGreen,
  },
  durationLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  durationLabelSelected: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  errorText: {
    ...typography.caption,
    color: colors.ledger.expense,
    marginTop: spacing[2],
  },
  runButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    justifyContent: 'center',
    marginTop: spacing[4],
    minHeight: 48,
  },
  runButtonDisabled: {
    backgroundColor: colors.disabledBackground,
  },
  runButtonPressed: {
    opacity: 0.82,
  },
  runButtonLabel: {
    ...typography.buttonSmall,
    color: colors.white,
  },
  resultSection: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing[8],
    width: '100%',
  },
  resultCard: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    flexBasis: '76%',
    flexGrow: 0,
    flexShrink: 1,
    minWidth: 0,
    padding: spacing[4],
  },
  resultHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  resultTitle: {
    ...typography.body1,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  resultCharacter: {
    flexShrink: 0,
    height: 92,
    marginLeft: spacing[2],
    width: 76,
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginBottom: spacing[3],
    marginTop: spacing[2],
  },
  resultRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing[2],
  },
  resultLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    flexShrink: 1,
  },
  effectValue: {
    ...typography.body1,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    marginLeft: spacing[2],
    textAlign: 'right',
  },
  decreaseValue: {
    color: colors.ledger.expense,
  },
  goalValue: {
    ...typography.body1,
    color: colors.primary.brown,
    fontFamily: fontFamily.semiBold,
    marginLeft: spacing[2],
    textAlign: 'right',
  },
  noticeBox: {
    alignItems: 'flex-start',
    backgroundColor: colors.goal.savingCharacterBackground,
    borderColor: colors.primary.brown,
    borderRadius: radius.default,
    borderWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    marginTop: spacing[2],
    padding: spacing[2],
  },
  noticeIcon: {
    color: colors.primary.brown,
    fontSize: fontSize.body1,
    marginRight: spacing[2],
  },
  noticeText: {
    ...typography.caption,
    color: colors.primary.brown,
    flex: 1,
    lineHeight: 17,
  },
  recalculateButtonPressed: {
    opacity: 0.6,
  },
  recalculateLabel: {
    ...typography.caption,
    color: colors.textTertiary,
    fontFamily: fontFamily.medium,
  },
  pendingBadge: {
    alignSelf: 'flex-start',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    marginTop: spacing[3],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  pendingBadgeText: {
    ...typography.caption,
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
  },
});
