import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, Text, TextInput, View } from 'react-native';

import { authApi, isApiError } from '@/api';
import { routes } from '@/constants/routes';
import { useGoogleLogin } from '@/hooks/useGoogleLogin';
import { useKakaoLogin } from '@/hooks/useKakaoLogin';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { colors } from '@/styles';
import type { SocialLoginResult } from '@/types/auth.types';

import { SocialLoginButton } from './components/SocialLoginButton';
import { styles } from './LoginScreen.styles';

type SubmissionType = 'kakao' | 'google' | 'demo';

export function LoginScreen() {
  const router = useRouter();
  const { login: loginWithKakao } = useKakaoLogin();
  const { login: loginWithGoogle } = useGoogleLogin();
  const [errorMessage, setErrorMessage] = useState('');
  const [submissionType, setSubmissionType] =
    useState<SubmissionType | null>(null);
  const [demoEmail, setDemoEmail] = useState('');
  const [demoPassword, setDemoPassword] = useState('');
  const isSubmitting = submissionType !== null;

  const showError = (error: unknown, fallbackMessage: string) => {
    setErrorMessage(
      isApiError(error)
        ? error.errors[0]?.reason ?? error.message
        : fallbackMessage,
    );
  };

  /** 서버가 새로 만든 회원(201)만 약관·금융 연동 온보딩을 거친다. */
  const handleSocialLogin = async (
    provider: Exclude<SubmissionType, 'demo'>,
    login: () => Promise<SocialLoginResult | null>,
    failureMessage: string,
  ) => {
    if (isSubmitting) return;

    setErrorMessage('');
    setSubmissionType(provider);

    try {
      const result = await login();
      if (!result) return;

      router.replace(result.isNewUser ? routes.terms : routes.home);
    } catch (error) {
      showError(error, failureMessage);
    } finally {
      setSubmissionType(null);
    }
  };

  const handleDemoLogin = async () => {
    if (isSubmitting) return;

    const email = demoEmail.trim();
    if (!email || !demoPassword) {
      setErrorMessage('데모 이메일과 비밀번호를 입력해주세요.');
      return;
    }

    setErrorMessage('');
    setSubmissionType('demo');

    try {
      await authApi.loginWithPassword({ email, password: demoPassword });
      router.replace(routes.home);
    } catch (error) {
      showError(error, '데모 로그인에 실패했어요. 다시 시도해주세요.');
    } finally {
      setSubmissionType(null);
    }
  };

  const showNaverComingSoon = () => {
    Alert.alert('준비 중', '네이버 로그인은 아직 준비 중이에요.');
  };

  return (
    <AuthScreenLayout title={'로그인하고\n시작하기'}>
      <View style={styles.buttonGroup}>
        <SocialLoginButton
          label={submissionType === 'kakao' ? '카카오 로그인 중...' : undefined}
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
          label={submissionType === 'google' ? '구글 로그인 중...' : undefined}
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

        {__DEV__ ? (
          <View style={styles.demoSection}>
            <Text style={styles.demoTitle}>개발용 로그인</Text>
            <TextInput
              autoCapitalize="none"
              autoComplete="email"
              editable={!isSubmitting}
              keyboardType="email-address"
              onChangeText={setDemoEmail}
              placeholder="데모 이메일"
              placeholderTextColor={colors.textTertiary}
              style={styles.demoInput}
              value={demoEmail}
            />
            <TextInput
              autoCapitalize="none"
              autoComplete="password"
              editable={!isSubmitting}
              onChangeText={setDemoPassword}
              onSubmitEditing={() => void handleDemoLogin()}
              placeholder="데모 비밀번호"
              placeholderTextColor={colors.textTertiary}
              returnKeyType="done"
              secureTextEntry
              style={styles.demoInput}
              value={demoPassword}
            />
            <Pressable
              accessibilityRole="button"
              disabled={isSubmitting}
              onPress={() => void handleDemoLogin()}
              style={({ pressed }) => [
                styles.demoButton,
                pressed && styles.demoButtonPressed,
                isSubmitting && styles.demoButtonDisabled,
              ]}>
              <Text style={styles.demoButtonLabel}>
                {submissionType === 'demo'
                  ? '로그인 중...'
                  : '데모 계정으로 로그인'}
              </Text>
            </Pressable>
          </View>
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
