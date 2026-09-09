import { useRef, useState } from 'react';
import {
  Alert,
  Keyboard,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors } from '@/styles';

import type { Carrier } from './CarrierBottomSheet';
import { styles } from './VerificationCodeBottomSheet.styles';

interface VerificationCodeBottomSheetProps {
  visible: boolean;
  carrier: Carrier;
  phoneNumber: string;
  onClose: () => void;
  onComplete: () => void;
}

export function VerificationCodeBottomSheet({
  visible,
  carrier,
  phoneNumber,
  onClose,
  onComplete,
}: VerificationCodeBottomSheetProps) {
  const inputRef = useRef<TextInput>(null);
  const [code, setCode] = useState('');

  const canContinue = code.length === 6;
  const formattedPhoneNumber = phoneNumber.replace(
    /^(\d{3})(\d{3,4})(\d{4})$/,
    '$1-$2-$3',
  );

  const handleClose = () => {
    Keyboard.dismiss();
    onClose();
  };

  const showApiNotice = () => {
    Alert.alert(
      'UI 미리보기',
      '문자 발송과 본인인증은 API 연결 후 사용할 수 있어요.',
    );
  };

  const handleComplete = () => {
    if (!canContinue) return;

    Keyboard.dismiss();
    onComplete();
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      statusBarTranslucent
      onShow={() => {
        setCode('');
        inputRef.current?.focus();
      }}
      onRequestClose={handleClose}>
      <View style={styles.overlay}>
        <Pressable
          style={styles.backdrop}
          onPress={handleClose}
          accessibilityRole="button"
          accessibilityLabel="인증번호 입력 닫기"
        />

        <KeyboardAvoidingView
          pointerEvents="box-none"
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
          style={styles.keyboardContainer}>
          <SafeAreaView edges={['bottom']} style={styles.sheet}>
            <ScrollView
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={styles.content}>
              <View style={styles.header}>
                <Text style={styles.title}>인증번호 입력</Text>

                <Pressable
                  onPress={handleClose}
                  accessibilityRole="button"
                  accessibilityLabel="닫기"
                  style={styles.closeButton}>
                  <Text style={styles.closeIcon}>×</Text>
                </Pressable>
              </View>

              <Text style={styles.phoneSummary}>
                {carrier}　{formattedPhoneNumber}
              </Text>

              <View style={styles.inputRow}>
                <TextInput
                  ref={inputRef}
                  value={code}
                  onChangeText={(value) =>
                    setCode(value.replace(/\D/g, '').slice(0, 6))
                  }
                  placeholder="인증번호 6자리"
                  placeholderTextColor={colors.textTertiary}
                  keyboardType="number-pad"
                  maxLength={6}
                  accessibilityLabel="인증번호 6자리"
                  style={styles.input}
                />

                {code.length > 0 && (
                  <Pressable
                    onPress={() => {
                      setCode('');
                      inputRef.current?.focus();
                    }}
                    accessibilityRole="button"
                    accessibilityLabel="인증번호 지우기"
                    style={styles.clearButton}>
                    <Text style={styles.clearIcon}>×</Text>
                  </Pressable>
                )}

                <Pressable
                  onPress={showApiNotice}
                  accessibilityRole="button"
                  style={styles.resendButton}>
                  <Text style={styles.resendLabel}>재전송</Text>
                </Pressable>
              </View>

              <Pressable
                disabled={!canContinue}
                onPress={handleComplete}
                accessibilityRole="button"
                accessibilityState={{ disabled: !canContinue }}
                style={({ pressed }) => [
                  styles.nextButton,
                  !canContinue && styles.disabledButton,
                  pressed && styles.pressed,
                ]}>
                <Text style={styles.nextLabel}>다음</Text>
              </Pressable>

              <Pressable
                onPress={showApiNotice}
                accessibilityRole="button"
                style={styles.helpButton}>
                <Text style={styles.helpLabel}>
                  인증문자가 오지 않나요?
                </Text>
              </Pressable>
            </ScrollView>
          </SafeAreaView>
        </KeyboardAvoidingView>
      </View>
    </Modal>
  );
}
