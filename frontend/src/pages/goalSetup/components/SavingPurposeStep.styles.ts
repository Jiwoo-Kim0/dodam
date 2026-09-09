import { StyleSheet } from 'react-native';

import {
  colors,
  componentSize,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  input: {
    ...typography.body1,
    minHeight: componentSize.button.default,
    paddingHorizontal: spacing[4],
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.default,
    color: colors.textPrimary,
    backgroundColor: colors.white,
  },
  suggestionList: {
    flexDirection: 'row',
    gap: spacing[2],
    marginTop: spacing[3],
  },
  suggestion: {
    flex: 1,
    minHeight: spacing[8],
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.default,
    backgroundColor: colors.white,
  },
  selectedSuggestion: {
    borderColor: colors.primary.green,
    backgroundColor: colors.primary.lightGreen,
  },
  suggestionLabel: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  selectedSuggestionLabel: {
    color: colors.primary.green,
  },
});
