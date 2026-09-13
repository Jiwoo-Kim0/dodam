import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  patternCard: {
    backgroundColor: colors.report.patternSurface,
    paddingBottom: spacing[1],
    paddingHorizontal: spacing[4],
    paddingTop: spacing[5],
  },
  patternList: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    marginTop: spacing[2],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  patternDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[1],
  },
  patternRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
  },
  /** 텍스트 두 줄(15+1+12)과 같은 높이라 행이 더 커지지 않는다. */
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
    fontSize: 11,
    letterSpacing: -0.3,
    lineHeight: 15,
  },
  patternDescription: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 9,
    lineHeight: 12,
    marginTop: 1,
  },
  statusBadge: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    flexShrink: 0,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  statusBadgeCheck: {
    backgroundColor: colors.report.checkBadge,
  },
  statusBadgeText: {
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
    fontSize: 9,
    lineHeight: 13,
  },
  statusBadgeTextCheck: {
    color: colors.primary.brown,
  },
  /** 캐릭터와 문구를 한 덩어리로 묶어 카드 가운데에 놓는다. */
  coachingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
    justifyContent: 'center',
    marginTop: spacing[1],
  },
  coachingCharacter: {
    height: 48,
    width: 48,
  },
  coachingMessage: {
    color: colors.primary.green,
    flexShrink: 1,
    fontFamily: fontFamily.semiBold,
    fontSize: 10,
    lineHeight: 14,
    minWidth: 0,
  },
});
