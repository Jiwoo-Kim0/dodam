import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  patternCard: {
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  patternList: {
    marginTop: semanticSpacing.cardTitleContentGap,
  },
  patternDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[1],
  },
  patternRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingVertical: spacing[2],
  },
  /** 아이콘 배경이 상태 표시를 겸한다. 기본은 좋은 상태, 주의가 필요하면 노란 배경이다. */
  patternIconFrame: {
    alignItems: 'center',
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  patternIconFrameCheck: {
    backgroundColor: colors.report.checkBadge,
  },
  patternIcon: {
    height: 24,
    width: 24,
  },
  patternTextContent: {
    flex: 1,
    minWidth: 0,
  },
  patternTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.3,
    lineHeight: 19,
  },
  patternDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
    marginTop: 2,
  },
});
