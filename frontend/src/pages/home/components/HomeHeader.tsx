import { Image } from 'expo-image';
import { SymbolView } from 'expo-symbols';
import { View } from 'react-native';

import { colors, componentSize } from '@/styles';

import { styles } from './HomeHeader.styles';

export function HomeHeader() {
  return (
    <View style={styles.container}>
      <Image
        contentFit="contain"
        source={require('@/assets/logos/dodam_logo_image.png')}
        style={styles.logo}
      />

      {/* 알림·메뉴 화면이 아직 없어 임시로 비활성 아이콘만 노출한다. 화면이 추가되면 Pressable + onPress로 라우팅을 연결한다. */}
      <View style={styles.actions}>
        <View
          accessible={false}
          importantForAccessibility="no-hide-descendants"
          style={styles.actionButton}>
          <SymbolView
            name={{ android: 'notifications', ios: 'bell', web: 'notifications' }}
            size={componentSize.icon.small + 4}
            tintColor={colors.textPrimary}
          />
        </View>

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
