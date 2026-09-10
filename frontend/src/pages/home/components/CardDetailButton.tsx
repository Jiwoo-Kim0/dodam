import { SymbolView } from 'expo-symbols';
import { Pressable } from 'react-native';

import { colors, componentSize } from '@/styles';

import { styles } from './CardDetailButton.styles';

interface CardDetailButtonProps {
  accessibilityLabel: string;
  onPress: () => void;
  tintColor?: string;
}

export function CardDetailButton({
  accessibilityLabel,
  onPress,
  tintColor = colors.textPrimary,
}: CardDetailButtonProps) {
  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}
    >
      <SymbolView
        name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
        size={componentSize.icon.small}
        tintColor={tintColor}
      />
    </Pressable>
  );
}
