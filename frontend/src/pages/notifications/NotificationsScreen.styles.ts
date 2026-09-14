import { StyleSheet } from 'react-native';

import { colors, componentSize, semanticSpacing, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
  },
  header: {
    justifyContent: 'center',
    minHeight: componentSize.touchTarget,
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  backButton: {
    alignItems: 'flex-start',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  pressed: {
    opacity: 0.5,
  },
  scrollContent: {
    paddingBottom: spacing[6],
    paddingHorizontal: semanticSpacing.screenMargin,
  },
  titleRow: {
    alignItems: 'center',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing[2],
  },
  title: {
    ...typography.headline,
    color: colors.textPrimary,
  },
  /** 아이콘 오른쪽 끝을 화면 여백에 맞춰 뒤로가기 버튼과 좌우 대칭을 이룬다. */
  settingsButton: {
    alignItems: 'flex-end',
    height: componentSize.touchTarget,
    justifyContent: 'center',
    width: componentSize.touchTarget,
  },
  list: {
    gap: spacing[3],
    marginTop: spacing[4],
  },
});
