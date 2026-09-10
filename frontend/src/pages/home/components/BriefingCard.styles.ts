import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.primary.brown,
    borderRadius: radius.card,
    minHeight: 102,
    overflow: 'visible',
    padding: spacing[4],
    position: 'relative',
  },
  characterImage: {
    bottom: -18,
    height: 88,
    left: 0,
    position: 'absolute',
    transform: [{ scaleX: -1 }],
    width: 88,
    zIndex: 2,
  },
  title: {
    color: colors.white,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  body: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    marginLeft: 64,
    marginTop: spacing[2],
  },
  spendingSummary: {
    alignItems: 'center',
  },
  label: {
    color: colors.white,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  amount: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    lineHeight: 28,
    marginTop: spacing[1],
  },
  messageBubble: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.default,
    flex: 1,
    flexDirection: 'row',
    gap: spacing[2],
    minHeight: 30,
    paddingHorizontal: spacing[2],
  },
  calendarIcon: {
    height: 14,
    width: 14,
  },
  messageText: {
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: 10,
    lineHeight: 15,
  },
});
