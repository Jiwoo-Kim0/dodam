import { Text, View } from 'react-native';

import type { AssetSection as AssetSectionData } from '@/types/asset.types';
import { formatCurrency } from '@/utils/currency';

import { AssetListItem } from './AssetListItem';
import { styles } from './AssetSection.styles';
import { getAssetSectionTotal } from '../utils/assetCalculations';

interface AssetSectionProps {
  section: AssetSectionData;
}

export function AssetSection({ section }: AssetSectionProps) {
  const totalAmount = getAssetSectionTotal(section);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>{section.title}</Text>

        <Text
          adjustsFontSizeToFit
          minimumFontScale={0.8}
          numberOfLines={1}
          style={styles.total}
        >
          {section.summaryPrefix
            ? `${section.summaryPrefix} ${formatCurrency(totalAmount)}`
            : formatCurrency(totalAmount)}
        </Text>
      </View>

      <View style={styles.list}>
        {section.items.length > 0 ? (
          section.items.map((item, index) => (
            <AssetListItem
              key={item.id}
              item={item}
              isLast={index === section.items.length - 1}
            />
          ))
        ) : (
          <Text style={styles.emptyText}>{`${section.title} 내역이 없어요.`}</Text>
        )}
      </View>
    </View>
  );
}
