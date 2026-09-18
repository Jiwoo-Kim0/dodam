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
  /** 눈금 위에 겹쳐 두고 통째로 확대하는 층이다. 확대 기준점은 뷰 중앙이라 좌표 보정이 필요 없다. */
  growLayer: {
    height: CHART_SIZE,
    left: 0,
    position: 'absolute',
    top: 0,
    width: CHART_SIZE,
  },
});
