import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  peerCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  peerCardPressed: {
    opacity: 0.85,
  },
  header: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
  },
  /** 분석 근거를 드러내되 모델 버전 같은 내부 정보는 노출하지 않는다. */
  badge: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.round,
    paddingHorizontal: spacing[2],
    paddingVertical: 3,
  },
  badgeText: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  clusterLabel: {
    ...typography.title,
    color: colors.textPrimary,
    letterSpacing: -0.4,
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  chartRow: {
    alignItems: 'center',
    marginTop: spacing[2],
  },
  moreRow: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing[3],
    paddingTop: spacing[3],
  },
  moreText: {
    ...typography.caption,
    color: colors.report.scoreStrong,
  },
  moreChevron: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    lineHeight: 16,
  },
  legend: {
    flexDirection: 'row',
    gap: spacing[4],
    justifyContent: 'center',
    marginTop: spacing[1],
  },
  legendItem: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  /** 그래프의 실선·점선과 같은 대비로 보여 준다. */
  legendUserMark: {
    backgroundColor: colors.primary.green,
    borderRadius: 2,
    height: 3,
    width: 14,
  },
  legendPeerMark: {
    borderColor: colors.textTertiary,
    borderRadius: 2,
    borderStyle: 'dashed',
    borderWidth: 1,
    height: 0,
    opacity: 0.55,
    width: 14,
  },
  legendText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  chartGuide: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    marginTop: spacing[1],
    textAlign: 'center',
  },
  emptyMessage: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
    marginTop: semanticSpacing.cardTitleContentGap,
  },
});
