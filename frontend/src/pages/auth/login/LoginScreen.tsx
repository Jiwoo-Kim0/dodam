import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, Text, View } from 'react-native';

import { isApiError, userApi } from '@/api';
import { routes } from '@/constants/routes';
import { useDemoLogin } from '@/hooks/useDemoLogin';
import { useGoogleLogin } from '@/hooks/useGoogleLogin';
import { useKakaoLogin } from '@/hooks/useKakaoLogin';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import type { SocialLoginResult } from '@/types/auth.types';

import { SocialLoginButton } from './components/SocialLoginButton';
import { styles } from './LoginScreen.styles';

type PendingLogin = 'kakao' | 'google' | 'demo';

export function LoginScreen() {
  const router = useRouter();
  const { login: loginWithKakao } = useKakaoLogin();
  const { login: loginWithGoogle } = useGoogleLogin();
  const { isAvailable: isDemoLoginAvailable, login: loginWithDemoAccount } =
    useDemoLogin();
  const [errorMessage, setErrorMessage] = useState('');
  const [pendingLogin, setPendingLogin] = useState<PendingLogin | null>(null);

  const showError = (error: unknown, fallbackMessage: string) => {
    setErrorMessage(
      isApiError(error)
        ? error.errors[0]?.reason ?? error.message
        : fallbackMessage,
    );
  };

  /** 서버가 새로 만든 회원(201)만 약관·금융 연동 온보딩을 거친다. */
  const handleSocialLogin = async (
    provider: PendingLogin,
    login: () => Promise<SocialLoginResult | null>,
    failureMessage: string,
  ) => {
    if (pendingLogin) return;

    setErrorMessage('');
    setPendingLogin(provider);

    try {
      const result = await login();
      if (!result) return;

      router.replace(result.isNewUser ? routes.terms : routes.home);
    } catch (error) {
      showError(error, failureMessage);
    } finally {
      setPendingLogin(null);
    }
  };

  /** 시연 계정은 이미 가입된 회원이라, 금융망 연동을 아직 안 했으면 연동 화면부터 보여준다. */
  const handleDemoLogin = async () => {
    if (pendingLogin) return;

    setErrorMessage('');
    setPendingLogin('demo');

    try {
      await loginWithDemoAccount();
      const profile = await userApi.getMe();

      router.replace(
        profile.financeConnectionStatus === 'CONNECTED'
          ? routes.home
          : routes.financeConnection,
      );
    } catch (error) {
      showError(error, '시연 계정으로 로그인하지 못했습니다.');
    } finally {
      setPendingLogin(null);
    }
  };

  const showNaverComingSoon = () => {
    Alert.alert('준비 중', '네이버 로그인은 아직 준비 중이에요.');
  };

  return (
    <AuthScreenLayout title={'로그인하고\n시작하기'}>
      <View style={styles.buttonGroup}>
        <SocialLoginButton
          label={pendingLogin === 'kakao' ? '카카오 로그인 중...' : undefined}
          provider="kakao"
          onPress={() =>
            void handleSocialLogin(
              'kakao',
              loginWithKakao,
              '카카오 로그인 중 문제가 발생했습니다. 다시 시도해주세요.',
            )
          }
        />

        <SocialLoginButton
          label={pendingLogin === 'google' ? '구글 로그인 중...' : undefined}
          provider="google"
          onPress={() =>
            void handleSocialLogin(
              'google',
              loginWithGoogle,
              '구글 로그인 중 문제가 발생했습니다. 다시 시도해주세요.',
            )
          }
        />

        <SocialLoginButton provider="naver" onPress={showNaverComingSoon} />

        {isDemoLoginAvailable ? (
          <Pressable
            accessibilityLabel="시연 계정으로 시작"
            accessibilityRole="button"
            accessibilityState={{ disabled: pendingLogin !== null }}
            disabled={pendingLogin !== null}
            onPress={() => void handleDemoLogin()}
            style={({ pressed }) => [
              styles.demoButton,
              pressed && !pendingLogin && styles.demoButtonPressed,
            ]}>
            <Text style={styles.demoLabel}>시연 계정으로 시작</Text>
          </Pressable>
        ) : null}

        {errorMessage ? (
          <Text accessibilityRole="alert" style={styles.errorText}>
            {errorMessage}
          </Text>
        ) : null}
      </View>
    </AuthScreenLayout>
  );
}
