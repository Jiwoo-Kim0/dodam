import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  metricsCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  metricGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing[2],
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  metricTile: {
    backgroundColor: colors.background,
    borderRadius: radius.card,
    flexBasis: '48%',
    flexGrow: 1,
    // 증감 문구 길이와 상관없이 네 타일 높이를 같게 고정한다.
    height: 88,
    justifyContent: 'space-between',
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[2],
  },
  metricHeader: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  metricLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    flexShrink: 1,
    lineHeight: 17,
  },
  metricIconFrame: {
    alignItems: 'center',
    height: 18,
    justifyContent: 'center',
    // 숫자가 주인공이라 아이콘은 보조로만 남긴다.
    opacity: 0.55,
    width: 18,
  },
  metricIcon: {
    height: 18,
    width: 18,
  },
  metricValueRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
  },
  metricValue: {
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.subtitle,
    letterSpacing: -0.6,
    lineHeight: 24,
  },
  metricValueUnit: {
    ...typography.caption,
    color: colors.textPrimary,
    letterSpacing: -0.2,
  },
  metricChange: {
    ...typography.caption,
    lineHeight: 17,
  },
  metricChangeGood: {
    color: colors.primary.green,
  },
  /** 지출·부채가 늘어난 방향. 일일 브리핑의 소비 증가 표시와 같은 색을 쓴다. */
  metricChangeBad: {
    color: colors.primary.orange,
  },
  metricChangeNeutral: {
    color: colors.textTertiary,
  },
});
