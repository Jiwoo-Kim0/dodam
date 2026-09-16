import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';
import type { GoalAccountApiResponse } from '@/types/goalApi.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from './GoalLinkedAccounts.styles';

interface GoalLinkedAccountsProps {
  accounts: readonly GoalAccountApiResponse[];
  onManage: () => void;
}

export function GoalLinkedAccounts({
  accounts,
  onManage,
}: GoalLinkedAccountsProps) {
  return (
    <View style={styles.section}>
      <View style={styles.header}>
        <Text style={styles.title}>{`연결 계좌 (${accounts.length})`}</Text>
        <Pressable
          accessibilityRole="button"
          onPress={onManage}
          style={({ pressed }) => [
            styles.manageButton,
            pressed && styles.manageButtonPressed,
          ]}
        >
          <Text style={styles.manageButtonLabel}>관리</Text>
        </Pressable>
      </View>

      <View style={styles.card}>
        {accounts.map((account, index) => {
          const institution = FINANCIAL_INSTITUTIONS.find(
            ({ name }) =>
              account.bankName.includes(name.replace('은행', '')) ||
              name.includes(account.bankName.replace('은행', '')),
          );

          return (
            <View
              accessible
              accessibilityLabel={`${account.bankName}, ${account.accountName}, ${formatCurrency(account.balance)}`}
              key={account.accountId}
              style={[
                styles.accountRow,
                index < accounts.length - 1 && styles.accountDivider,
              ]}
            >
              {institution ? (
                <Image
                  accessibilityIgnoresInvertColors
                  contentFit="contain"
                  source={institution.logo}
                  style={styles.logo}
                />
              ) : null}

              <View style={styles.accountInformation}>
                <Text numberOfLines={1} style={styles.bankName}>
                  {account.bankName}
                </Text>
                <Text numberOfLines={1} style={styles.productName}>
                  {account.accountName}
                </Text>
              </View>

              <Text numberOfLines={1} style={styles.amount}>
                {formatCurrency(account.balance)}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}
