import { Image, Pressable, ScrollView, Text, View } from 'react-native';

import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';
import type { GoalAccount } from '@/mocks/goalAccounts';

import { styles } from './GoalAccountSelectionList.styles';

interface GoalAccountSelectionListProps {
  accounts: readonly GoalAccount[];
  selectedAccountIds: readonly string[];
  onToggle: (accountId: string) => void;
}

export function GoalAccountSelectionList({
  accounts,
  selectedAccountIds,
  onToggle,
}: GoalAccountSelectionListProps) {
  return (
    <View style={styles.container}>
      <Text style={styles.sectionTitle}>연결된 계좌</Text>

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.accountList}>
        {accounts.map((account) => {
          const institution = FINANCIAL_INSTITUTIONS.find(
            ({ id }) => id === account.institutionId,
          );
          const isSelected = selectedAccountIds.includes(account.id);

          if (!institution) {
            return null;
          }

          return (
            <View
              key={account.id}
              style={[
                styles.accountCard,
                isSelected && styles.selectedAccountCard,
              ]}>
              <Pressable
                onPress={() => onToggle(account.id)}
                accessibilityRole="checkbox"
                accessibilityLabel={`${account.displayName} ${account.maskedAccountNumber}`}
                accessibilityState={{ checked: isSelected }}
                style={({ pressed }) => [
                  styles.accountRow,
                  pressed && styles.pressedRow,
                ]}>
                <Image
                  source={institution.logo}
                  accessibilityLabel={institution.name}
                  style={styles.bankLogo}
                />

                <View style={styles.accountInfo}>
                  <Text style={styles.bankName}>{account.displayName}</Text>
                  <Text style={styles.accountNumber}>
                    {account.maskedAccountNumber}
                  </Text>
                </View>

                <View
                  style={[
                    styles.checkCircle,
                    isSelected && styles.selectedCheckCircle,
                  ]}>
                  <Text
                    style={[
                      styles.checkmark,
                      isSelected && styles.selectedCheckmark,
                    ]}>
                    ✓
                  </Text>
                </View>
              </Pressable>
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}
