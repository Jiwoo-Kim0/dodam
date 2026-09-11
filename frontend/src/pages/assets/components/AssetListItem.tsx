import { Image, Text, View } from 'react-native';

import { FINANCIAL_INSTITUTIONS } from '@/mocks/financeConnection';
import type { AssetItem } from '@/types/asset.types';
import { formatCurrency } from '@/utils/currency';

import { styles } from './AssetListItem.styles';

interface AssetListItemProps {
  item: AssetItem;
  isLast: boolean;
}

export function AssetListItem({ item, isLast }: AssetListItemProps) {
  const institution = FINANCIAL_INSTITUTIONS.find(
    ({ id }) => id === item.institutionId,
  );

  if (!institution) {
    return null;
  }

  return (
    <View
      accessible
      accessibilityLabel={`${item.productName}, ${item.description}, ${formatCurrency(
        item.amount,
      )}`}
      style={[styles.container, !isLast && styles.divider]}
    >
      <Image
        source={institution.logo}
        accessibilityIgnoresInvertColors
        style={styles.logo}
      />

      <View style={styles.information}>
        <Text numberOfLines={1} style={styles.productName}>
          {item.productName}
        </Text>
        <Text numberOfLines={1} style={styles.description}>
          {item.description}
        </Text>
      </View>

      <Text numberOfLines={1} style={styles.amount}>
        {formatCurrency(item.amount)}
      </Text>
    </View>
  );
}
