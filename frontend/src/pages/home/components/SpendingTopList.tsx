import { SymbolView } from 'expo-symbols';
import { Text, View } from 'react-native';

import { HOME_TOP_SPENDING } from '@/mocks/home';
import { colors, componentSize } from '@/styles';
import { formatCurrency } from '@/utils/currency';

import { styles } from './SpendingTopList.styles';

export function SpendingTopList() {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>이번 달 소비 TOP 3</Text>

      {HOME_TOP_SPENDING.map((item) => (
        <View key={item.rank} style={styles.row}>
          <View style={[styles.rankBadge, item.rank > 1 && styles.secondaryRankBadge]}>
            <Text style={styles.rankLabel}>{item.rank}</Text>
          </View>

          <Text style={styles.categoryLabel}>{item.label}</Text>
          <Text style={styles.categoryAmount}>{formatCurrency(item.amount)}</Text>

          <View style={styles.changeGroup}>
            <Text style={styles.changeLabel}>{`지난 달 대비 ${item.changePercent}%`}</Text>
            <SymbolView
              name={
                item.direction === 'down'
                  ? { android: 'arrow_downward', ios: 'arrow.down', web: 'arrow_downward' }
                  : { android: 'arrow_upward', ios: 'arrow.up', web: 'arrow_upward' }
              }
              size={componentSize.icon.small}
              tintColor={
                item.direction === 'down' ? colors.primary.green : colors.primary.orange
              }
            />
          </View>
        </View>
      ))}
    </View>
  );
}
