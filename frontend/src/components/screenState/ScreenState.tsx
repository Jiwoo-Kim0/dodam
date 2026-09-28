import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';

import { styles } from './ScreenState.styles';

interface ScreenStateProps {
  actionLabel?: string;
  description: string;
  isLoading?: boolean;
  onAction?: () => void;
  title?: string;
  onRetry?: () => void;
}

export function ScreenState({
  actionLabel,
  description,
  isLoading = false,
  onAction,
  title,
  onRetry,
}: ScreenStateProps) {
  const handleAction = onAction ?? onRetry;

  return (
    <View style={styles.container}>
      {isLoading ? (
        <ActivityIndicator color={colors.primary.green} size="large" />
      ) : null}
      {title ? <Text style={styles.title}>{title}</Text> : null}
      <Text accessibilityRole={onRetry ? 'alert' : undefined} style={styles.description}>
        {description}
      </Text>
      {handleAction ? (
        <Pressable
          accessibilityRole="button"
          onPress={handleAction}
          style={({ pressed }) => [styles.retryButton, pressed && styles.pressed]}>
          <Text style={styles.retryLabel}>{actionLabel ?? '다시 시도'}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}
