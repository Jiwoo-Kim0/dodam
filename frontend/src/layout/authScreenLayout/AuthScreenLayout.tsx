import type { ReactNode } from 'react';
import { Text, View } from 'react-native';

import { AuthBackButton } from '@/components/authBackButton/AuthBackButton';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { styles } from './AuthScreenLayout.styles';

interface AuthScreenLayoutProps {
  title: string;
  description?: string;
  headerAccessory?: ReactNode;
  showBackButton?: boolean;
  onBackPress?: () => void;
  footer?: ReactNode;
  children: ReactNode;
}

export function AuthScreenLayout({
  title,
  description,
  headerAccessory,
  showBackButton = false,
  onBackPress,
  footer,
  children,
}: AuthScreenLayoutProps) {
  return (
    <SafeAreaLayout
      edges={['left', 'right', 'bottom']}
      style={styles.container}>
      {showBackButton ? <AuthBackButton onPress={onBackPress} /> : null}

      {headerAccessory}

      <Text style={styles.title}>{title}</Text>

      {description ? (
        <Text style={styles.description}>{description}</Text>
      ) : null}

      <View style={styles.content}>{children}</View>

      {footer}
    </SafeAreaLayout>
  );
}
