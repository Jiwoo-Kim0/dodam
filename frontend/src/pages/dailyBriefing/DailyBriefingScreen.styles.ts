import { StyleSheet } from 'react-native';

import { colors, fontFamily, radius, semanticSpacing, spacing, fontSize } from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  pressed: {
    opacity: 0.62,
  },
  scrollContent: {
    paddingBottom: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  sections: {
    gap: spacing[3],
  },
  card: {
    backgroundColor: colors.briefing.card,
    borderRadius: radius.card,
  },
  sectionTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.buttonSmall,
    lineHeight: 20,
  },
});
