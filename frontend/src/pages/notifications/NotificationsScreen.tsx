import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { RefreshableBouncyScrollView } from '@/components/bouncyScrollView/RefreshableBouncyScrollView';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { NOTIFICATIONS } from '@/mocks/notifications';
import { colors } from '@/styles';

import { NotificationCard } from './components/NotificationCard';
import { styles } from './NotificationsScreen.styles';

export function NotificationsScreen() {
  const router = useRouter();

  const handleBackPress = () => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  };

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
      <AppHeader
        onBackPress={handleBackPress}
        right={
          // 알림 설정 화면이 아직 없어 임시로 비활성 아이콘만 노출한다. 화면이 추가되면 Pressable + onPress로 라우팅을 연결한다.
          <View
            accessible={false}
            importantForAccessibility="no-hide-descendants"
            style={styles.settingsButton}>
            <SymbolView
              name={{ android: 'settings', ios: 'gearshape.fill', web: 'settings' }}
              size={22}
              tintColor={colors.textPrimary}
            />
          </View>
        }
        showBackButton
        title="알림"
      />

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.list}>
          {NOTIFICATIONS.map((notification) => (
            <NotificationCard key={notification.id} notification={notification} />
          ))}
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
