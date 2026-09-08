import { useRouter } from 'expo-router';
import { View } from 'react-native';

import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';

import { SocialLoginButton } from './components/SocialLoginButton';
import { styles } from './LoginScreen.styles';

export function LoginScreen() {
  const router = useRouter();

  const handleSocialLogin = () => {
    router.push('/terms');
  };

  return (
    <AuthScreenLayout title={'로그인하고\n시작하기'}>
      <View style={styles.buttonGroup}>
        <SocialLoginButton
          provider="kakao"
          onPress={handleSocialLogin}
        />

        <SocialLoginButton
          provider="google"
          onPress={handleSocialLogin}
        />

        <SocialLoginButton
          provider="naver"
          onPress={handleSocialLogin}
        />
      </View>
    </AuthScreenLayout>
  );
}