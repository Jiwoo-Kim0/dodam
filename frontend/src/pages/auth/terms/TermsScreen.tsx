import { StatusBar } from 'expo-status-bar';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { AgreementItem } from './components/AgreementItem';
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

    // 다음 본인인증 화면을 만든 뒤 이동 경로 연결
  };

  return (
    <SafeAreaView
      edges={['left', 'right']}
      style={styles.container}
    >
      <StatusBar style="dark" />

      <Pressable
        onPress={() => router.back()}
        accessibilityRole="button"
        accessibilityLabel="뒤로 가기"
        hitSlop={12}
        style={styles.backButton}
      >
        <Text style={styles.backIcon}>‹</Text>
      </Pressable>

      <View style={styles.header}>
        <Text style={styles.title}>약관에 동의해주세요</Text>
        <Text style={styles.description}>
          도담 서비스 이용을 위해 아래 약관 동의가 필요해요.
        </Text>
      </View>

      <View style={styles.agreementSection}>
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
      </View>

      <Pressable
        onPress={handleContinue}
        disabled={!canContinue}
        accessibilityRole="button"
        accessibilityState={{ disabled: !canContinue }}
        style={({ pressed }) => [
          styles.continueButton,
          !canContinue && styles.disabledButton,
          pressed && canContinue && styles.pressedButton,
        ]}
      >
        <Text style={styles.continueButtonLabel}>
          동의하고 계속하기
        </Text>
      </Pressable>
    </SafeAreaView>
  );
}
