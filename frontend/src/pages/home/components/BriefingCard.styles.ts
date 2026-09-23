import { StyleSheet } from 'react-native';

import { colors, componentSize, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.primary.brown,
    borderRadius: radius.card,
    minHeight: 90,
    overflow: 'visible',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
    position: 'relative',
  },
  cardPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  characterImage: {
    bottom: -12,
    height: 76,
    left: 0,
    position: 'absolute',
    transform: [{ scaleX: -1 }],
    width: 76,
    zIndex: 2,
  },
  title: {
    color: colors.white,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  body: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    marginLeft: 72,
    marginTop: spacing[3],
    paddingRight: spacing[8],
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
    fontFamily: fontFamily.bold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 20,
  },
  messageBubble: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    flex: 1,
    flexDirection: 'row',
    gap: spacing[2],
    maxWidth: '62%',
    minHeight: 28,
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
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  loadingIndicator: {
    flex: 1,
  },
  unavailableText: {
    color: colors.white,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 18,
  },
});
