import { StyleSheet } from 'react-native';

import {
  colors,
  radius,
  spacing,
  typography,
} from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
  },
  heroImage: {
    width: 140,
    height: 140,
  },
  optionList: {
    width: '100%',
    marginTop: spacing[8],
    gap: spacing[4],
  },
  option: {
    minHeight: 104,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing[4],
    borderWidth: 1,
    borderColor: colors.divider,
    borderRadius: radius.default,
    backgroundColor: colors.white,
    shadowColor: colors.textPrimary,
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.08,
    shadowRadius: 6,
    elevation: 2,
  },
  selectedOption: {
    borderColor: colors.primary.green,
  },
  pressedOption: {
    opacity: 0.8,
  },
  optionImage: {
    width: 64,
    height: 64,
  },
  optionText: {
    flex: 1,
    marginLeft: spacing[4],
    gap: spacing[1],
  },
  optionTitle: {
    ...typography.subtitle,
    color: colors.textPrimary,
  },
  optionDescription: {
    ...typography.body2,
    color: colors.textSecondary,
  },
  chevron: {
    color: colors.textSecondary,
    fontSize: 28,
  },
});