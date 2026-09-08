import type { PropsWithChildren, ReactNode } from 'react';
import type { StyleProp, ViewStyle } from 'react-native';
import { View } from 'react-native';
import type { Edge } from 'react-native-safe-area-context';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { styles } from './MainLayout.styles';

type MainLayoutProps = PropsWithChildren<{
  title?: string;
  headerLeft?: ReactNode;
  headerRight?: ReactNode;
  showBackButton?: boolean;
  onBackPress?: () => void;
  padded?: boolean;
  safeAreaEdges?: Edge[];
  contentStyle?: StyleProp<ViewStyle>;
}>;

export function MainLayout({
  children,
  title,
  headerLeft,
  headerRight,
  showBackButton,
  onBackPress,
  padded = true,
  safeAreaEdges,
  contentStyle,
}: MainLayoutProps) {
  return (
    <SafeAreaLayout edges={safeAreaEdges}>
      {title ? (
        <AppHeader
          left={headerLeft}
          onBackPress={onBackPress}
          right={headerRight}
          showBackButton={showBackButton}
          title={title}
        />
      ) : null}
      <View style={[styles.content, padded && styles.padded, contentStyle]}>{children}</View>
    </SafeAreaLayout>
  );
}
