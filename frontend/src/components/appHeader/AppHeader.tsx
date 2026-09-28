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
  backDisabled?: boolean;
  titleAccessory?: ReactNode;
  onBackPress?: () => void;
  sideWidth?: number;
};

export function AppHeader({
  title,
  left,
  right,
  subtitle,
  showBackButton = false,
  backDisabled = false,
  titleAccessory,
  onBackPress,
  sideWidth,
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
      accessibilityState={{ disabled: backDisabled }}
      disabled={backDisabled}
      hitSlop={8}
      onPress={handleBackPress}
      style={[styles.actionButton, backDisabled && styles.actionButtonDisabled]}>
      <SymbolView
        name={{ android: 'arrow_back', ios: 'chevron.left', web: 'arrow_back' }}
        size={24}
        tintColor={backDisabled ? colors.textTertiary : colors.textPrimary}
      />
    </Pressable>
  ) : (
    left
  );

  return (
    <View style={styles.container}>
      <View style={[styles.side, sideWidth ? { width: sideWidth } : undefined]}>{leftContent}</View>
      <View style={styles.titleGroup}>
        <View style={styles.titleLine}>
          <Text numberOfLines={1} style={styles.title}>
            {title}
          </Text>
          {titleAccessory}
        </View>
        {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
      </View>
      <View style={[styles.side, styles.right, sideWidth ? { width: sideWidth } : undefined]}>{right}</View>
    </View>
  );
}
