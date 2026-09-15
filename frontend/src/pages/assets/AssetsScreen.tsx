import { View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { MOCK_ASSET_OVERVIEW, MOCK_ASSET_SECTIONS } from '@/mocks/assets';

import { AssetOverviewCard } from './components/AssetOverviewCard';
import { AssetSection } from './components/AssetSection';
import { styles } from './AssetsScreen.styles';

export function AssetsScreen() {
  return (
    <SafeAreaLayout
      edges={['top', 'left', 'right']}
      style={styles.screen}
    >
      <AppHeader showBackButton title="내 자산" />

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        <AssetOverviewCard overview={MOCK_ASSET_OVERVIEW} />

        <View style={styles.sections}>
          {MOCK_ASSET_SECTIONS.map((section) => (
            <AssetSection key={section.id} section={section} />
          ))}
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
