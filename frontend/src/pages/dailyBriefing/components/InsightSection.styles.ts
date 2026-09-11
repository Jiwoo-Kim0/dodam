import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  insightSection: {
    padding: spacing[4],
  },
  insightRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  insightCard: {
    backgroundColor: colors.briefing.softCard,
    borderColor: colors.briefing.cardBorder,
    borderRadius: radius.card,
    borderWidth: 1,
    flex: 1,
    minHeight: 104,
    paddingHorizontal: spacing[2],
    paddingVertical: 10,
  },
  insightTag: {
    alignSelf: 'center',
    backgroundColor: colors.briefing.decreaseTag,
    borderRadius: radius.round,
    paddingHorizontal: spacing[3],
    paddingVertical: 1,
  },
  insightTagText: {
    color: colors.primary.brown,
    fontFamily: fontFamily.medium,
    fontSize: 11,
    lineHeight: 15,
  },
  insightIncreaseText: {
    color: colors.briefing.increaseText,
  },
  insightBody: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: spacing[1],
    marginTop: 10,
  },
  insightIconFrame: {
    alignItems: 'center',
    height: 40,
    justifyContent: 'center',
    width: 40,
  },
  insightIcon: {
    height: 38,
    width: 38,
  },
  insightTextContent: {
    flex: 1,
    minWidth: 0,
  },
  insightTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    letterSpacing: -0.3,
    lineHeight: 17,
  },
  insightDescription: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
    marginTop: 2,
  },
});
