import type { ReactNode } from 'react';
import { StatusBar } from 'expo-status-bar';
import { Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { styles } from './AuthScreenLayout.styles';

interface AuthScreenLayoutProps {
  title: string;
  children: ReactNode;
}

export function AuthScreenLayout({
  title,
  children,
}: AuthScreenLayoutProps) {
  return (
    <SafeAreaView
      edges={['left', 'right', 'bottom']}
      style={styles.container}
    >
      <StatusBar style="dark" />

      <Text style={styles.title}>{title}</Text>

      <View style={styles.content}>{children}</View>
    </SafeAreaView>
  );
}