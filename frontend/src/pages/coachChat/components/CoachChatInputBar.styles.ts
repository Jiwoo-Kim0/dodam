import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  semanticSpacing,
  spacing,
} from '@/styles';

const SEND_BUTTON_SIZE = 36;

export const styles = StyleSheet.create({
  /**
   * 대화 영역과 같은 바탕을 쓴다. 흰색으로 두면 `#FBFBFB`와 미세하게 달라 경계가 실수처럼 보이고,
   * 토리 말풍선이 흰색이라 바탕까지 흰색이면 말풍선이 묻힌다. 구분은 위쪽 실선이 맡는다.
   */
  bar: {
    backgroundColor: colors.background,
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingBottom: spacing[2],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  counter: {
    alignSelf: 'flex-end',
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    marginBottom: spacing[1],
  },
  counterOver: {
    color: colors.primary.orange,
  },
  inputRow: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    gap: spacing[2],
  },
  /**
   * 여러 줄을 허용하되 화면을 다 먹지 않게 상한을 둔다. 세로 패딩을 직접 주는 이유는
   * 안드로이드 기본 `TextInput`이 위아래로 치우쳐 보이기 때문이다.
   */
  input: {
    backgroundColor: colors.white,
    borderColor: colors.divider,
    borderRadius: radius.card,
    borderWidth: 1,
    color: colors.textPrimary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 20,
    maxHeight: 108,
    minHeight: SEND_BUTTON_SIZE,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
    textAlignVertical: 'center',
  },
  sendButton: {
    alignItems: 'center',
    backgroundColor: colors.primary.green,
    borderRadius: radius.round,
    height: SEND_BUTTON_SIZE,
    justifyContent: 'center',
    width: SEND_BUTTON_SIZE,
  },
  sendButtonDisabled: {
    backgroundColor: colors.ledger.softSurface,
  },
  pressed: {
    opacity: 0.72,
  },
});
