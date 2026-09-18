import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  summaryCard: {
    alignItems: 'center',
    flexDirection: 'row',
    minHeight: 124,
    overflow: 'hidden',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  summaryCharacterFrame: {
    height: 84,
    marginLeft: -spacing[3],
    marginRight: spacing[2],
    position: 'relative',
    width: 84,
  },
  summaryCharacter: {
    height: '100%',
    width: '100%',
  },
  summaryCharacterCornerMask: {
    backgroundColor: colors.briefing.card,
    bottom: 0,
    height: 12,
    position: 'absolute',
    right: 0,
    width: 12,
  },
  summaryContent: {
    flex: 1,
    minWidth: 0,
  },
  summaryHeadingRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  /** 리포트 화면과 같은 카드 제목 규칙이다. 아래 금액이 시선을 가져가게 한다. */
  summaryLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  statusBadge: {
    backgroundColor: colors.briefing.statusBackground,
    borderColor: colors.primary.green,
    borderRadius: radius.round,
    borderWidth: 1,
    paddingHorizontal: spacing[2],
    paddingVertical: spacing[1],
  },
  statusBadgeText: {
    color: colors.briefing.statusText,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
  summaryAmount: {
    color: colors.primary.brown,
    fontFamily: fontFamily.bold,
    fontSize: fontSize.headline,
    letterSpacing: -0.6,
    lineHeight: 34,
    marginTop: spacing[2],
  },
  summaryAmountUnit: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
  },
  averageText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.detail,
    lineHeight: 16,
  },
});
