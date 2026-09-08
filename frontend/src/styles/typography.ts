import type { TextStyle } from 'react-native';

/**
 * expo-font로 등록할 Pretendard fontFamily 이름이다.
 * 폰트 로딩 시 아래 이름과 동일한 키를 사용한다.
 */
export const fontFamily = {
  regular: 'Pretendard-Regular',
  medium: 'Pretendard-Medium',
  semiBold: 'Pretendard-SemiBold',
  bold: 'Pretendard-Bold',
} as const;

export const fontSize = {
  display: 34,
  headline: 26,
  title: 22,
  subtitle: 18,
  button: 20,
  buttonSmall: 15,
  body1: 14,
  body2: 13,
  caption: 12,
} as const;

/** 도담 디자인 가이드의 텍스트 역할별 스타일 */
export const typography = {
  input: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.button,
  },
  display: {
    fontFamily: fontFamily.bold,
    fontSize: fontSize.display,
  },
  headline: {
    fontFamily: fontFamily.bold,
    fontSize: fontSize.headline,
  },
  title: {
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
  },
  subtitle: {
    fontFamily: fontFamily.medium,
    fontSize: fontSize.subtitle,
  },
  body1: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body1,
  },
  body2: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
  },
  caption: {
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  button: {
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.button,
  },
  buttonSmall: {
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.buttonSmall,
    lineHeight: 20,
  },
} as const satisfies Record<string, TextStyle>;
