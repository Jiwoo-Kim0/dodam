import { ActivityIndicator, Text, View } from 'react-native';

import { colors } from '@/styles';
import type { AssetOverview } from '@/types/asset.types';
import { formatCurrency, formatNumber } from '@/utils/currency';

import { AssetDonutChart } from '@/components/assetDonutChart/AssetDonutChart';
import { CardDetailButton } from './CardDetailButton';
import { styles } from './AssetSummaryCard.styles';

type AssetSummaryCardProps = {
  hasError: boolean;
  isLoading: boolean;
  onDetailPress: () => void;
  overview: AssetOverview | null;
};

const compositionColors = {
  strong: colors.home.assetChart.strong,
  medium: colors.home.assetChart.medium,
  light: colors.home.assetChart.light,
} as const;

export function AssetSummaryCard({
  hasError,
  isLoading,
  onDetailPress,
  overview,
}: AssetSummaryCardProps) {
  return (
    <View style={styles.card}>
      <View style={styles.content}>
        <View style={styles.copy}>
          <Text style={styles.title}>내 자산</Text>
          {isLoading ? (
            <ActivityIndicator
              color={colors.primary.green}
              size="small"
              style={styles.loadingIndicator}
            />
          ) : overview ? (
            <>
              <Text
                adjustsFontSizeToFit
                minimumFontScale={0.75}
                numberOfLines={1}
                style={styles.amount}
              >
                {formatNumber(overview.netAsset)}
                <Text style={styles.currencyUnit}> 원</Text>
              </Text>
              {overview.changeFromLastMonth !== null ? (
                <View style={styles.changeRow}>
                  <Text style={styles.changePrefix}>지난 달 대비</Text>
                  <Text style={styles.changeArrow}>
                    {overview.changeFromLastMonth >= 0 ? '↑' : '↓'}
                  </Text>
                  <Text style={styles.changeAmount}>
                    {formatCurrency(Math.abs(overview.changeFromLastMonth))}
                  </Text>
                </View>
              ) : (
                <Text style={styles.netAssetLabel}>총자산</Text>
              )}
            </>
          ) : (
            <Text style={styles.errorText}>
              {hasError ? '자산을 불러오지 못했어요' : '연결된 자산이 없어요'}
            </Text>
          )}
        </View>

        {overview ? (
          <View style={styles.chartGroup}>
            <AssetDonutChart
              composition={overview.composition}
              size={60}
              strokeWidth={14}
            />

            <View style={styles.legend}>
              {overview.composition.map((item) => (
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
        ) : null}
      </View>
      <CardDetailButton
        accessibilityLabel="내 자산 자세히 보기"
        centerVertically
        onPress={onDetailPress}
      />
    </View>
  );
}
