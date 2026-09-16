import { SymbolView } from 'expo-symbols';
import { View } from 'react-native';

import { colors, componentSize } from '@/styles';

import { styles } from './CardDetailButton.styles';

interface CardDetailButtonProps {
  tintColor?: string;
}

export function CardDetailButton({
  tintColor = colors.textPrimary,
}: CardDetailButtonProps) {
  return (
    <View
      pointerEvents="none"
      style={styles.button}
    >
      <SymbolView
        name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
        size={componentSize.icon.small}
        tintColor={tintColor}
      />
    </View>
  );
}
