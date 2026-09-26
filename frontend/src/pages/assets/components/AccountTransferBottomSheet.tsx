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

interface AccountTransferBottomSheetProps {
  /** 고를 수 있는 입출금 계좌. 도담 계좌도 포함된다. */
  accounts: readonly AccountApiResponse[];
  /** 도담 계좌 ID와 목표에 담지 않은 금액. 도담 계좌에서 보낼 때 상한이다. */
  safebox: { accountId: number; availableAmount: number } | null;
  isSubmitting: boolean;
  errorMessage: string | null;
  onClose: () => void;
  onSubmit: (withdrawal: AccountApiResponse, deposit: AccountApiResponse, amount: number) => void;
}

function digitsOf(value: string) {
  return value.replace(/[^0-9]/g, '');
}

interface AccountOptionsProps {
  accounts: readonly AccountApiResponse[];
  selectedId: number | null;
  disabled: boolean;
  safeboxId: number | null;
  onSelect: (accountId: number) => void;
}

function AccountOptions({ accounts, selectedId, disabled, safeboxId, onSelect }: AccountOptionsProps) {
  return (
    <View style={styles.accountOptions}>
      {accounts.map((account) => {
        const isSelected = account.accountId === selectedId;
        return (
          <Pressable
            accessibilityRole="radio"
            accessibilityState={{ disabled, selected: isSelected }}
            disabled={disabled}
            key={account.accountId}
            onPress={() => onSelect(account.accountId)}
            style={({ pressed }) => [
              styles.accountOption,
              isSelected && styles.accountOptionSelected,
              pressed && styles.pressed,
            ]}>
            <Image
              accessibilityIgnoresInvertColors
              source={getAccountLogo(account.bankCode, account.bankName, account.accountId === safeboxId)}
              style={styles.logo}
            />
            <View style={styles.accountText}>
              <Text numberOfLines={1} style={styles.accountName}>
                {account.accountId === safeboxId ? '도담 계좌' : account.accountName}
              </Text>
              <Text numberOfLines={1} style={styles.accountNumber}>
                {`${account.bankName} ${account.accountNoMasked}`}
              </Text>
            </View>
            <Text style={styles.accountBalance}>{formatCurrency(account.balance)}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** 자산 화면의 입출금 계좌 간 이체. 확인 창과 실제 호출은 호출부가 맡는다. */
export function AccountTransferBottomSheet({
  accounts,
  safebox,
  isSubmitting,
  errorMessage,
  onClose,
  onSubmit,
}: AccountTransferBottomSheetProps) {
  const [amountText, setAmountText] = useState('');
  const [fromId, setFromId] = useState<number | null>(
    () => accounts.find(({ accountId }) => accountId !== safebox?.accountId)?.accountId
      ?? accounts[0]?.accountId ?? null,
  );
  const [toId, setToId] = useState<number | null>(
    () => safebox?.accountId ?? accounts.find(({ accountId }) => accountId !== fromId)?.accountId ?? null,
  );

  const from = useMemo(() => accounts.find(({ accountId }) => accountId === fromId) ?? null, [accounts, fromId]);
  const toOptions = useMemo(() => accounts.filter(({ accountId }) => accountId !== fromId), [accounts, fromId]);
  const to = toOptions.find(({ accountId }) => accountId === toId) ?? null;

  // 도담 계좌에서는 목표에 담긴 돈을 보낼 수 없다(서버 SAFEBOX_ALLOCATED_BALANCE와 같은 규칙).
  const maxAmount = from === null
    ? 0
    : from.accountId === safebox?.accountId
      ? Math.min(from.balance, safebox.availableAmount)
      : Math.max(from.balance, 0);
  const amount = Number(digitsOf(amountText) || '0');
  const exceedsBalance = amount > maxAmount;
  const canSubmit = !isSubmitting && from !== null && to !== null && amount > 0 && !exceedsBalance;

  return (
    <Modal animationType="fade" onRequestClose={onClose} statusBarTranslucent transparent visible>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="계좌 이체 닫기"
          accessibilityRole="button"
          disabled={isSubmitting}
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <Text style={styles.title}>계좌 이체</Text>

          <ScrollView keyboardShouldPersistTaps="handled" style={styles.body}>
            {accounts.length < 2 ? (
              <Text style={styles.emptyText}>이체하려면 입출금 계좌가 두 개 이상 필요해요.</Text>
            ) : (
              <>
                <Text style={styles.fieldLabel}>보낼 계좌</Text>
                <AccountOptions
                  accounts={accounts}
                  disabled={isSubmitting}
                  safeboxId={safebox?.accountId ?? null}
                  selectedId={fromId}
                  onSelect={(accountId) => {
                    setFromId(accountId);
                    if (accountId === toId) setToId(null);
                  }}
                />

                <Text style={styles.fieldLabel}>받는 계좌</Text>
                <AccountOptions
                  accounts={toOptions}
                  disabled={isSubmitting}
                  safeboxId={safebox?.accountId ?? null}
                  selectedId={toId}
                  onSelect={setToId}
                />
              </>
            )}

            <Text style={styles.fieldLabel}>금액</Text>
            <View style={[styles.amountRow, exceedsBalance && styles.amountRowError]}>
              <TextInput
                accessibilityLabel="보낼 금액"
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
                ? `보낼 수 있는 금액은 ${formatCurrency(maxAmount)}까지예요`
                : from?.accountId === safebox?.accountId && from !== null
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
              if (from !== null && to !== null) onSubmit(from, to, amount);
            }}
            style={({ pressed }) => [
              styles.submitButton,
              !canSubmit && styles.submitButtonDisabled,
              pressed && canSubmit && styles.pressed,
            ]}>
            {isSubmitting ? (
              <ActivityIndicator color={colors.white} />
            ) : (
              <Text style={styles.submitLabel}>보내기</Text>
            )}
          </Pressable>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
