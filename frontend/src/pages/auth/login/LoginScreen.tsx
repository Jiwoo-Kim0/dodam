import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Text, View } from 'react-native';

import { isApiError } from '@/api';
import { routes } from '@/constants/routes';
import { useKakaoLogin } from '@/hooks/useKakaoLogin';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';

import { SocialLoginButton } from './components/SocialLoginButton';
import { styles } from './LoginScreen.styles';

export function LoginScreen() {
  const router = useRouter();
  const { login: loginWithKakao } = useKakaoLogin();
  const [errorMessage, setErrorMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleKakaoLogin = async () => {
    if (isSubmitting) return;

    setErrorMessage('');
    setIsSubmitting(true);

    try {
      const response = await loginWithKakao();
      if (!response) return;

      router.replace(
        response.onboardingRequired === false ? routes.home : routes.terms,
      );
    } catch (error) {
      setErrorMessage(
        isApiError(error)
          ? error.errors[0]?.reason ?? error.message
          : '카카오 로그인 중 문제가 발생했습니다. 다시 시도해주세요.',
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const showComingSoon = () => {
    Alert.alert('준비 중', '현재 카카오 로그인만 연동을 준비하고 있습니다.');
  };

  return (
    <AuthScreenLayout title={'로그인하고\n시작하기'}>
      <View style={styles.buttonGroup}>
        <SocialLoginButton
          label={isSubmitting ? '카카오 로그인 중...' : undefined}
          provider="kakao"
          onPress={() => void handleKakaoLogin()}
        />

        <SocialLoginButton provider="google" onPress={showComingSoon} />
        <SocialLoginButton provider="naver" onPress={showComingSoon} />

        {errorMessage ? (
          <Text accessibilityRole="alert" style={styles.errorText}>
            {errorMessage}
          </Text>
        ) : null}
      </View>
    </AuthScreenLayout>
  );
}
