import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    gap: spacing[1],
    marginTop: spacing[8],
  },
  /** 상세 화면의 다른 섹션 제목과 같은 크기다. 18px은 본문 대비 너무 컸다. */
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
    paddingVertical: spacing[3],
  },
  rowDivider: {
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  iconChip: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    height: 38,
    justifyContent: 'center',
    width: 38,
  },
  icon: {
    height: 20,
    width: 20,
  },
  merchantCopy: {
    flex: 1,
    minWidth: 0,
  },
  merchantName: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  count: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
    marginTop: 1,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    letterSpacing: -0.3,
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
  /** 브리핑 결제 내역의 펼치기와 같은 형태다. */
  expandButton: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    height: 40,
    justifyContent: 'center',
  },
  expandButtonPressed: {
    opacity: 0.62,
  },
  expandButtonText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
});
