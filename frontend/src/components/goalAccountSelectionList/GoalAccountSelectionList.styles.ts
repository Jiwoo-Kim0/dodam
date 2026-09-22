import { StyleSheet } from 'react-native';

import {
  colors,
  fontFamily,
  fontSize,
  radius,
  spacing,
  typography,
} from '@/styles';

/** 로고가 있든 없든 글자 시작점이 같아야 해서 자리 크기를 한 곳에서 잡는다. */
const LOGO_SIZE = 32;
const CHECK_SIZE = 18;

export const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  sectionTitle: {
    ...typography.body1,
    fontFamily: fontFamily.semiBold,
    color: colors.textPrimary,
  },
  /** 제목 바로 아래 붙는 보조 설명이라 제목과의 간격을 목록보다 좁게 둔다. */
  sectionHint: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: spacing[1],
    marginBottom: spacing[3],
  },
  emptyText: {
    ...typography.caption,
    color: colors.textTertiary,
    paddingVertical: spacing[4],
  },
  accountList: {
    gap: spacing[2],
  },
  /** 두 줄이 들어가므로 높이를 고정하지 않고 세로 여백으로 맞춘다. */
  accountCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing[3],
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.default,
    backgroundColor: colors.white,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  /** 고른 계좌는 테두리와 바탕을 같이 바꿔야 목록에서 한눈에 구분된다. */
  selectedAccountCard: {
    borderColor: colors.primary.green,
    backgroundColor: colors.primary.lightGreen,
  },
  pressedCard: {
    opacity: 0.72,
  },
  logoSlot: {
    width: LOGO_SIZE,
    height: LOGO_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
  },
  bankLogo: {
    width: LOGO_SIZE,
    height: LOGO_SIZE,
    resizeMode: 'contain',
  },
  /** 로고를 못 찾은 은행 자리. 빈칸보다 낫고 로고들과 크기가 같아 줄이 맞는다. */
  logoFallback: {
    width: LOGO_SIZE,
    height: LOGO_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.round,
    backgroundColor: colors.background,
  },
  logoFallbackText: {
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
    color: colors.textSecondary,
  },
  accountInfo: {
    flex: 1,
    minWidth: 0,
    gap: spacing[1],
  },
  /** 은행명이 길면 줄임표로 자른다. 폭은 부모(`accountInfo`)가 잡는다. */
  bankName: {
    ...typography.body1,
    fontFamily: fontFamily.medium,
    color: colors.textPrimary,
  },
  /** 카드에서 가장 먼저 읽히는 값이라 또렷하게 둔다. 줄지 않아 항상 끝까지 보인다. */
  balance: {
    flexShrink: 0,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    color: colors.textPrimary,
    letterSpacing: -0.3,
  },
  accountNumber: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  checkCircle: {
    width: CHECK_SIZE,
    height: CHECK_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.round,
    borderWidth: 1,
    borderColor: colors.border,
  },
  selectedCheckCircle: {
    borderColor: colors.primary.green,
    backgroundColor: colors.primary.green,
  },
});
