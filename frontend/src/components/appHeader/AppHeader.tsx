import type { ReactNode } from 'react';

import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import { colors } from '@/styles';

import { styles } from './AppHeader.styles';

type AppHeaderProps = {
  title: string;
  left?: ReactNode;
  right?: ReactNode;
  subtitle?: string;
  showBackButton?: boolean;
  titleAccessory?: ReactNode;
  onBackPress?: () => void;
};

export function AppHeader({
  title,
  left,
  right,
  subtitle,
  showBackButton = false,
  titleAccessory,
  onBackPress,
}: AppHeaderProps) {
  const router = useRouter();

  const handleBackPress = () => {
    if (onBackPress) {
      onBackPress();
      return;
    }

    router.back();
  };

  const leftContent = showBackButton ? (
    <Pressable
      accessibilityLabel="이전 화면으로 이동"
      accessibilityRole="button"
      hitSlop={8}
      onPress={handleBackPress}
      style={styles.actionButton}>
      <SymbolView
        name={{ android: 'arrow_back', ios: 'chevron.left', web: 'arrow_back' }}
        size={24}
        tintColor={colors.textPrimary}
      />
    </Pressable>
  ) : (
    left
  );

  return (
    <View style={styles.container}>
      <View style={styles.side}>{leftContent}</View>
      <View style={styles.titleGroup}>
        <View style={styles.titleLine}>
          <Text numberOfLines={1} style={styles.title}>
            {title}
          </Text>
          {titleAccessory}
        </View>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      <View style={[styles.side, styles.right]}>{right}</View>
    </View>
  );
}
