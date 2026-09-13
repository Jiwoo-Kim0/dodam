import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  semanticSpacing,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  monthSelector: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  monthLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  screenDescription: {
    ...typography.body1,
    color: colors.textSecondary,
    lineHeight: 20,
  },
  sections: {
    gap: spacing[4],
    marginTop: spacing[4],
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
  },
  /** 가계부·브리핑의 카드 제목과 같은 규칙(16 Medium / 22)을 사용한다. */
  cardTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: 16,
    lineHeight: 22,
  },
  cardDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
    marginTop: spacing[1],
  },
});
