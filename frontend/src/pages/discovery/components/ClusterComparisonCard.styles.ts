import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing, typography } from '@/styles';

/** 막대 위 표식을 가운데 정렬하려고 폭의 절반만큼 왼쪽으로 민다. */
const USER_DOT_SIZE = 12;

export const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
    marginTop: spacing[6],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[4],
  },
  /** 상세 화면의 다른 카드 제목과 같은 규칙이다. */
  title: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  summary: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body1,
    lineHeight: 20,
    marginTop: spacing[1],
  },
  summaryMuted: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  valueRow: {
    alignItems: 'baseline',
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: spacing[3],
  },
  featureLabel: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
  },
  userValue: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.title,
    letterSpacing: -0.6,
    lineHeight: 28,
  },
  track: {
    backgroundColor: colors.home.goalProgressTrack,
    borderRadius: radius.round,
    height: 8,
    marginTop: spacing[2],
    position: 'relative',
    width: '100%',
  },
  /** 군집의 5~95분위. 여기 안이면 또래와 비슷하다는 뜻이다. */
  band: {
    backgroundColor: colors.report.scoreLight,
    borderRadius: radius.round,
    bottom: 0,
    position: 'absolute',
    top: 0,
  },
  /** 회원님 위치. 기준 범위 밖으로 나가도 보이도록 트랙보다 크게 그린다. */
  userDot: {
    backgroundColor: colors.primary.orange,
    borderColor: colors.white,
    borderRadius: radius.round,
    borderWidth: 2,
    height: USER_DOT_SIZE,
    marginLeft: -(USER_DOT_SIZE / 2),
    position: 'absolute',
    top: -(USER_DOT_SIZE - 8) / 2,
    width: USER_DOT_SIZE,
  },
  legendRow: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[2],
    justifyContent: 'space-between',
    marginTop: spacing[3],
  },
  legendText: {
    ...typography.caption,
    color: colors.textSecondary,
    flexShrink: 1,
  },
  rankText: {
    color: colors.primary.orange,
    flexShrink: 0,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.caption,
  },
  /** 띠를 어떻게 읽는지 알려 주는 한 줄. 범례 바로 아래에 붙여 함께 읽히게 한다. */
  guideText: {
    ...typography.caption,
    color: colors.textTertiary,
    lineHeight: 17,
    marginTop: spacing[1],
  },
  footnote: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
    marginTop: spacing[3],
  },
  /** 평소엔 닫아 두는 보조 정보라 눌리는 곳임만 알 정도로 약하게 둔다. */
  basisToggle: {
    alignItems: 'center',
    alignSelf: 'flex-start',
    flexDirection: 'row',
    gap: spacing[1],
    marginTop: spacing[1],
    paddingVertical: spacing[1],
  },
  basisTogglePressed: {
    opacity: 0.62,
  },
  basisToggleText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 14,
  },
  basisText: {
    color: colors.textTertiary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.micro,
    lineHeight: 15,
  },
});
