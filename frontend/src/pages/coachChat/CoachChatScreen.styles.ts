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
  /** 공통 헤더의 액션 버튼과 같은 규격이다. */
  headerAction: {
    alignItems: 'center',
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  /**
   * `flexGrow`와 아래 정렬을 같이 줘야 대화가 적을 때 아래에 붙고, 많아지면 평범하게 스크롤된다.
   * 위에서부터 쌓으면 아래가 휑해서 채팅으로 안 보인다.
   */
  scrollContent: {
    flexGrow: 1,
    justifyContent: 'flex-end',
    paddingBottom: spacing[4],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[4],
  },
  coachRow: {
    alignItems: 'flex-end',
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  avatar: {
    height: 32,
    width: 32,
  },
  coachContent: {
    flex: 1,
    minWidth: 0,
  },
  coachBubble: {
    alignSelf: 'flex-start',
    backgroundColor: colors.white,
    borderBottomLeftRadius: radius.small,
    borderRadius: radius.card,
    maxWidth: '100%',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  coachText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 20,
  },
  /** 내가 물은 것. 오른쪽에 붙여 도리 답변과 방향으로 구분한다. */
  userBubble: {
    alignSelf: 'flex-end',
    backgroundColor: colors.primary.green,
    borderBottomRightRadius: radius.small,
    borderRadius: radius.card,
    marginTop: spacing[4],
    maxWidth: '84%',
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  userText: {
    color: colors.white,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 19,
  },
  noticeBlock: {
    alignItems: 'flex-start',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    gap: spacing[2],
    marginTop: spacing[3],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  noticeText: {
    ...typography.body2,
    color: colors.textSecondary,
    lineHeight: 19,
  },
  noticeButton: {
    backgroundColor: colors.primary.lightGreen,
    borderRadius: radius.round,
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  noticeButtonText: {
    color: colors.report.scoreStrong,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
  },
  disclaimer: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 15,
    marginTop: spacing[4],
  },
  /** 다음 질문 후보. 내 말풍선과 같은 쪽에 세워 누르면 그대로 올라가는 흐름을 만든다. */
  chipList: {
    alignItems: 'flex-end',
    gap: spacing[2],
    marginTop: spacing[4],
  },
  chip: {
    backgroundColor: colors.white,
    borderColor: colors.primary.green,
    borderRadius: radius.round,
    borderWidth: 1,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[2],
  },
  chipDisabled: {
    backgroundColor: colors.ledger.softSurface,
    borderColor: colors.divider,
  },
  chipText: {
    color: colors.primary.green,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  chipTextDisabled: {
    color: colors.textTertiary,
  },
  pressed: {
    opacity: 0.62,
  },
});
