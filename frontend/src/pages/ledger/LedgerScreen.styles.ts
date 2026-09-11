import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

const ledgerColors = {
  card: '#FFFFFF',
  softSurface: '#F8F8F8',
  calendarMuted: '#C9C9C9',
  expense: '#C84B16',
  income: '#4E79FF',
  selectedDay: '#E4E4E4',
  selectedTab: '#F4F7E5',
  logoPlaceholder: '#F1F1F1',
} as const;

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
    flex: 1,
  },
  fill: {
    flex: 1,
  },
  scroll: {
    flex: 1,
  },
  scrollContent: {
    gap: spacing[5],
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  overviewTopRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: componentSize.touchTarget,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  overviewBackButton: {
    alignItems: 'center',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  headerAddButtonPressed: {
    opacity: 0.82,
  },
  overviewTitle: {
    ...typography.headline,
    color: colors.textPrimary,
    marginTop: spacing[2],
    marginBottom: spacing[1],
  },
  centeredHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 56,
    paddingHorizontal: spacing[3],
  },
  headerSide: {
    justifyContent: 'center',
    minWidth: 64,
  },
  headerRight: {
    alignItems: 'flex-end',
  },
  headerIconButton: {
    alignItems: 'center',
    height: 44,
    justifyContent: 'center',
    width: 44,
  },
  chevronIcon: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 42,
    lineHeight: 44,
  },
  centeredHeaderTitle: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: 22,
    letterSpacing: -0.45,
    textAlign: 'center',
  },
  headerAction: {
    ...typography.buttonSmall,
    color: colors.textSecondary,
  },
  headerActionDisabled: {
    color: colors.textTertiary,
  },
  closeIcon: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 32,
    lineHeight: 38,
  },
  monthSelector: {
    alignItems: 'center',
    alignSelf: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'center',
    minHeight: 40,
  },
  monthArrowButton: {
    alignItems: 'center',
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  monthArrow: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 30,
    lineHeight: 34,
  },
  monthLabel: {
    ...typography.subtitle,
    color: colors.textPrimary,
    fontSize: 16,
    minWidth: 96,
    textAlign: 'center',
  },
  card: {
    backgroundColor: ledgerColors.card,
    borderRadius: radius.card,
  },
  expenseSummaryCard: {
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[5],
  },
  cardEyebrow: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  expenseAmount: {
    ...typography.display,
    color: colors.textPrimary,
    marginTop: spacing[2],
  },
  expenseAmountUnit: {
    ...typography.body1,
    fontFamily: fontFamily.regular,
  },
  expenseComparison: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing[1],
  },
  sectionCard: {
    padding: spacing[5],
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  categoryBar: {
    borderRadius: radius.round,
    flexDirection: 'row',
    height: 16,
    marginTop: spacing[3],
    overflow: 'hidden',
    width: '100%',
  },
  categoryBarSegment: {
    height: '100%',
  },
  categoryLegend: {
    gap: spacing[3],
    marginTop: spacing[4],
  },
  categoryLegendRow: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  categoryLegendDot: {
    borderRadius: radius.round,
    height: 8,
    marginRight: spacing[2],
    width: 8,
  },
  categoryLegendLabel: {
    ...typography.body1,
    color: colors.textPrimary,
    flex: 1,
  },
  categoryLegendAmount: {
    ...typography.body1,
    color: colors.textPrimary,
  },
  glanceSection: {
    gap: spacing[2],
  },
  activityCard: {
    gap: spacing[8],
    padding: spacing[5],
  },
  glanceOuterCard: {
    backgroundColor: ledgerColors.softSurface,
    borderRadius: radius.card,
    padding: spacing[1],
  },
  glanceGrid: {
    flexDirection: 'row',
    gap: spacing[1],
  },
  glanceItem: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.default,
    flex: 1,
    gap: spacing[2],
    paddingHorizontal: spacing[1],
    paddingVertical: spacing[4],
  },
  glanceLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    textAlign: 'center',
  },
  glanceValue: {
    ...typography.body1,
    color: colors.textPrimary,
    textAlign: 'center',
  },
  glanceValueNumber: {
    fontFamily: fontFamily.bold,
  },
  expenseText: {
    color: ledgerColors.expense,
  },
  incomeText: {
    color: ledgerColors.income,
  },
  sectionTitleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  textButton: {
    ...typography.buttonSmall,
    color: colors.textPrimary,
  },
  recentSection: {
    gap: spacing[3],
  },
  transactionList: {
    backgroundColor: 'transparent',
  },
  recentGroup: {
    paddingTop: spacing[3],
  },
  groupLabel: {
    ...typography.caption,
    color: colors.textTertiary,
    marginBottom: spacing[2],
    paddingHorizontal: spacing[1],
  },
  transactionRow: {
    alignItems: 'center',
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    minHeight: spacing[20],
    paddingHorizontal: spacing[5],
  },
  transactionRowCompact: {
    borderBottomWidth: 0,
    paddingHorizontal: spacing[1],
  },
  rowPressed: {
    backgroundColor: ledgerColors.softSurface,
  },
  merchantLogoPlaceholder: {
    backgroundColor: ledgerColors.logoPlaceholder,
    borderRadius: radius.round,
    height: 44,
    width: 44,
  },
  transactionCopy: {
    flex: 1,
    marginLeft: spacing[3],
  },
  transactionAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: typography.subtitle.fontSize - 2,
  },
  transactionMerchant: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 14,
    marginTop: 2,
  },
  calendarContent: {
    gap: spacing[4],
    paddingBottom: 112,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  totalsCard: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[6],
    paddingVertical: spacing[4],
  },
  totalItem: {
    gap: spacing[1],
  },
  totalLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 14,
  },
  totalValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 13,
  },
  searchBox: {
    alignItems: 'center',
    backgroundColor: ledgerColors.softSurface,
    borderRadius: 14,
    flexDirection: 'row',
    minHeight: 52,
    paddingHorizontal: spacing[4],
  },
  searchIcon: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 23,
    marginRight: spacing[2],
  },
  searchInput: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: 16,
    paddingVertical: spacing[3],
  },
  calendarCard: {
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[4],
  },
  weekdayRow: {
    flexDirection: 'row',
    marginBottom: spacing[2],
  },
  weekdayLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.medium,
    fontSize: 14,
    textAlign: 'center',
    width: '14.2857%',
  },
  calendarGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  calendarCell: {
    alignItems: 'center',
    height: 67,
    paddingTop: spacing[1],
    width: '14.2857%',
  },
  calendarDayButton: {
    alignItems: 'center',
    borderRadius: radius.round,
    height: 28,
    justifyContent: 'center',
    width: 28,
  },
  calendarDaySelected: {
    backgroundColor: ledgerColors.selectedDay,
  },
  calendarDay: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
  },
  calendarDayOutside: {
    color: ledgerColors.calendarMuted,
  },
  calendarTotal: {
    fontFamily: fontFamily.regular,
    fontSize: 8,
    lineHeight: 11,
    marginTop: 1,
    maxWidth: '100%',
  },
  calendarExpense: {
    color: ledgerColors.expense,
  },
  calendarIncome: {
    color: ledgerColors.income,
  },
  calendarNeutral: {
    color: colors.textTertiary,
  },
  selectedDateTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 16,
    marginBottom: spacing[2],
    marginTop: spacing[3],
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: spacing[8],
  },
  emptyStateText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 14,
  },
  toTopButton: {
    alignItems: 'center',
    alignSelf: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    bottom: spacing[4],
    elevation: 4,
    position: 'absolute',
    shadowColor: colors.textPrimary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.18,
    shadowRadius: 6,
  },
  toTopButtonInner: {
    paddingHorizontal: spacing[6],
    paddingVertical: spacing[3],
  },
  toTopLabel: {
    color: colors.white,
    fontFamily: fontFamily.medium,
    fontSize: 16,
  },
  detailContent: {
    gap: spacing[4],
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  detailCard: {
    padding: spacing[6],
  },
  detailHero: {
    alignItems: 'center',
    flexDirection: 'row',
  },
  detailMerchant: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: 20,
    marginLeft: spacing[3],
  },
  detailAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 30,
    letterSpacing: -0.8,
    marginBottom: spacing[8],
    marginTop: spacing[5],
  },
  detailSettingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 56,
  },
  detailSettingLabel: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.medium,
    fontSize: 17,
  },
  detailSettingValue: {
    color: colors.textSecondary,
    flexShrink: 1,
    fontFamily: fontFamily.regular,
    fontSize: 16,
    textAlign: 'right',
  },
  detailMemoEditor: {
    borderRadius: radius.default,
    minHeight: 56,
    paddingHorizontal: spacing[2],
  },
  detailMemoInput: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 16,
    paddingVertical: spacing[4],
  },
  rowChevron: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: 28,
    marginLeft: spacing[2],
  },
  divider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[5],
  },
  detailInfoRow: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    minHeight: 46,
  },
  detailInfoLabel: {
    color: colors.textPrimary,
    flex: 0.8,
    fontFamily: fontFamily.medium,
    fontSize: 16,
  },
  detailInfoValue: {
    color: colors.textSecondary,
    flex: 1.5,
    fontFamily: fontFamily.regular,
    fontSize: 15,
    textAlign: 'right',
  },
  historyTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  historySummary: {
    flexDirection: 'row',
    marginVertical: spacing[5],
  },
  historySummaryItem: {
    alignItems: 'center',
    flex: 1,
    gap: spacing[2],
  },
  historySummaryItemBorder: {
    borderLeftColor: colors.divider,
    borderLeftWidth: StyleSheet.hairlineWidth,
  },
  historySummaryLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 14,
  },
  historySummaryValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 18,
  },
  historyDate: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 14,
    marginBottom: spacing[1],
    marginTop: spacing[3],
  },
  categoryScreenContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  categoryTransactionPreview: {
    alignItems: 'center',
    flexDirection: 'row',
    marginBottom: spacing[4],
    paddingHorizontal: spacing[5],
  },
  categoryPreviewCopy: {
    flex: 1,
    marginLeft: spacing[3],
  },
  categoryPreviewAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 18,
  },
  categoryPreviewMerchant: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 14,
    marginTop: 2,
  },
  categoryList: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  categoryRow: {
    alignItems: 'center',
    borderRadius: radius.default,
    flexDirection: 'row',
    minHeight: 64,
    paddingHorizontal: spacing[2],
  },
  categoryRowSelected: {
    backgroundColor: ledgerColors.selectedTab,
  },
  categoryIcon: {
    height: 48,
    width: 48,
  },
  categoryLabel: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.medium,
    fontSize: 17,
    marginLeft: spacing[3],
  },
  categoryCheck: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: 20,
  },
  formKeyboardArea: {
    flex: 1,
  },
  formScrollContent: {
    paddingBottom: spacing[5],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  typeSelector: {
    backgroundColor: colors.white,
    borderRadius: radius.round,
    flexDirection: 'row',
    marginBottom: spacing[5],
    padding: spacing[1],
  },
  typeOption: {
    alignItems: 'center',
    borderRadius: radius.round,
    flex: 1,
    justifyContent: 'center',
    minHeight: 52,
  },
  typeSelectorPill: {
    backgroundColor: ledgerColors.selectedTab,
    borderRadius: radius.round,
    bottom: spacing[1],
    left: spacing[1],
    position: 'absolute',
    top: spacing[1],
  },
  typeOptionText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 20,
  },
  formCard: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    paddingHorizontal: spacing[5],
    paddingVertical: spacing[4],
  },
  formRow: {
    alignItems: 'center',
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    minHeight: 70,
  },
  formLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 17,
    width: 96,
  },
  formValue: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: 16,
    textAlign: 'right',
  },
  amountInput: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: 16,
    paddingVertical: spacing[3],
    textAlign: 'right',
  },
  amountUnit: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    marginLeft: spacing[2],
  },
  saveButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.default,
    justifyContent: 'center',
    marginHorizontal: semanticSpacing.screenMargin,
    minHeight: 56,
  },
  saveButtonDisabled: {
    backgroundColor: colors.disabledBackground,
  },
  saveButtonPressed: {
    opacity: 0.82,
  },
  saveButtonLabel: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: 20,
  },
  formFooter: {
    backgroundColor: colors.background,
    paddingBottom: spacing[5],
    paddingTop: spacing[3],
  },
});
