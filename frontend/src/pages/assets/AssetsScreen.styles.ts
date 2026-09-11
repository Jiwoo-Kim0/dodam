import { StyleSheet } from 'react-native';

import {
  colors,
  semanticSpacing,
  spacing,
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
  sections: {
    gap: spacing[5],
    marginTop: spacing[6],
  },
});
