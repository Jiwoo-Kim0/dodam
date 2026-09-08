import type { PropsWithChildren } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import type { Edge } from 'react-native-safe-area-context';
import { SafeAreaView } from 'react-native-safe-area-context';

import { styles } from './SafeAreaLayout.styles';

const defaultEdges: Edge[] = ['top', 'left', 'right'];

type SafeAreaLayoutProps = PropsWithChildren<{
  edges?: Edge[];
  style?: StyleProp<ViewStyle>;
}>;

export function SafeAreaLayout({ children, edges = defaultEdges, style }: SafeAreaLayoutProps) {
  return (
    <SafeAreaView edges={edges} style={[styles.container, style]}>
      {children}
    </SafeAreaView>
  );
}
