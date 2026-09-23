import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    marginTop: spacing[4],
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing[2],
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
    minHeight: 64,
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
  /** 목표 통장이 없을 때 무엇을 해야 하는지 알려 주는 자리. */
  transferNotice: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.card,
    gap: spacing[2],
    marginTop: spacing[2],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  transferNoticeText: {
    ...typography.body2,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  transferNoticeButton: {
    backgroundColor: colors.white,
    borderRadius: radius.round,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  transferNoticeButtonLabel: {
    ...typography.body2,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  /** 보내기·되가져오기. 실제 돈이 움직이는 입구라 카드 아래에 크게 둔다. */
  transferActions: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[2],
  },
  transferButton: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderColor: colors.primary.green,
    borderRadius: radius.default,
    borderWidth: 1,
    flex: 1,
    paddingVertical: spacing[3],
  },
  transferButtonPrimary: {
    backgroundColor: colors.primary.green,
  },
  transferButtonDisabled: {
    backgroundColor: colors.ledger.softSurface,
    borderColor: colors.divider,
  },
  transferButtonLabel: {
    ...typography.body1,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  transferButtonLabelPrimary: {
    color: colors.white,
  },
  transferButtonLabelDisabled: {
    color: colors.textTertiary,
  },
});