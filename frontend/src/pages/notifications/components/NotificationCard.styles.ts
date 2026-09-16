import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  radius,
  spacing,
} from '@/styles';

const ICON_SIZE = 40;
/** 본문 길이와 무관하게 모든 알림 카드의 높이를 동일하게 유지한다. */
const CARD_MIN_HEIGHT = 76;

export const styles = StyleSheet.create({
  swipeable: {
    borderRadius: radius.card,
    overflow: 'hidden',
  },
  card: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    flexDirection: 'row',
    gap: spacing[3],
    minHeight: CARD_MIN_HEIGHT,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  cardOpen: {
    borderBottomRightRadius: 0,
    borderTopRightRadius: 0,
  },
  unreadIndicator: {
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: spacing[2],
    position: 'absolute',
    right: spacing[3],
    top: spacing[3],
    width: spacing[2],
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
    marginRight: spacing[2],
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  deleteAction: {
    alignItems: 'center',
    backgroundColor: colors.primary.orange,
    borderBottomRightRadius: radius.card,
    borderTopRightRadius: radius.card,
    justifyContent: 'center',
    minHeight: CARD_MIN_HEIGHT,
    width: 64,
  },
  deleteActionLabel: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
  },
  deleteActionPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
