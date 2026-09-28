import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  section: {
    gap: spacing[1],
    marginTop: spacing[8],
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
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[3],
    marginTop: spacing[3],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  row: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  rowLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  rowValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  rowValueStrong: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
  },
  targetBlock: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
    paddingTop: spacing[3],
  },
  targetLabel: {
    ...typography.body2,
    color: colors.textSecondary,
    flex: 1,
  },
  targetRate: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  monthlyNote: {
    ...typography.body2,
    color: colors.textPrimary,
  },
  unavailableText: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  caption: {
    ...typography.caption,
    color: colors.textTertiary,
    lineHeight: 17,
    marginTop: spacing[2],
  },
});
