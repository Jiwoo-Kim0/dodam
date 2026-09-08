import { useRouter } from 'expo-router';
import { Pressable, Text } from 'react-native';

import { styles } from './AuthBackButton.styles';

interface AuthBackButtonProps {
  onPress?: () => void;
}

export function AuthBackButton({ onPress }: AuthBackButtonProps) {
  const router = useRouter();

  return (
    <Pressable
      onPress={onPress ?? router.back}
      accessibilityRole="button"
      accessibilityLabel="뒤로 가기"
      hitSlop={12}
      style={styles.button}>
      <Text style={styles.icon}>‹</Text>
    </Pressable>
  );
}
