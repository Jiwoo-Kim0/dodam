import { useRouter } from 'expo-router';
import { Text, View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';

import { BankLogoCloud } from './components/BankLogoCloud';
import { styles } from './FinanceConnectionIntroScreen.styles';

export function FinanceConnectionIntroScreen() {
  const router = useRouter();

  return (
    <SafeAreaLayout edges={['left', 'right', 'bottom']} style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>김싸피님의 자산{`\n`}한 번에 찾아볼게요</Text>
        <Text style={styles.description}>
          흩어진 금융 정보를 연결하면{`\n`}도담이 한눈에 정리해 드려요.
        </Text>
      </View>

      <View style={styles.visualArea}>
        <BankLogoCloud />
      </View>

      <BottomActionButton
        label="금융 정보 연결하기"
        onPress={() => router.push(routes.certificateSelection)}
      />
    </SafeAreaLayout>
  );
}
