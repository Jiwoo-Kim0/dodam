import { SymbolView } from 'expo-symbols';
import { Pressable } from 'react-native';

import { colors, componentSize } from '@/styles';

import { styles } from './CardDetailButton.styles';

interface CardDetailButtonProps {
  accessibilityLabel: string;
  centerVertically?: boolean;
  onPress: () => void;
  tintColor?: string;
}

export function CardDetailButton({
  accessibilityLabel,
  centerVertically = false,
  onPress,
  tintColor = colors.textPrimary,
}: CardDetailButtonProps) {
  return (
    <Pressable
      accessibilityLabel={accessibilityLabel}
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        centerVertically && styles.centered,
        pressed && styles.pressed,
      ]}
    >
      <SymbolView
        name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
        size={componentSize.icon.small}
        tintColor={tintColor}
      />
    </Pressable>
  );
}
