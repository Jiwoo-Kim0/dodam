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
  scrollContent: {
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  monthSelector: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[1],
  },
  monthLabel: {
    color: colors.textPrimary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body1,
    lineHeight: 20,
  },
  /** 리포트를 여는 한 줄 인사다. 카드 안에서 캐릭터와 문구를 가로로 붙여 둔다. */
  introCard: {
    alignItems: 'center',
    flexDirection: 'row',
    gap: spacing[3],
    paddingHorizontal: spacing[4],
    paddingVertical: spacing[3],
  },
  introCharacter: {
    height: 40,
    width: 40,
  },
  introText: {
    ...typography.body2,
    color: colors.textSecondary,
    flex: 1,
    lineHeight: 19,
  },
  sections: {
    gap: spacing[3],
    marginTop: spacing[3],
  },
  card: {
    backgroundColor: colors.white,
    borderRadius: radius.card,
  },
  /**
   * 카드 제목은 카드 안에서 무엇을 보는지 알려 주는 라벨로 두고, 시선은 그 아래 숫자·그래프가 가져가게
   * 한다. 홈 소비 TOP 3, 가계부 쓴 돈·소비 TOP 3, 자산 점검도 같은 규칙을 쓴다.
   */
  cardTitle: {
    color: colors.textSecondary,
    fontFamily: fontFamily.medium,
    fontSize: fontSize.body2,
    lineHeight: 18,
  },
  cardDescription: {
    color: colors.textSecondary,
    fontFamily: fontFamily.regular,
    fontSize: 11,
    lineHeight: 15,
    marginTop: 2,
  },
});
