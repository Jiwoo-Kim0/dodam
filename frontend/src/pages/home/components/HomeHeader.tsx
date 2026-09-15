import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { Pressable, View } from 'react-native';

import { colors, componentSize } from '@/styles';

import { styles } from './HomeHeader.styles';

interface HomeHeaderProps {
  onLogoPress: () => void;
  onNotificationPress: () => void;
}

export function HomeHeader({ onLogoPress, onNotificationPress }: HomeHeaderProps) {
  return (
    <View style={styles.container}>
      <Pressable
        accessibilityLabel="홈 새로고침"
        accessibilityRole="button"
        hitSlop={8}
        onPress={onLogoPress}>
        <Image
          contentFit="contain"
          source={require('@/assets/logos/dodam_logo_image.png')}
          style={styles.logo}
        />
      </Pressable>

      <View style={styles.actions}>
        <Pressable
          accessibilityLabel="알림 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={onNotificationPress}
          style={({ pressed }) => [styles.actionButton, pressed && styles.actionButtonPressed]}>
          <SymbolView
            name={{ android: 'notifications', ios: 'bell', web: 'notifications' }}
            size={componentSize.icon.small + 4}
            tintColor={colors.textPrimary}
          />
        </Pressable>

        {/* 메뉴 화면이 아직 없어 임시로 비활성 아이콘만 노출한다. 화면이 추가되면 Pressable + onPress로 라우팅을 연결한다. */}
        <View
          accessible={false}
          importantForAccessibility="no-hide-descendants"
          style={styles.actionButton}>
          <SymbolView
            name={{ android: 'menu', ios: 'line.horizontal.3', web: 'menu' }}
            size={componentSize.icon.small + 4}
            tintColor={colors.textPrimary}
          />
        </View>
      </View>
    </View>
  );
}
