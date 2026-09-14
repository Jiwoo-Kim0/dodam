import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Pressable, Text, View } from 'react-native';

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
      <View style={styles.header}>
        <Pressable
          accessibilityLabel="이전 화면으로 이동"
          accessibilityRole="button"
          hitSlop={8}
          onPress={handleBackPress}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}>
          <SymbolView
            name={{ android: 'arrow_back', ios: 'chevron.left', web: 'arrow_back' }}
            size={24}
            tintColor={colors.textPrimary}
          />
        </Pressable>
      </View>

      <RefreshableBouncyScrollView contentContainerStyle={styles.scrollContent}>
        <View style={styles.titleRow}>
          <Text style={styles.title}>알림</Text>

          {/* 알림 설정 화면이 아직 없어 임시로 비활성 아이콘만 노출한다. 화면이 추가되면 Pressable + onPress로 라우팅을 연결한다. */}
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
        </View>

        <View style={styles.list}>
          {NOTIFICATIONS.map((notification) => (
            <NotificationCard key={notification.id} notification={notification} />
          ))}
        </View>
      </RefreshableBouncyScrollView>
    </SafeAreaLayout>
  );
}
