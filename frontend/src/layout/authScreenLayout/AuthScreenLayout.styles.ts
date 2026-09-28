import { StyleSheet } from 'react-native';

import { colors, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingTop: semanticSpacing.screenTitleTop,
    paddingHorizontal: semanticSpacing.screenMargin,
    backgroundColor: colors.white,
  },

  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },

  content: {
    flex: 1,
    marginTop: semanticSpacing.screenTitleContentGap,
  },

  /** `avoidKeyboard`를 켠 화면 전용. 키보드가 올라오면 이 영역이 줄고 본문이 스크롤된다. */
  keyboardArea: {
    flex: 1,
  },

  scrollContent: {
    flexGrow: 1,
    paddingBottom: spacing[6],
  },

  scrollableContent: {
    marginTop: semanticSpacing.screenTitleContentGap,
  },

  description: {
    ...typography.body1,
    marginTop: spacing[2],
    color: colors.textSecondary,
    lineHeight: 20,
  },
});
