import { useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Modal,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { styles } from '@/pages/goalDetail/components/GoalTransferBottomSheet.styles';
import { colors } from '@/styles';
import type { AccountApiResponse } from '@/types/assetApi.types';
import { formatCurrency, formatNumber } from '@/utils/currency';
import { getAccountLogo } from '@/utils/institution';

const QUICK_AMOUNTS = [10_000, 50_000, 100_000] as const;

/** 채우기: 다른 통장 → 누른 통장. 보내기: 누른 통장 → 다른 통장. */
export type AccountTransferDirection = 'fill' | 'send';

interface AccountTransferBottomSheetProps {
  /** 자산 목록에서 누른 통장. 채우기면 받는 쪽, 보내기면 보내는 쪽으로 고정된다. */
  account: AccountApiResponse;
  direction: AccountTransferDirection;
  /** 고를 수 있는 입출금 계좌 전체(누른 통장 포함, 도담 계좌가 맨 앞). */
  accounts: readonly AccountApiResponse[];
  /** 도담 계좌 ID와 목표에 담지 않은 금액. 도담 계좌에서 나갈 때 상한이다. */
  safebox: { accountId: number; availableAmount: number } | null;
  isSubmitting: boolean;
  errorMessage: string | null;
  onClose: () => void;
  onSubmit: (withdrawal: AccountApiResponse, deposit: AccountApiResponse, amount: number) => void;
}

function digitsOf(value: string) {
  return value.replace(/[^0-9]/g, '');
}

export function AccountTransferBottomSheet({
  account,
  direction,
  accounts,
  safebox,
  isSubmitting,
  errorMessage,
  onClose,
  onSubmit,
}: AccountTransferBottomSheetProps) {
  const isFill = direction === 'fill';
  const safeboxId = safebox?.accountId ?? null;
  const nameOf = (item: AccountApiResponse) => (item.accountId === safeboxId ? '도담 계좌' : item.accountName);
  const counterparts = useMemo(
    () => accounts.filter(({ accountId }) => accountId !== account.accountId),
    [account.accountId, accounts],
  );
  const [amountText, setAmountText] = useState('');
  // 도담 계좌를 채울 때는 일반 통장을, 일반 통장에서 보낼 때는 도담 계좌를 먼저 고른다.
  const [counterpartId, setCounterpartId] = useState<number | null>(() => {
    const preferred = account.accountId === safeboxId
      ? counterparts[0]
      : counterparts.find(({ accountId }) => accountId === safeboxId) ?? counterparts[0];
    return preferred?.accountId ?? null;
  });
  const counterpart = counterparts.find(({ accountId }) => accountId === counterpartId) ?? null;

  const withdrawal = isFill ? counterpart : account;
  const deposit = isFill ? account : counterpart;
  // 도담 계좌에서는 목표에 담긴 돈을 보낼 수 없다(서버 SAFEBOX_ALLOCATED_BALANCE와 같은 규칙).
  const maxAmount = withdrawal === null
    ? 0
    : withdrawal.accountId === safeboxId && safebox !== null
      ? Math.min(withdrawal.balance, safebox.availableAmount)
      : Math.max(withdrawal.balance, 0);
  const amount = Number(digitsOf(amountText) || '0');
  const exceedsBalance = amount > maxAmount;
  const canSubmit = !isSubmitting && withdrawal !== null && deposit !== null && amount > 0 && !exceedsBalance;
  const title = isFill ? `${nameOf(account)} 채우기` : `${nameOf(account)}에서 보내기`;

  const renderAccount = (item: AccountApiResponse) => (
    <>
      <Image
        accessibilityIgnoresInvertColors
        source={getAccountLogo(item.bankCode, item.bankName, item.accountId === safeboxId)}
        style={styles.logo}
      />
      <View style={styles.accountText}>
        <Text numberOfLines={1} style={styles.accountName}>{nameOf(item)}</Text>
        <Text numberOfLines={1} style={styles.accountNumber}>{`${item.bankName} ${item.accountNoMasked}`}</Text>
      </View>
      <Text style={styles.accountBalance}>{formatCurrency(item.balance)}</Text>
    </>
  );

  return (
    <Modal animationType="fade" onRequestClose={onClose} statusBarTranslucent transparent visible>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel={`${title} 닫기`}
          accessibilityRole="button"
          disabled={isSubmitting}
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <Text style={styles.title}>{title}</Text>

          <ScrollView keyboardShouldPersistTaps="handled" style={styles.body}>
            <Text style={styles.fieldLabel}>{isFill ? '받는 통장' : '보낼 통장'}</Text>
            <View style={styles.fixedAccount}>{renderAccount(account)}</View>

            <Text style={styles.fieldLabel}>{isFill ? '가져올 통장' : '받는 통장'}</Text>
            {counterparts.length === 0 ? (
              <Text style={styles.emptyText}>옮길 수 있는 다른 입출금 통장이 없어요.</Text>
            ) : (
              <View style={styles.accountOptions}>
                {counterparts.map((item) => {
                  const isSelected = item.accountId === counterpartId;
                  return (
                    <Pressable
                      accessibilityRole="radio"
                      accessibilityState={{ disabled: isSubmitting, selected: isSelected }}
                      disabled={isSubmitting}
                      key={item.accountId}
                      onPress={() => setCounterpartId(item.accountId)}
                      style={({ pressed }) => [
                        styles.accountOption,
                        isSelected && styles.accountOptionSelected,
                        pressed && styles.pressed,
                      ]}>
                      {renderAccount(item)}
                    </Pressable>
                  );
                })}
              </View>
            )}

            <Text style={styles.fieldLabel}>금액</Text>
            <View style={[styles.amountRow, exceedsBalance && styles.amountRowError]}>
              <TextInput
                accessibilityLabel="옮길 금액"
                editable={!isSubmitting}
                inputMode="numeric"
                keyboardType="number-pad"
                onChangeText={(next) => setAmountText(digitsOf(next))}
                placeholder="0"
                placeholderTextColor={colors.textTertiary}
                style={styles.amountInput}
                value={amount === 0 ? '' : formatNumber(amount)}
              />
              <Text style={styles.amountUnit}>원</Text>
            </View>
            <Text style={[styles.helperText, exceedsBalance && styles.helperTextError]}>
              {exceedsBalance
                ? `옮길 수 있는 금액은 ${formatCurrency(maxAmount)}까지예요`
                : withdrawal !== null && withdrawal.accountId === safeboxId
                  ? `목표에 담지 않은 돈 최대 ${formatCurrency(maxAmount)}`
                  : `최대 ${formatCurrency(maxAmount)}`}
            </Text>

            <View style={styles.quickAmounts}>
              {QUICK_AMOUNTS.filter((quick) => quick <= maxAmount).map((quick) => (
                <Pressable
                  accessibilityRole="button"
                  disabled={isSubmitting}
                  key={quick}
                  onPress={() => setAmountText(String(amount + quick))}
                  style={({ pressed }) => [styles.quickAmount, pressed && styles.pressed]}>
                  <Text style={styles.quickAmountLabel}>{`+${formatNumber(quick / 10_000)}만`}</Text>
                </Pressable>
              ))}
            </View>

            {errorMessage === null ? null : (
              <Text accessibilityLiveRegion="polite" style={styles.errorText}>{errorMessage}</Text>
            )}
          </ScrollView>

          <Pressable
            accessibilityRole="button"
            accessibilityState={{ disabled: !canSubmit }}
            disabled={!canSubmit}
            onPress={() => {
              if (withdrawal !== null && deposit !== null) onSubmit(withdrawal, deposit, amount);
            }}
            style={({ pressed }) => [
              styles.submitButton,
              !canSubmit && styles.submitButtonDisabled,
              pressed && canSubmit && styles.pressed,
            ]}>
            {isSubmitting ? (
              <ActivityIndicator color={colors.white} />
            ) : (
              <Text style={styles.submitLabel}>{isFill ? '채우기' : '보내기'}</Text>
            )}
          </Pressable>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
