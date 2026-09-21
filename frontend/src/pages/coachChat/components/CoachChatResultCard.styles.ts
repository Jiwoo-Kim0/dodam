import { StyleSheet } from 'react-native';

import { colors, fontFamily, fontSize, radius, spacing } from '@/styles';

export const styles = StyleSheet.create({
  /** 말풍선은 내용만큼만 커지지만 결과 카드는 남는 폭을 다 쓴다. */
  card: {
    alignSelf: 'stretch',
    backgroundColor: colors.white,
    borderRadius: radius.card,
    marginTop: spacing[1],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  sectionDivider: {
    backgroundColor: colors.divider,
    height: StyleSheet.hairlineWidth,
    marginVertical: spacing[3],
  },
  /** 카드 안 구분 라벨이다. 값이 주인공이라 한 단계 낮춘다. */
  sectionTitle: {
    color: colors.textTertiary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.detail,
    lineHeight: 15,
  },
  factList: {
    gap: spacing[2],
    marginTop: spacing[2],
  },
  /**
   * 라벨이 길다(`현재 예상 달성일(노트북 바꾸기)`). 라벨 폭을 묶으면 단어 중간에서 줄바꿈되므로
   * 라벨이 남는 폭을 쓰게 두고, 값은 줄지 않되 절반은 넘지 않게 한다.
   */
  factRow: {
    alignItems: 'flex-start',
    flexDirection: 'row',
    gap: spacing[3],
    justifyContent: 'space-between',
  },
  factLabel: {
    color: colors.textSecondary,
    flex: 1,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 18,
    minWidth: 0,
  },
  /** 서버가 계산한 값이다. 라벨보다 또렷해야 한다. */
  factValue: {
    color: colors.textPrimary,
    flexShrink: 0,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    letterSpacing: -0.3,
    lineHeight: 18,
    maxWidth: '50%',
    textAlign: 'right',
  },
  failureBlock: {
    backgroundColor: colors.discovery.insightIncreaseTag,
    borderRadius: radius.default,
    gap: spacing[1],
    marginTop: spacing[3],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[2],
  },
  failureTitle: {
    color: colors.primary.brown,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.caption,
    lineHeight: 16,
  },
  failureText: {
    color: colors.primary.brown,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
  },
  /** 보조 조작이다. 결과 값보다 튀면 안 된다. */
  detailButton: {
    alignItems: 'center',
    borderTopColor: colors.divider,
    borderTopWidth: StyleSheet.hairlineWidth,
    height: 36,
    justifyContent: 'center',
    marginTop: spacing[3],
  },
  detailPressed: {
    opacity: 0.62,
  },
  detailText: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.caption,
  },
  /** 펼쳐야 보이는 한 겹 안쪽 내용이다. 카드가 흰색이라 여기만 색을 줘 깊이를 만든다. */
  detailBlock: {
    backgroundColor: colors.report.patternSurface,
    borderRadius: radius.default,
    gap: spacing[1],
    paddingHorizontal: spacing[3],
    paddingVertical: spacing[3],
  },
  detailTitle: {
    color: colors.textPrimary,
    fontFamily: fontFamily.semiBold,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  detailSummary: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.caption,
    lineHeight: 17,
  },
  detailNotice: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: fontSize.body2,
    lineHeight: 19,
  },
});
