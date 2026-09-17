import { SymbolView } from 'expo-symbols';
import { useEffect, useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, Pressable, Text, TextInput, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import { BouncyScrollView } from '@/components/bouncyScrollView/BouncyScrollView';
import { colors, spacing } from '@/styles';
import type { LedgerTransactionDraft, LedgerTransactionType } from '@/types/ledger.types';
import { formatNumber } from '@/utils/currency';

import { styles } from '../LedgerScreen.styles';
import {
  getTransactionAmountError,
  isValidTransactionDate,
  isValidTransactionTime,
} from '../utils/ledgerTransactionValidation';
import { CashTransactionDatePickerBottomSheet } from './CashTransactionDatePickerBottomSheet';
import { CashTransactionTimePickerBottomSheet } from './CashTransactionTimePickerBottomSheet';
import { LedgerHeader } from './LedgerShared';

interface TransactionFormProps {
  mode: 'create' | 'edit';
  draft: LedgerTransactionDraft;
  isSubmitting?: boolean;
  onChange: (patch: Partial<LedgerTransactionDraft>) => void;
  onClose: () => void;
  onSave: () => void;
  onDelete?: () => void;
}

function formatFormDateTime(dateValue: string, timeValue: string) {
  if (!isValidTransactionDate(dateValue) || !isValidTransactionTime(timeValue)) {
    return '날짜와 시간을 다시 선택해 주세요';
  }

  const date = new Date(`${dateValue}T00:00:00`);
  const weekday = ['일', '월', '화', '수', '목', '금', '토'][date.getDay()];
  return `${dateValue.replaceAll('-', '.')} (${weekday}) ${timeValue}`;
}

function getDigits(value: string) {
  return value.replace(/\D/g, '');
}

export function TransactionForm({
  mode,
  draft,
  isSubmitting = false,
  onChange,
  onClose,
  onSave,
  onDelete,
}: TransactionFormProps) {
  const [hasTouchedAmount, setHasTouchedAmount] = useState(false);
  const [isDatePickerVisible, setIsDatePickerVisible] = useState(false);
  const [isTimePickerVisible, setIsTimePickerVisible] = useState(false);
  const [selectorWidth, setSelectorWidth] = useState(0);
  const amountValidationError = getTransactionAmountError(draft.amountText);
  const dateError = isValidTransactionDate(draft.transactionDate)
    ? null
    : '오늘까지의 올바른 날짜를 선택해 주세요.';
  const timeError = isValidTransactionTime(draft.transactionTime)
    ? null
    : '올바른 거래 시간을 선택해 주세요.';
  const amountError = hasTouchedAmount ? amountValidationError : null;
  const canSave = !amountValidationError && !dateError && !timeError;
  const typeLabel = draft.transactionType === 'income' ? '수입' : '지출';

  const pillProgress = useSharedValue(draft.transactionType === 'income' ? 1 : 0);
  const labelOpacity = useSharedValue(1);

  useEffect(() => {
    pillProgress.value = withTiming(draft.transactionType === 'income' ? 1 : 0, {
      duration: 240,
    });
    labelOpacity.value = 0;
    labelOpacity.value = withTiming(1, { duration: 240 });
  }, [draft.transactionType, labelOpacity, pillProgress]);

  const pillWidth = selectorWidth > 0 ? (selectorWidth - spacing[1] * 2) / 2 : 0;

  const pillAnimatedStyle = useAnimatedStyle(() => ({
    width: pillWidth,
    transform: [{ translateX: pillProgress.value * pillWidth }],
  }));

  const labelAnimatedStyle = useAnimatedStyle(() => ({
    opacity: labelOpacity.value,
  }));

  const selectType = (transactionType: LedgerTransactionType) => {
    onChange({ transactionType });
  };

  const confirmDelete = () => {
    if (!onDelete) return;

    Alert.alert('거래 내역을 삭제할까요?', '삭제한 거래는 되돌릴 수 없어요.', [
      { text: '취소', style: 'cancel' },
      { text: '삭제', style: 'destructive', onPress: onDelete },
    ]);
  };

  return (
    <KeyboardAvoidingView
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      style={styles.formKeyboardArea}>
      <LedgerHeader
        backDisabled={isSubmitting}
        onBackPress={() => {
          if (!isSubmitting) onClose();
        }}
        onRightPress={mode === 'edit' ? confirmDelete : undefined}
        rightLabel={mode === 'edit' ? '삭제' : undefined}
        rightDisabled={isSubmitting}
        title={mode === 'edit' ? '거래 내역 수정' : '거래 추가'}
      />

      <BouncyScrollView contentContainerStyle={styles.formScrollContent}>
        <View
          onLayout={(event) => setSelectorWidth(event.nativeEvent.layout.width)}
          style={styles.typeSelector}>
          {pillWidth > 0 ? (
            <Animated.View style={[styles.typeSelectorPill, pillAnimatedStyle]} />
          ) : null}
          {(['expense', 'income'] as const).map((type) => {
            const isSelected = draft.transactionType === type;
            return (
              <Pressable
                accessibilityRole="tab"
                accessibilityState={{ disabled: isSubmitting, selected: isSelected }}
                disabled={isSubmitting}
                key={type}
                onPress={() => selectType(type)}
                style={styles.typeOption}>
                <Text style={styles.typeOptionText}>{type === 'expense' ? '지출' : '수입'}</Text>
              </Pressable>
            );
          })}
        </View>

        <View style={styles.formCard}>
          <View style={styles.amountFieldSection}>
            <View style={[styles.formRow, styles.formRowWithoutDivider]}>
              <Text style={styles.formLabel}>금액</Text>
              <TextInput
                accessibilityHint={amountError ?? undefined}
                accessibilityLabel="거래 금액"
                editable={!isSubmitting}
                keyboardType="number-pad"
                maxLength={16}
                onBlur={() => setHasTouchedAmount(true)}
                onChangeText={(value) => onChange({ amountText: getDigits(value) })}
                placeholder="금액을 입력하세요"
                placeholderTextColor={colors.textTertiary}
                style={styles.amountInput}
                value={draft.amountText ? formatNumber(Number(draft.amountText)) : ''}
              />
              <Text style={styles.amountUnit}>원</Text>
            </View>
            {amountError ? (
              <Text accessibilityLiveRegion="polite" style={styles.formValidationText}>
                {amountError}
              </Text>
            ) : null}
          </View>

          <View style={styles.amountFieldSection}>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ disabled: isSubmitting }}
              disabled={isSubmitting}
              onPress={() => setIsDatePickerVisible(true)}
              style={({ pressed }) => [
                styles.formRow,
                styles.formRowWithoutDivider,
                pressed && !isSubmitting && styles.rowPressed,
              ]}>
              <Animated.Text style={[styles.formLabel, labelAnimatedStyle]}>
                {typeLabel} 시간
              </Animated.Text>
              <Text style={styles.formValue}>
                {formatFormDateTime(draft.transactionDate, draft.transactionTime)}
              </Text>
              <SymbolView
                name={{ android: 'chevron_right', ios: 'chevron.right', web: 'chevron_right' }}
                size={18}
                tintColor={colors.textSecondary}
              />
            </Pressable>
            {dateError || timeError ? (
              <Text accessibilityLiveRegion="polite" style={styles.formValidationText}>
                {dateError ?? timeError}
              </Text>
            ) : null}
          </View>

          <View style={[styles.formRow, styles.formRowLast]}>
            <Animated.Text style={[styles.formLabel, labelAnimatedStyle]}>
              {typeLabel}처
            </Animated.Text>
            <TextInput
              editable={!isSubmitting}
              maxLength={255}
              onChangeText={(merchantName) => onChange({ merchantName })}
              placeholder={
                draft.transactionType === 'income' ? '예: 용돈, 이자' : '예: 스타벅스, 점심식사'
              }
              placeholderTextColor={colors.textTertiary}
              style={styles.amountInput}
              value={draft.merchantName}
            />
          </View>
        </View>
      </BouncyScrollView>

      <View style={styles.formFooter}>
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ disabled: !canSave || isSubmitting }}
          disabled={!canSave || isSubmitting}
          onPress={onSave}
          style={({ pressed }) => [
            styles.saveButton,
            (!canSave || isSubmitting) && styles.saveButtonDisabled,
            pressed && canSave && !isSubmitting && styles.saveButtonPressed,
          ]}>
          <Text style={styles.saveButtonLabel}>
            {isSubmitting ? '저장 중...' : '저장'}
          </Text>
        </Pressable>
      </View>

      <CashTransactionDatePickerBottomSheet
        onClose={() => setIsDatePickerVisible(false)}
        onConfirm={(dateKey) => {
          onChange({ transactionDate: dateKey });
          setIsDatePickerVisible(false);
          setIsTimePickerVisible(true);
        }}
        value={draft.transactionDate}
        visible={isDatePickerVisible && !isSubmitting}
      />

      <CashTransactionTimePickerBottomSheet
        dateKey={draft.transactionDate}
        onClose={() => setIsTimePickerVisible(false)}
        onConfirm={(time) => {
          onChange({ transactionTime: time });
          setIsTimePickerVisible(false);
        }}
        value={draft.transactionTime}
        visible={isTimePickerVisible && !isSubmitting}
      />
    </KeyboardAvoidingView>
  );
}
