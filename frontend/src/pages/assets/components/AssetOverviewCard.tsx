import { Text, View } from 'react-native';

import { AssetDonutChart } from '@/components/assetDonutChart/AssetDonutChart';
import { colors } from '@/styles';
import type { AssetOverview } from '@/types/asset.types';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles } from './AssetOverviewCard.styles';

const compositionColors = {
  strong: colors.home.assetChart.strong,
  medium: colors.home.assetChart.medium,
  light: colors.home.assetChart.light,
} as const;

interface AssetOverviewCardProps {
  overview: AssetOverview;
}

export function AssetOverviewCard({ overview }: AssetOverviewCardProps) {
  const { netAsset, changeFromLastMonth, composition } = overview;

  return (
    <View style={styles.card}>
      <Text style={styles.label}>순자산</Text>

      <Text
        adjustsFontSizeToFit
        minimumFontScale={0.8}
        numberOfLines={1}
        style={styles.totalAmount}
      >
        {formatNumber(netAsset)}
        <Text style={styles.currencyUnit}>원</Text>
      </Text>

      {changeFromLastMonth !== null && (
        <View style={styles.changeRow}>
          <Text style={styles.change}>
            {changeFromLastMonth >= 0 ? '↑' : '↓'}{' '}
            {formatCurrency(Math.abs(changeFromLastMonth))}
          </Text>
          <Text style={styles.changeDescription}> · 지난 달 대비</Text>
        </View>
      )}

      <View style={styles.divider} />

      <View style={styles.composition}>
        <AssetDonutChart composition={composition} size={68} strokeWidth={16} />

        <View style={styles.legend}>
          {composition.map((item) => (
            <View key={item.label} style={styles.legendRow}>
              <View style={styles.legendLabelGroup}>
                <View
                  style={[
                    styles.legendDot,
                    { backgroundColor: compositionColors[item.tone] },
                  ]}
                />
                <Text style={styles.legendLabel}>{item.label}</Text>
              </View>

              <Text style={styles.legendAmount}>
                {formatCurrency(item.amount)}
              </Text>
            </View>
          ))}
        </View>
      </View>
    </View>
  );
}
