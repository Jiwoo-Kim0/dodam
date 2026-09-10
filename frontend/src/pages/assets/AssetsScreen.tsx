import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { MOCK_ASSET_SECTIONS } from '@/mocks/assets';
import { colors } from '@/styles';

import { AssetOverviewCard } from './components/AssetOverviewCard';
import { AssetSection } from './components/AssetSection';
import { styles } from './AssetsScreen.styles';

export function AssetsScreen() {
  const router = useRouter();

  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right']}
      style={styles.screen}
    >
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="이전 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={() => router.back()}
          style={({ pressed }) => [
            styles.backButton,
            pressed && styles.backButtonPressed,
          ]}
        >
          <SymbolView
            name={{
              android: 'arrow_back',
              ios: 'chevron.left',
              web: 'arrow_back',
            }}
            size={24}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>

      <BouncyScrollView contentContainerStyle={styles.scrollContent}>
        <Text style={styles.title}>내 자산</Text>

        <AssetOverviewCard />

        <View style={styles.sections}>
          {MOCK_ASSET_SECTIONS.map((section) => (
            <AssetSection key={section.id} section={section} />
          ))}
        </View>
      </BouncyScrollView>
    </SafeAreaLayout>
  );
}
