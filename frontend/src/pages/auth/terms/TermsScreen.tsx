import { useRouter } from 'expo-router';
import { useState } from 'react';
import { View } from 'react-native';

import { AgreementItem } from '@/components/agreementItem/AgreementItem';
import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { routes } from '@/constants/routes';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';

import { styles } from './TermsScreen.styles';

interface AgreementState {
  service: boolean;
  privacy: boolean;
  marketing: boolean;
}

type AgreementKey = keyof AgreementState;

const INITIAL_AGREEMENTS: AgreementState = {
  service: false,
  privacy: false,
  marketing: false,
};

export function TermsScreen() {
  const router = useRouter();
  const [agreements, setAgreements] =
    useState<AgreementState>(INITIAL_AGREEMENTS);

  const allAgreed = Object.values(agreements).every(Boolean);
  const canContinue = agreements.service && agreements.privacy;

  const toggleAll = () => {
    const nextChecked = !allAgreed;

    setAgreements({
      service: nextChecked,
      privacy: nextChecked,
      marketing: nextChecked,
    });
  };

  const toggleAgreement = (key: AgreementKey) => {
    setAgreements((current) => ({
      ...current,
      [key]: !current[key],
    }));
  };

  const handleContinue = () => {
    if (!canContinue) {
      return;
    }

    router.push(routes.identityVerification);
  };

  return (
    <AuthScreenLayout
      title="약관에 동의해주세요"
      description="도담 서비스 이용을 위해 아래 약관 동의가 필요해요."
      showBackButton
      footer={
        <BottomActionButton
          label="동의하고 계속하기"
          onPress={handleContinue}
          disabled={!canContinue}
        />
      }>
        <AgreementItem
          label="전체 동의"
          checked={allAgreed}
          onPress={toggleAll}
          emphasized
        />

        <View style={styles.agreementList}>
          <AgreementItem
            label="서비스 이용약관 (필수)"
            checked={agreements.service}
            onPress={() => toggleAgreement('service')}
          />

          <AgreementItem
            label="개인정보 수집 · 이용 동의 (필수)"
            checked={agreements.privacy}
            onPress={() => toggleAgreement('privacy')}
          />

          <AgreementItem
            label="마케팅 정보 수신 동의 (선택)"
            checked={agreements.marketing}
            onPress={() => toggleAgreement('marketing')}
          />
        </View>
    </AuthScreenLayout>
  );
}
