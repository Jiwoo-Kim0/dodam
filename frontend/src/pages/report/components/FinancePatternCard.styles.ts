import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, semanticSpacing, spacing } from '@/styles';

export const styles = StyleSheet.create({
  patternCard: {
    backgroundColor: colors.report.patternSurface,
    paddingBottom: spacing[3],
    paddingHorizontal: spacing[4],
    paddingTop: spacing[3],
  },
  patternList: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    marginTop: semanticSpacing.cardTitleContentGap,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[1],
  },
  patternDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: 2,
  },
  patternRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    minHeight: 48,
  },
  /** 아이콘을 텍스트 두 줄보다 작게 유지해 행 높이가 커지지 않게 한다. */
  patternIconFrame: {
    alignItems: 'center',
    height: 28,
    justifyContent: 'center',
    width: 28,
  },
  patternIcon: {
    height: 28,
    width: 28,
  },
  patternTextContent: {
    flex: 1,
    minWidth: 0,
  },
  patternTitle: {
    color: colors.textPrimary,
    flexShrink: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: 13,
    letterSpacing: -0.3,
    lineHeight: 18,
  },
  patternDescription: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
    marginTop: 1,
  },
  statusBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    flexShrink: 0,
    paddingHorizontal: spacing[2],
    paddingVertical: 3,
  },
  statusBadgeCheck: {
    backgroundColor: colors.report.checkBadge,
  },
  statusBadgeText: {
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
    fontSize: 10,
    lineHeight: 14,
  },
  statusBadgeTextCheck: {
    color: colors.primary.brown,
  },
  /** 캐릭터와 문구를 한 덩어리로 묶어 카드 가운데에 놓는다. */
  coachingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[2],
  },
  coachingCharacter: {
    height: 64,
    width: 64,
  },
  coachingContent: {
    alignItems: 'flex-start',
    flex: 1,
    minWidth: 0,
  },
  coachingLabel: {
    color: colors.primary.brown,
    fontFamily: fontFamily.medium,
    fontSize: 11,
    lineHeight: 15,
    textAlign: 'left',
    width: '100%',
  },
  coachingMessage: {
    color: colors.primary.green,
    fontFamily: fontFamily.semiBold,
    fontSize: 12,
    lineHeight: 17,
    marginTop: 1,
    textAlign: 'left',
    width: '100%',
  },
  coachingDetail: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 14,
    marginTop: 2,
    textAlign: 'left',
    width: '100%',
  },
  disclaimer: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 9,
    lineHeight: 13,
    marginTop: spacing[1],
    textAlign: 'left',
    width: '100%',
  },
});
