import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  fontFamily,
  fontSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  row: {
    alignItems: 'center',
    backgroundColor: colors.white,
    borderColor: 'transparent',
    borderRadius: radius.card,
    borderWidth: componentSize.icon.stroke,
    flexDirection: 'row',
    gap: spacing[3],
    minHeight: 76,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /**
   * 선택 표시는 테두리와 배경으로만 준다. 별도 체크 아이콘을 두면 브랜드 로고와 함께
   * 한 줄에 원형 요소가 둘이 되어 시선이 갈린다.
   */
  rowSelected: {
    backgroundColor: colors.primary.lightGreen,
    borderColor: colors.primary.green,
  },
  rowPressed: {
    opacity: componentSize.bottomSheet.pressedOpacity,
  },
  serviceIcon: {
    height: 44,
    width: 44,
  },
  nameGroup: {
    flex: 1,
    flexShrink: 1,
  },
  name: {
    ...typography.subtitle,
    color: colors.textPrimary,
    lineHeight: 24,
  },
  cycleNote: {
    ...typography.caption,
    color: colors.textSecondary,
    lineHeight: 16,
    marginTop: 2,
  },
  amount: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.subtitle,
    lineHeight: 24,
  },
  /** 금액이 주인공이라 단위는 한 단계 작고 연하게 둔다. */
  amountUnit: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
});
