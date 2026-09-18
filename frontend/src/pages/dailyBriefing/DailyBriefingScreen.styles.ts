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
  /** 리포트 화면과 같은 카드 제목 규칙이다. 제목은 작고 연하게, 값이 눈에 먼저 들어오게 한다. */
  sectionTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  emptyMessage: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 19,
    marginTop: spacing[3],
  },
});
