import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { MOCK_ASSET_SECTIONS } from '@/mocks/assets';
import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';
import { formatCurrency } from '@/utils/currency';

import { styles } from './GoalLinkedAccounts.styles';

interface GoalLinkedAccountsProps {
  accountIds: readonly string[];
  onManage: () => void;
}

export function GoalLinkedAccounts({
  accountIds,
  onManage,
}: GoalLinkedAccountsProps) {
  const accounts = MOCK_ASSET_SECTIONS.flatMap(({ items }) => items).filter(
    ({ id }) => accountIds.includes(id),
  );

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
            ({ id }) => id === account.institutionId,
          );

          if (!institution) {
            return null;
          }

          return (
            <View
              accessible
              accessibilityLabel={`${institution.name}, ${account.productName}, ${formatCurrency(account.amount)}`}
              key={account.id}
              style={[
                styles.accountRow,
                index < accounts.length - 1 && styles.accountDivider,
              ]}
            >
              <Image
                accessibilityIgnoresInvertColors
                contentFit="contain"
                source={institution.logo}
                style={styles.logo}
              />

              <View style={styles.accountInformation}>
                <Text numberOfLines={1} style={styles.bankName}>
                  {institution.name}
                </Text>
                <Text numberOfLines={1} style={styles.productName}>
                  {account.productName}
                </Text>
              </View>

              <Text numberOfLines={1} style={styles.amount}>
                {formatCurrency(account.amount)}
              </Text>
            </View>
          );
        })}
      </View>
    </View>
  );
}
