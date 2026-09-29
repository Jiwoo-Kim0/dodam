import { StyleSheet } from 'react-native';

import { colors, componentSize, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  overlay: {
    alignItems: 'center',
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: spacing[8],
  },
  backdrop: {
    backgroundColor: colors.overlay,
    bottom: 0,
    left: 0,
    position: 'absolute',
    right: 0,
    top: 0,
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    maxWidth: 360,
    paddingBottom: spacing[4],
    paddingHorizontal: spacing[5],
    paddingTop: spacing[6],
    width: '100%',
  },
  title: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
    lineHeight: 26,
    textAlign: 'center',
  },
  message: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
    lineHeight: 21,
    marginTop: spacing[2],
    textAlign: 'center',
  },
  /** 버튼 두 개는 가로로(취소 왼쪽), 하나나 셋 이상은 세로로 쌓는다. */
  buttonRow: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[6],
  },
  buttonColumn: {
    gap: spacing[2],
    marginTop: spacing[6],
  },
  button: {
    alignItems: 'center',
    borderRadius: radius.default,
    justifyContent: 'center',
    minHeight: componentSize.button.compact,
    paddingHorizontal: spacing[3],
  },
  buttonInRow: {
    flex: 1,
  },
  primaryButton: {
    backgroundColor: colors.primary.green,
  },
  /** 삭제·해제처럼 되돌릴 수 없는 동작. 앱에서 경고에 쓰는 주황을 쓴다. */
  destructiveButton: {
    backgroundColor: colors.primary.orange,
  },
  cancelButton: {
    backgroundColor: colors.ledger.softSurface,
  },
  label: {
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 20,
  },
  filledLabel: {
    color: colors.white,
  },
  cancelLabel: {
    color: colors.textSecondary,
  },
  pressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
});
