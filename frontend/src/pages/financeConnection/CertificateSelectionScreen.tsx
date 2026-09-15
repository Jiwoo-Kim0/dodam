import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { financeDataApi } from '@/api';
import { AgreementItem } from '@/components/agreementItem/AgreementItem';
import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { SocialLoginButton } from '@/pages/auth/login/components/SocialLoginButton';
import { getFinanceErrorMessage } from '@/utils/financeErrorMessage';

import { styles } from './CertificateSelectionScreen.styles';

type CertificateProvider = 'kakao' | 'naver';

export function CertificateSelectionScreen() {
  const router = useRouter();
  const [selectedProvider, setSelectedProvider] =
    useState<CertificateProvider | null>(null);
  const [isConsentStep, setIsConsentStep] = useState(false);
  const [isConsentChecked, setIsConsentChecked] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  const canContinue = selectedProvider !== null && isConsentChecked;

  const handleProviderPress = (provider: CertificateProvider) => {
    setSelectedProvider(provider);
    setIsConsentChecked(false);
    setErrorMessage('');
    setIsConsentStep(true);
  };

  const handleContinue = async () => {
    if (!canContinue || isConnecting) return;

    setIsConnecting(true);
    setErrorMessage('');

    try {
      // 회원가입 때 이미 연결된 계정이면 ensureConnected가 현재 연결 상태를 그대로 돌려준다.
      await financeDataApi.ensureConnected();
      router.push(routes.financeSync);
    } catch (error) {
      setErrorMessage(getFinanceErrorMessage(error));
    } finally {
      setIsConnecting(false);
    }
  };

  return (
    <AuthScreenLayout
      title={isConsentStep ? '금융 정보 연결에 동의해 주세요' : '인증 수단을 선택해 주세요'}
      description={
        isConsentStep
          ? '선택한 인증서로 금융 정보를 안전하게 불러올게요.'
          : '자주 사용하는 민간 인증서를 선택해 주세요.'
      }
      showBackButton
      onBackPress={isConsentStep ? () => setIsConsentStep(false) : undefined}
      footer={isConsentStep ? (
        <BottomActionButton
          label={isConnecting ? '연결 중...' : '동의하고 진행'}
          onPress={() => void handleContinue()}
          disabled={!canContinue || isConnecting}
        />
      ) : undefined}>
      <View style={styles.optionList}>
        <SocialLoginButton
          provider="kakao"
          label="카카오 인증서"
          selected={selectedProvider === 'kakao'}
          onPress={() => handleProviderPress('kakao')}
        />
        <SocialLoginButton
          provider="naver"
          label="네이버 인증서"
          selected={selectedProvider === 'naver'}
          onPress={() => handleProviderPress('naver')}
        />
      </View>

      {isConsentStep ? (
        <View style={styles.consentSection}>
          <AgreementItem
            label="민간 인증서 개인정보 제3자 제공 필수 동의"
            checked={isConsentChecked}
            onPress={() => setIsConsentChecked((checked) => !checked)}
          />

          {errorMessage ? (
            <Text accessibilityRole="alert" style={styles.errorText}>
              {errorMessage}
            </Text>
          ) : null}
        </View>
      ) : null}
    </AuthScreenLayout>
  );
}
