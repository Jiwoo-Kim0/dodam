import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';

import { styles } from './ScreenState.styles';

interface ScreenStateProps {
  description: string;
  isLoading?: boolean;
  title?: string;
  onRetry?: () => void;
}

export function ScreenState({
  description,
  isLoading = false,
  title,
  onRetry,
}: ScreenStateProps) {
  return (
    <View style={styles.container}>
      {isLoading ? (
        <ActivityIndicator color={colors.primary.green} size="large" />
      ) : null}
      {title ? <Text style={styles.title}>{title}</Text> : null}
      <Text accessibilityRole={onRetry ? 'alert' : undefined} style={styles.description}>
        {description}
      </Text>
      {onRetry ? (
        <Pressable
          accessibilityRole="button"
          onPress={onRetry}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}>
          <Text style={styles.retryLabel}>다시 시도</Text>
        </Pressable>
      ) : null}
    </View>
  );
}
