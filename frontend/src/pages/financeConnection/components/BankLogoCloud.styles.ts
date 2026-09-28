import { StyleSheet } from 'react-native';

import { spacing } from '@/styles';

export const styles = StyleSheet.create({
  container: {
    width: '100%',
    maxWidth: 350,
    alignSelf: 'center',
    alignItems: 'center',
    gap: spacing[6],
  },
  /** 로고가 흩어지는 영역. 비율을 고정해 화면 크기가 달라져도 구도가 유지된다. */
  scatter: {
    width: '100%',
    aspectRatio: 1.36,
  },
  floatingSlot: {
    position: 'absolute',
  },
  /** 로고 아래에 자리한 캐릭터. 원본 비율(약 1.6:1)을 유지한다. */
  character: {
    width: '58%',
    aspectRatio: 1.6,
  },
});
