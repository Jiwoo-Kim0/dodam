import { StyleSheet } from 'react-native';

import { colors, radius, spacing } from '@/styles';

const LOGO_SIZE = 56;

export const styles = StyleSheet.create({
  container: {
    width: '100%',
    maxWidth: 350,
    alignSelf: 'center',
    justifyContent: 'center',
    gap: spacing[5],
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
  },
  middleRow: {
    minHeight: 132,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  sideGroup: {
    gap: spacing[5],
  },
  characterSpace: {
    width: 132,
    height: 132,
  },
  logoContainer: {
    width: LOGO_SIZE,
    height: LOGO_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.round,
    backgroundColor: colors.white,
    shadowColor: colors.textPrimary,
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.08,
    shadowRadius: 8,
    elevation: 2,
  },
  logo: {
    width: 44,
    height: 44,
    resizeMode: 'contain',
  },
});
