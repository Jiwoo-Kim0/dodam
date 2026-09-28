import { StyleSheet } from 'react-native';

import { colors, componentSize, radius, spacing, typography } from '@/styles';

export const styles = StyleSheet.create({
  button: {
    width: '100%',
    height: componentSize.button.compact,
    alignItems: 'center',
    justifyContent: 'center',
  },

  content: {
    width: '100%',
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },

  iconSlot: {
    position: 'absolute',
    left: spacing[5],
    width: 20,
    height: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },

  icon: {
    width: 20,
    height: 20,
  },

  kakaoIcon: {
    width: 20,
    height: 20,
  },

  googleIcon: {
    width: 20,
    height: 20,
  },

  naverIcon: {
    width: 18,
    height: 18,
  },

  label: {
    ...typography.buttonSmall,
  },

  kakaoButton: {
    backgroundColor: '#FEE500',
    borderRadius: radius.default,
  },

  kakaoLabel: {
    color: 'rgba(0, 0, 0, 0.85)',
  },

  googleButton: {
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: '#747775',
    borderRadius: radius.default,
  },

  googleLabel: {
    color: '#1F1F1F',
  },

  naverButton: {
    backgroundColor: '#03A94D',
    borderRadius: radius.default,
  },

  naverLabel: {
    color: colors.white,
  },

  pressed: {
    opacity: 0.8,
  },

  selectedButton: {
    borderWidth: 2,
    borderColor: colors.primary.green,
  },
});
