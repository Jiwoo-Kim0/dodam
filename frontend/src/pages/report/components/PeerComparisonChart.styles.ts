import { StyleSheet } from 'react-native';

/** 축 라벨까지 포함한 정사각 영역이다. 작은 Android 화면에서도 카드 안에 들어가는 크기로 잡았다. */
export const CHART_SIZE = 196;

export const styles = StyleSheet.create({
  chartFrame: {
    alignItems: 'center',
    height: CHART_SIZE,
    justifyContent: 'center',
    width: CHART_SIZE,
  },
});
