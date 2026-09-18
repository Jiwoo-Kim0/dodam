import { StyleSheet } from 'react-native';

import {
  colors,
  semanticSpacing,
  spacing,
} from '@/styles';

export const styles = StyleSheet.create({
  screen: {
    backgroundColor: colors.background,
    flex: 1,
  },
  /** 카드 간격은 여기서만 정한다. 카드가 스스로 바깥 여백을 갖고 있으면 순서가 바뀔 때 어긋난다. */
  scrollContent: {
    gap: spacing[3],
    paddingBottom: spacing[8],
    paddingHorizontal: semanticSpacing.screenMargin,
    paddingTop: spacing[2],
  },
  sections: {
    gap: spacing[3],
  },
});
