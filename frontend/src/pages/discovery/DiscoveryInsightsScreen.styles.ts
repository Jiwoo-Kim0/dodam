import { StyleSheet } from 'react-native';

import {
  fontSize,
  colors,
  fontFamily,
  radius,
  semanticSpacing,
  shadows,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scroll: {
    backgroundColor: colors.home.goalGradientEnd,
  },
  scrollContent: {
    backgroundColor: colors.background,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  /** 아래 여백은 여기에 준다. 스크롤 컨텐츠에 주면 그라데이션 밖이라 흰 띠가 드러난다. */
  gradientContainer: {
    marginTop: spacing[6],
    paddingBottom: spacing[8],
    position: 'relative',
  },
  gradientOverlay: {
    left: -semanticSpacing.screenMargin,
    position: 'absolute',
    right: -semanticSpacing.screenMargin,
  },
  summaryCard: {
    ...shadows.softCard,
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[2],
    padding: spacing[4],
    zIndex: 2,
  },
  summaryHeaderRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  summaryLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  summaryCount: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
  },
  summaryHeadline: {
    ...typography.title,
    color: colors.textPrimary,
    lineHeight: 28,
  },
  summaryAmountHighlight: {
    color: colors.primary.green,
  },
  /** 누를 수 없는 안내 문구다. 예전에는 아래 재시도 버튼과 같은 스타일을 써서 구분이 안 됐다. */
  summaryNote: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  summaryNoteLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
  },
  retryButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: 44,
    paddingHorizontal: spacing[3],
  },
  retryButtonText: {
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
  disabledButton: {
    opacity: 0.45,
  },
  cooldownLabel: {
    ...typography.caption,
    color: colors.textTertiary,
    marginBottom: spacing[2],
    textAlign: 'center',
  },
  fixedFooter: {
    backgroundColor: colors.home.goalGradientEnd,
    paddingBottom: spacing[4],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  diagnosisButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: 44,
  },
  diagnosisButtonPressed: {
    opacity: 0.7,
  },
  diagnosisButtonText: {
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
  listSection: {
    gap: spacing[3],
    marginTop: spacing[6],
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. */
  sectionTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  /** 오류·빈 상태 안내다. 예전에는 섹션 제목과 같은 스타일이라 제목처럼 읽혔다. */
  stateMessage: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 19,
    marginTop: spacing[2],
  },
});
