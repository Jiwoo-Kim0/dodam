import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { getSubscriptionServiceIcon } from '@/constants/subscriptionServiceIcons';
import { formatNumber } from '@/utils/currency';

import type { SubscriptionListItemModel } from '../hooks/useSubscriptions';
import { styles } from './SubscriptionListItem.styles';

interface SubscriptionListItemProps {
  isSelected: boolean;
  onToggle: (subscriptionId: string) => void;
  subscription: SubscriptionListItemModel;
}

export function SubscriptionListItem({
  isSelected,
  onToggle,
  subscription,
}: SubscriptionListItemProps) {
  return (
    <Pressable
      accessibilityLabel={`${subscription.name} 매달 ${formatNumber(subscription.monthlyAmount)}원`}
      accessibilityRole="checkbox"
      accessibilityState={{ checked: isSelected }}
      onPress={() => onToggle(subscription.id)}
      style={({ pressed }) => [
        styles.row,
        isSelected && styles.rowSelected,
        pressed && styles.rowPressed,
      ]}>
      <Image
        contentFit="contain"
        source={getSubscriptionServiceIcon(subscription.name)}
        style={styles.serviceIcon}
      />

      <View style={styles.nameGroup}>
        <Text numberOfLines={1} style={styles.name}>
          {subscription.name}
        </Text>
        {/* 월 결제가 아니면 목록의 금액이 실제 청구액과 달라, 환산했다는 것을 밝힌다. */}
        {subscription.billingCycleNote ? (
          <Text style={styles.cycleNote}>{subscription.billingCycleNote}</Text>
        ) : null}
      </View>

      <Text style={styles.amount}>
        {formatNumber(subscription.monthlyAmount)}
        <Text style={styles.amountUnit}> 원</Text>
      </Text>
    </Pressable>
  );
}
