import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { requestNotificationPermission } from '@/services/notificationPermission';

import { styles } from './NotificationConsentScreen.styles';

const NOTIFICATION_BENEFITS = [
  {
    title: '목표 달성',
    description: '목표에 가까워질 때마다 진행 상황을 알려드려요.',
  },
  {
    title: '현금흐름 위험',
    description: '잔액 부족 위험을 미리 알려드려요.',
  },
  {
    title: '개선 행동',
    description: '놓치기 쉬운 개선 행동을 알려드려요.',
  },
] as const;

export function NotificationConsentScreen() {
  const router = useRouter();
  const [isRequesting, setIsRequesting] = useState(false);

  const handleContinue = async () => {
    if (isRequesting) return;

    setIsRequesting(true);

    await requestNotificationPermission().catch(() => false);
    router.replace(routes.home);
  };

  return (
    <AuthScreenLayout
      title="알림을 허용해주세요"
      description="꼭 필요한 순간에만 먼저 알려드릴게요."
      footer={
        <BottomActionButton
          label="동의하고 계속하기"
          onPress={() => void handleContinue()}
          disabled={isRequesting}
        />
      }>
      <View style={styles.benefitList}>
        {NOTIFICATION_BENEFITS.map((benefit) => (
          <View key={benefit.title} style={styles.benefitCard}>
            <View
              importantForAccessibility="no-hide-descendants"
              style={styles.iconSpace}
            />
            <View style={styles.benefitText}>
              <Text style={styles.benefitTitle}>{benefit.title}</Text>
              <Text style={styles.benefitDescription}>{benefit.description}</Text>
            </View>
          </View>
        ))}
      </View>
    </AuthScreenLayout>
  );
}
