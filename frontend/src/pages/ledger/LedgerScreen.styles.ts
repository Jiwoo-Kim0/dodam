import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

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
    gap: spacing[3],
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  headerAddButtonPressed: {
    opacity: 0.82,
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
    fontSize: fontSize.title,
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
    justifyContent: 'center',
    minHeight: 36,
  },
  monthArrowButton: {
    alignItems: 'center',
    height: 32,
    justifyContent: 'center',
    width: 32,
  },
  /** 월 이름이 바뀌어도 화살표가 움직이지 않도록 폭을 고정한다. */
  monthLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 16,
    lineHeight: 22,
    minWidth: 104,
    textAlign: 'center',
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
  },
  expenseSummaryCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  expenseSummaryContent: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
  },
  expenseSummaryCopy: {
    flex: 1,
    minWidth: 0,
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. 제목은 작고 연하게, 금액이 눈에 먼저 들어오게 한다. */
  cardEyebrow: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  expenseAmount: {
    ...typography.headline,
    color: colors.textPrimary,
    marginTop: spacing[1],
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
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  categorySectionTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  categorySummaryList: {
    gap: spacing[3],
    marginTop: spacing[4],
  },
  categoryItem: {
    gap: spacing[2],
    borderRadius: radius.small,
    paddingVertical: spacing[1],
  },
  fixedMonthSelector: {
    zIndex: 12,
    backgroundColor: colors.background,
    paddingVertical: spacing[2],
  },
  headerActions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[1],
  },
  /** 두 액션이 헤더 우측 폭(84) 안에 들어가도록 36으로 잡고, 모자란 터치 영역은 hitSlop으로 채운다. */
  headerIconAction: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerMenu: {
    position: 'absolute',
    right: semanticSpacing.screenMargin,
    top: 52,
    zIndex: 20,
    minWidth: 148,
    paddingVertical: spacing[1],
    borderRadius: radius.default,
    backgroundColor: colors.white,
    elevation: 8,
    shadowColor: colors.textPrimary,
    shadowOffset: { width: 0, height: 3 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
  },
  headerMenuItem: {
    minHeight: componentSize.touchTarget,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[4],
  },
  headerMenuIcon: {
    width: 28,
    color: colors.textSecondary,
    fontSize: 16,
  },
  headerMenuLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  categoryItemHeader: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  categoryNameRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    gap: spacing[2],
  },
  categoryLegendLabel: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  categoryLegendAmount: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  categoryLegendRatio: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  categoryProgressTrack: {
    backgroundColor: colors.ledger.categoryTrack,
    borderRadius: radius.round,
    flexDirection: 'row',
    height: 8,
    overflow: 'hidden',
    padding: 1,
  },
  categoryProgressFill: {
    borderRadius: radius.round,
  },
  glanceSection: {
    gap: spacing[2],
  },
  activityCard: {
    gap: spacing[5],
    padding: spacing[4],
  },
  glanceStats: {
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.default,
    flexDirection: 'row',
    marginTop: spacing[1],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  glanceStat: {
    flex: 1,
    gap: spacing[1],
  },
  glanceStatTrailing: {
    alignItems: 'flex-end',
  },
  glanceStatDivider: {
    alignSelf: 'stretch',
    backgroundColor: colors.divider,
    marginHorizontal: spacing[4],
    width: StyleSheet.hairlineWidth,
  },
  glanceLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  glanceStatValue: {
    ...typography.subtitle,
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
  },
  glanceComparison: {
    alignItems: 'center',
    borderRadius: radius.default,
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  glanceComparisonLabel: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  glanceComparisonValue: {
    ...typography.body2,
    fontFamily: fontFamily.semiBold,
    textAlign: 'right',
  },
  spendingIncreaseSurface: {
    backgroundColor: colors.ledger.spendingIncreaseSurface,
  },
  spendingDecreaseSurface: {
    backgroundColor: colors.ledger.spendingDecreaseSurface,
  },
  spendingNeutralSurface: {
    backgroundColor: colors.ledger.softSurface,
  },
  expenseText: {
    color: colors.ledger.expense,
  },
  incomeText: {
    color: colors.ledger.income,
  },
  spendingDecreaseText: {
    color: colors.ledger.income,
  },
  sectionTitleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  textButton: {
    ...typography.body2,
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
  },
  recentSection: {
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: spacing[2],
    paddingTop: spacing[4],
  },
  transactionList: {
    backgroundColor: 'transparent',
  },
  recentGroup: {
    paddingTop: spacing[2],
  },
  recentGroupSeparated: {
    marginTop: spacing[4],
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
    minHeight: 64,
    paddingHorizontal: spacing[1],
  },
  transactionRowCompact: {
    borderBottomWidth: 0,
    minHeight: 48,
    paddingHorizontal: spacing[1],
  },
  rowPressed: {
    backgroundColor: colors.ledger.softSurface,
  },
  transactionCategoryIcon: {
    height: 36,
    width: 36,
  },
  transactionCategoryIconCompact: {
    height: 36,
    width: 36,
  },
  transactionCopy: {
    flex: 1,
    marginLeft: spacing[3],
  },
  transactionCopyCompact: {
    marginLeft: spacing[2],
  },
  transactionAmount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: typography.subtitle.fontSize - 2,
  },
  transactionAmountCompact: {
    fontSize: fontSize.body1,
  },
  transactionMerchant: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    marginTop: 2,
  },
  transactionMerchantCompact: {
    fontSize: fontSize.body2,
  },
  calendarContent: {
    gap: spacing[3],
    paddingBottom: 112,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  totalsCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  totalPrimaryLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  totalPrimaryValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    marginTop: spacing[1],
  },
  totalSecondaryRow: {
    alignItems: 'center',
    flexDirection: 'row',
    marginTop: spacing[4],
  },
  totalSecondaryItem: {
    flex: 1,
    gap: spacing[1],
  },
  totalSecondaryItemTrailing: {
    alignItems: 'flex-end',
  },
  totalSecondaryDivider: {
    alignSelf: 'stretch',
    backgroundColor: colors.divider,
    marginHorizontal: spacing[4],
    width: StyleSheet.hairlineWidth,
  },
  totalSecondaryLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  totalSecondaryValue: {
    ...typography.body2,
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
  },
  searchBox: {
    alignItems: 'center',
    backgroundColor: colors.ledger.softSurface,
    borderRadius: radius.default,
    flexDirection: 'row',
    minHeight: 40,
    paddingHorizontal: spacing[3],
  },
  searchIcon: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.subtitle,
    marginRight: spacing[2],
  },
  searchInput: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    paddingVertical: spacing[2],
  },
  searchClearButton: {
    alignItems: 'center',
    borderRadius: radius.round,
    height: 28,
    justifyContent: 'center',
    width: 28,
  },
  searchClearIcon: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.subtitle,
    lineHeight: 22,
  },
  hiddenTransactionsButton: {
    alignSelf: 'flex-end',
    backgroundColor: colors.white,
    borderColor: colors.divider,
    borderRadius: radius.round,
    borderWidth: StyleSheet.hairlineWidth,
    marginTop: spacing[1],
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
  },
  hiddenTransactionsButtonSelected: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.primary.green,
  },
  hiddenTransactionsButtonText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
  },
  hiddenTransactionsButtonTextSelected: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  calendarCard: {
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[3],
  },
  weekdayRow: {
    flexDirection: 'row',
    marginBottom: spacing[1],
  },
  weekdayLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    textAlign: 'center',
    width: '14.2857%',
  },
  calendarGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  calendarCell: {
    alignItems: 'center',
    height: 58,
    paddingTop: spacing[1],
    width: '14.2857%',
  },
  calendarDayButton: {
    alignItems: 'center',
    borderRadius: radius.round,
    height: 26,
    justifyContent: 'center',
    width: 26,
  },
  calendarDaySelected: {
    backgroundColor: colors.primary.lightGreen,
  },
  overviewCalendar: {
    paddingHorizontal: spacing[1],
  },
  overviewCalendarCell: {
    alignItems: 'center',
    minHeight: 52,
    paddingTop: spacing[1],
    width: '14.2857%',
  },
  stickyCalendar: {
    position: 'absolute',
    top: 104,
    right: semanticSpacing.screenMargin,
    left: semanticSpacing.screenMargin,
    zIndex: 15,
    paddingHorizontal: spacing[5],
    paddingTop: spacing[3],
    paddingBottom: spacing[2],
    borderRadius: radius.card,
    backgroundColor: colors.white,
  },
  calendarDayToday: {
    borderColor: colors.primary.green,
    borderWidth: 1,
  },
  calendarDaySelectedText: {
    color: colors.home.assetChart.strong,
    fontFamily: fontFamily.semiBold,
  },
  calendarDay: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
  },
  calendarDayOutside: {
    color: colors.ledger.calendarMuted,
  },
  calendarDayDisabled: {
    color: colors.textTertiary,
  },
  calendarTotal: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.tiny,
    lineHeight: 11,
    marginTop: 1,
    maxWidth: '100%',
  },
  calendarIncome: {
    color: colors.ledger.income,
  },
  calendarNeutral: {
    color: colors.textTertiary,
  },
  calendarToggle: {
    width: 32,
    height: 28,
    alignSelf: 'center',
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.round,
  },
  calendarDivider: {
    height: StyleSheet.hairlineWidth,
    marginHorizontal: spacing[2],
    backgroundColor: colors.divider,
  },
  activeFilterChip: {
    alignSelf: 'flex-start',
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
    borderRadius: radius.round,
    backgroundColor: colors.primary.lightGreen,
  },
  activeFilterLabel: {
    ...typography.caption,
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
  },
  calendarFeedGroup: {
    paddingHorizontal: spacing[2],
  },
  calendarFeedCard: {
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[2],
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
  sectionEmptyState: {
    alignItems: 'center',
    paddingVertical: spacing[5],
  },
  emptyStateText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
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
    fontSize: fontSize.button,
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
    fontSize: fontSize.buttonSmall,
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
    fontSize: fontSize.body1,
  },
  historySummaryValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
  },
  historyDate: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
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
    fontSize: fontSize.subtitle,
  },
  categoryPreviewMerchant: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
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
    backgroundColor: colors.primary.lightGreen,
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
    fontSize: fontSize.button,
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
    marginBottom: spacing[4],
    padding: spacing[1],
  },
  typeOption: {
    alignItems: 'center',
    borderRadius: radius.round,
    flex: 1,
    justifyContent: 'center',
    minHeight: 44,
  },
  typeSelectorPill: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    bottom: spacing[1],
    left: spacing[1],
    position: 'absolute',
    top: spacing[1],
  },
  typeOptionText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.buttonSmall,
  },
  formCard: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  formRow: {
    alignItems: 'center',
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    minHeight: 56,
  },
  amountFieldSection: {
    borderBottomColor: colors.divider,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  formRowWithoutDivider: {
    borderBottomWidth: 0,
  },
  formRowLast: {
    borderBottomWidth: 0,
  },
  formValidationText: {
    color: colors.ledger.expense,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
    marginTop: -spacing[1],
    paddingBottom: spacing[2],
    paddingLeft: 80,
  },
  formLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.buttonSmall,
    width: 80,
  },
  formValue: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    paddingRight: spacing[1],
    textAlign: 'right',
  },
  amountInput: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    paddingVertical: spacing[2],
    textAlign: 'right',
  },
  amountUnit: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    marginLeft: spacing[1],
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
    fontSize: fontSize.button,
  },
  formFooter: {
    backgroundColor: colors.background,
    paddingBottom: spacing[5],
    paddingTop: spacing[3],
  },
});
