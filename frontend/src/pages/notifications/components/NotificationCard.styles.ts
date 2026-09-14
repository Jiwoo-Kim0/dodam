import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

const ICON_SIZE = 40;
/** 본문 길이와 무관하게 모든 알림 카드의 높이를 동일하게 유지한다. */
const CARD_MIN_HEIGHT = 76;

export const styles = StyleSheet.create({
  card: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    elevation: 1,
    flexDirection: 'row',
    gap: spacing[3],
    minHeight: CARD_MIN_HEIGHT,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
    shadowColor: colors.textPrimary,
    shadowOffset: { height: 2, width: 0 },
    shadowOpacity: 0.08,
    shadowRadius: 3,
  },
  icon: {
    height: ICON_SIZE,
    width: ICON_SIZE,
  },
  content: {
    flex: 1,
    gap: spacing[1],
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: 16,
    lineHeight: 22,
  },
  description: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
  },
  timeLabel: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
});
