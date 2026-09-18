import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';

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
  /**
   * 입력칸이 있는 화면에서 켠다. 본문을 스크롤 영역으로 만들고 키보드를 피해 올려 준다.
   * 높이를 `flex: 1`로 잡는 자식이 있으면 스크롤 안에서 찌그러지므로 그런 화면에서는 켜지 않는다.
   */
  avoidKeyboard?: boolean;
  children: ReactNode;
}

export function AuthScreenLayout({
  title,
  description,
  headerAccessory,
  showBackButton = false,
  onBackPress,
  footer,
  avoidKeyboard = false,
  children,
}: AuthScreenLayoutProps) {
  const header = (
    <>
      {showBackButton ? <AuthBackButton onPress={onBackPress} /> : null}

      {headerAccessory}

      <Text style={styles.title}>{title}</Text>

      {description ? (
        <Text style={styles.description}>{description}</Text>
      ) : null}
    </>
  );

  if (avoidKeyboard) {
    return (
      <SafeAreaLayout edges={['left', 'right', 'bottom']} style={styles.container}>
        <KeyboardAvoidingView
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.keyboardArea}>
          <ScrollView
            contentContainerStyle={styles.scrollContent}
            keyboardShouldPersistTaps="handled"
            showsVerticalScrollIndicator={false}>
            {header}
            <View style={styles.scrollableContent}>{children}</View>
          </ScrollView>

          {footer}
        </KeyboardAvoidingView>
      </SafeAreaLayout>
    );
  }

  return (
    <SafeAreaLayout
      edges={['left', 'right', 'bottom']}
      style={styles.container}>
      {header}

      <View style={styles.content}>{children}</View>

      {footer}
    </SafeAreaLayout>
  );
}
