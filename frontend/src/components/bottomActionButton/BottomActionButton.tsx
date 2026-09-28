import { Pressable, Text } from 'react-native';

import { styles } from './BottomActionButton.styles';

interface BottomActionButtonProps {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}

export function BottomActionButton({
  label,
  onPress,
  disabled = false,
}: BottomActionButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      style={({ pressed }) => [
        styles.button,
        disabled && styles.disabled,
        pressed && !disabled && styles.pressed,
      ]}>
      <Text style={styles.label}>{label}</Text>
    </Pressable>
  );
}
