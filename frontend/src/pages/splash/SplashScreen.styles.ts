import { StyleSheet } from 'react-native';

import { colors } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.white,
  },

  logo: {
    width: 400,
    height: 400,
    transform: [{ translateY: -60 }],
  },
});
