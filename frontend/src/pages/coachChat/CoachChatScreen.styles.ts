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
  revealSurface: {
    flex: 1,
  },
  screen: {
    backgroundColor: colors.background,
  },
  /** 대화 영역과 입력 바가 함께 키보드를 피해야 해서 한 겹으로 묶는다. */
  chatArea: {
    flex: 1,
  },
  /** 공통 헤더의 액션 버튼과 같은 규격이다. */
  headerAction: {
    alignItems: 'center',
    height: 36,
    justifyContent: 'center',
    width: 36,
  },
  /** 인사말이 맨 위에 오고 대화가 아래로 흐른다. 새 답변이 오면 자동으로 끝까지 내려간다. */
  scrollContent: {
    flexGrow: 1,
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
  /** 아바타(32)와 간격(8)을 뺀 나머지를 차지한다. 말풍선은 이 폭 안에서만 커진다. */
  coachContent: {
    alignItems: 'flex-start',
    flex: 1,
    minWidth: 0,
  },
  /**
   * 폭 제한은 `coachContent`가 실제 값으로 건다. 여기에 `maxWidth: '100%'`를 쓰면 부모 폭이
   * 정해지기 전에 글자를 재서 화면 밖으로 넘친다.
   */
  coachBubble: {
    backgroundColor: colors.white,
    borderBottomLeftRadius: radius.small,
    borderRadius: radius.card,
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  coachText: {
    color: colors.textPrimary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 20,
  },
  /** 내가 물은 것. 오른쪽에 붙여 토리 답변과 방향으로 구분한다. */
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
  /** 스크롤 바깥이라 좌우 여백을 직접 준다. 입력창 위 각주라 최대한 작게 둔다. */
  disclaimer: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    paddingBottom: spacing[1],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  /**
   * 첫 인사말 아래에 붙는 예시 질문이다. 아바타 폭(32)과 간격(8)만큼 들여써서 말풍선과 줄을 맞춘다.
   * 토리가 건네는 말이라 왼쪽에 둔다.
   */
  chipList: {
    alignItems: 'flex-start',
    gap: spacing[2],
    marginTop: spacing[2],
    paddingLeft: 40,
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
