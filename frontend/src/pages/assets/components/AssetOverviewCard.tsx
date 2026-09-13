import { Text, View } from 'react-native';

import { AssetDonutChart } from '@/components/assetDonutChart/AssetDonutChart';
import { MOCK_ASSET_OVERVIEW } from '@/mocks/assets';
import { colors } from '@/styles';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { styles } from './AssetOverviewCard.styles';

const compositionColors = {
  strong: colors.home.assetChart.strong,
  medium: colors.home.assetChart.medium,
  light: colors.home.assetChart.light,
} as const;

export function AssetOverviewCard() {
  const { totalAsset, changeFromLastMonth, composition } =
    MOCK_ASSET_OVERVIEW;
  const isIncrease = changeFromLastMonth >= 0;

  return (
    <View style={styles.card}>
      <Text style={styles.label}>총자산</Text>

      <Text
        adjustsFontSizeToFit
        minimumFontScale={0.8}
        numberOfLines={1}
        style={styles.totalAmount}
      >
        {formatNumber(totalAsset)}
        <Text style={styles.currencyUnit}>원</Text>
      </Text>

      <View style={styles.changeRow}>
        <Text style={styles.change}>
          {isIncrease ? '↑' : '↓'}{' '}
          {formatCurrency(Math.abs(changeFromLastMonth))}
        </Text>
        <Text style={styles.changeDescription}> · 지난 달 대비</Text>
      </View>

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
