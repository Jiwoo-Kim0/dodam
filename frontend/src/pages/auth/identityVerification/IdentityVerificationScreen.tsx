import { useState } from 'react';
import { Keyboard, Pressable, Text, TextInput, View } from 'react-native';

import { BottomActionButton } from '@/components/bottomActionButton/BottomActionButton';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { colors } from '@/styles';

import {
  CarrierBottomSheet,
  type Carrier,
} from './components/CarrierBottomSheet';
import { VerificationField } from './components/VerificationField';
import { VerificationCodeBottomSheet } from './components/VerificationCodeBottomSheet';
import { styles } from './IdentityVerificationScreen.styles';

interface InputClearButtonProps {
  accessibilityLabel: string;
  onPress: () => void;
}

function InputClearButton({
  accessibilityLabel,
  onPress,
}: InputClearButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      hitSlop={8}
      style={styles.clearButton}>
      <Text style={styles.clearIcon}>×</Text>
    </Pressable>
  );
}

const getDigits = (value: string) => value.replace(/\D/g, '');

export function IdentityVerificationScreen() {
  const [name, setName] = useState('');
  const [birthDate, setBirthDate] = useState('');
  const [residentDigit, setResidentDigit] = useState('');
  const [carrier, setCarrier] = useState<Carrier | null>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [isCarrierSheetVisible, setIsCarrierSheetVisible] = useState(false);
  const [isCodeSheetVisible, setIsCodeSheetVisible] = useState(false);

  const canContinue =
    name.trim().length > 0 &&
    birthDate.length === 6 &&
    residentDigit.length === 1 &&
    carrier !== null &&
    phoneNumber.length >= 10;

  const openCarrierSheet = () => {
    Keyboard.dismiss();
    setIsCarrierSheetVisible(true);
  };

  const selectCarrier = (selectedCarrier: Carrier) => {
    setCarrier(selectedCarrier);
    setIsCarrierSheetVisible(false);
  };

  const handleContinue = () => {
    if (!canContinue) return;

    Keyboard.dismiss();
    setIsCodeSheetVisible(true);
  };

  return (
    <AuthScreenLayout
      title="본인인증을 진행해 주세요"
      description="안전한 서비스 이용을 위해 본인확인이 필요합니다"
      showBackButton
      footer={
        <BottomActionButton
          label="인증하고 계속하기"
          onPress={handleContinue}
          disabled={!canContinue}
        />
      }>
      <View style={styles.form}>
        <VerificationField label="이름">
          <View style={styles.inputRow}>
            <TextInput
              value={name}
              onChangeText={setName}
              placeholder="이름 입력"
              placeholderTextColor={colors.textSecondary}
              autoCorrect={false}
              textContentType="name"
              style={styles.input}
            />

            {name.length > 0 ? (
              <InputClearButton
                accessibilityLabel="이름 지우기"
                onPress={() => setName('')}
              />
            ) : null}
          </View>
        </VerificationField>

        <VerificationField label="주민등록번호 앞 7자리">
          <View style={styles.residentNumberRow}>
            <TextInput
              value={birthDate}
              onChangeText={(value) => setBirthDate(getDigits(value))}
              placeholder="생년월일"
              placeholderTextColor={colors.textSecondary}
              keyboardType="number-pad"
              maxLength={6}
              style={[styles.input, styles.birthDateInput]}
            />

            <Text style={styles.residentSeparator}>—</Text>

            <View style={styles.residentSuffix}>
              <TextInput
                value={residentDigit}
                onChangeText={(value) => setResidentDigit(getDigits(value))}
                placeholder="0"
                placeholderTextColor={colors.textSecondary}
                keyboardType="number-pad"
                maxLength={1}
                style={[styles.input, styles.residentDigitInput]}
              />

              <Text
                numberOfLines={1}
                adjustsFontSizeToFit
                style={styles.residentMask}>******</Text>
            </View>
          </View>
        </VerificationField>

        <VerificationField label="휴대폰번호">
          <View style={styles.inputRow}>
            <Pressable
              onPress={openCarrierSheet}
              accessibilityRole="button"
              accessibilityLabel="통신사 선택"
              style={styles.carrierSelector}>
              <Text
                style={[
                  styles.carrierLabel,
                  carrier && styles.selectedCarrierLabel,
                ]}>
                {carrier ?? '통신사'}
              </Text>
              <View style={styles.carrierChevron} />
            </Pressable>

            <TextInput
              value={phoneNumber}
              onChangeText={(value) => setPhoneNumber(getDigits(value))}
              placeholder="휴대폰번호 입력"
              placeholderTextColor={colors.textSecondary}
              keyboardType="phone-pad"
              textContentType="telephoneNumber"
              maxLength={11}
              style={[styles.input, styles.phoneInput]}
            />

            {phoneNumber.length > 0 ? (
              <InputClearButton
                accessibilityLabel="휴대폰번호 지우기"
                onPress={() => setPhoneNumber('')}
              />
            ) : null}
          </View>
        </VerificationField>
      </View>

      <CarrierBottomSheet
        visible={isCarrierSheetVisible}
        selectedCarrier={carrier}
        onSelect={selectCarrier}
        onClose={() => setIsCarrierSheetVisible(false)}
      />
      {carrier !== null && (
        <VerificationCodeBottomSheet
          visible={isCodeSheetVisible}
          carrier={carrier}
          phoneNumber={phoneNumber}
          onClose={() => setIsCodeSheetVisible(false)}
        />
      )}
    </AuthScreenLayout>
  );
}
