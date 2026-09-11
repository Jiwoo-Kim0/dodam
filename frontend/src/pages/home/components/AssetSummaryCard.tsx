import { Text, View } from 'react-native';

import { HOME_SUMMARY } from '@/mocks/home';
import { colors } from '@/styles';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { AssetDonutChart } from '@/components/assetDonutChart/AssetDonutChart';
import { CardDetailButton } from './CardDetailButton';
import { styles } from './AssetSummaryCard.styles';

type AssetSummaryCardProps = {
  onDetailPress: () => void;
};

const compositionColors = {
  strong: colors.home.assetChart.strong,
  medium: colors.home.assetChart.medium,
  light: colors.home.assetChart.light,
} as const;

export function AssetSummaryCard({ onDetailPress }: AssetSummaryCardProps) {
  const assetChange = HOME_SUMMARY.assetChangeFromLastMonth;

  return (
    <View style={styles.card}>
      <Text style={styles.title}>내 자산</Text>
      <View style={styles.content}>
        <View style={styles.copy}>
          <Text
            adjustsFontSizeToFit
            minimumFontScale={0.75}
            numberOfLines={1}
            style={styles.amount}
          >
            {formatNumber(HOME_SUMMARY.totalAsset)}
            <Text style={styles.currencyUnit}> 원</Text>
          </Text>
          <View style={styles.changeRow}>
            <Text style={styles.changeArrow}>{assetChange >= 0 ? '↑' : '↓'}</Text>
            <Text style={styles.changeAmount}>{formatCurrency(Math.abs(assetChange))}</Text>
          </View>
          <Text style={styles.changePrefix}>지난 달 대비</Text>
        </View>

        <View style={styles.chartGroup}>
          <AssetDonutChart composition={HOME_SUMMARY.assetComposition} />

          <View style={styles.legend}>
            {HOME_SUMMARY.assetComposition.map((item) => (
              <View key={item.label} style={styles.legendItem}>
                <View
                  style={[
                    styles.legendDot,
                    { backgroundColor: compositionColors[item.tone] },
                  ]}
                />
                <Text style={styles.legendLabel}>{item.label}</Text>
              </View>
            ))}
          </View>
        </View>
      </View>
      <CardDetailButton accessibilityLabel="내 자산 자세히 보기" onPress={onDetailPress} />
    </View>
  );
}
