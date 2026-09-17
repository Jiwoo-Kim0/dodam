import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.background },
  content: { gap: spacing[5], padding: semanticSpacing.screenMargin, paddingBottom: spacing[12] },
  eyebrow: { ...typography.body2, color: colors.textSecondary },
  total: { ...typography.headline, marginTop: spacing[1], color: colors.textPrimary, fontFamily: fontFamily.bold },
  comparison: { ...typography.caption, marginTop: spacing[2], color: colors.textSecondary },
  chartCard: { padding: spacing[4], borderRadius: radius.card, backgroundColor: colors.white },
  tooltip: { alignSelf: 'center', alignItems: 'center', paddingHorizontal: spacing[4], paddingVertical: spacing[2], borderRadius: radius.default, backgroundColor: colors.primary.lightGreen },
  tooltipDay: { ...typography.caption, color: colors.textSecondary },
  tooltipAmount: { ...typography.body1, color: colors.primary.green, fontFamily: fontFamily.semiBold },
  legend: { flexDirection: 'row', justifyContent: 'center', gap: spacing[4] },
  currentLegend: { ...typography.caption, color: colors.primary.green },
  previousLegend: { ...typography.caption, color: colors.textTertiary },
  summaryCard: { flexDirection: 'row', padding: spacing[5], borderRadius: radius.card, backgroundColor: colors.white },
  summaryItem: { flex: 1, gap: spacing[2], alignItems: 'center' },
  summaryDivider: { width: StyleSheet.hairlineWidth, backgroundColor: colors.divider },
  summaryLabel: { ...typography.caption, color: colors.textSecondary },
  summaryValue: { ...typography.body2, color: colors.textPrimary, fontFamily: fontFamily.semiBold, textAlign: 'center' },
  categoryCard: { gap: spacing[4], padding: spacing[5], borderRadius: radius.card, backgroundColor: colors.white },
  categoryTitle: { ...typography.subtitle, color: colors.textPrimary, fontFamily: fontFamily.semiBold },
  categoryRow: { flexDirection: 'row', alignItems: 'center', gap: spacing[3] },
  categoryBody: { flex: 1, gap: spacing[2] },
  categoryHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  categoryNameRow: { flexDirection: 'row', alignItems: 'baseline', gap: spacing[2] },
  categoryName: { ...typography.body2, color: colors.textPrimary, fontFamily: fontFamily.medium },
  categoryRatio: { ...typography.caption, color: colors.textTertiary },
  categoryAmount: { ...typography.body2, color: colors.textPrimary, fontFamily: fontFamily.medium },
  categoryTrack: { height: 5, overflow: 'hidden', borderRadius: radius.round, backgroundColor: colors.ledger.categoryTrack },
  categoryFill: { height: '100%', borderRadius: radius.round },
});
