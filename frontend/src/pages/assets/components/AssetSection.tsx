import { Text, View } from 'react-native';

import type { AssetSection as AssetSectionData } from '@/types/asset.types';
import { formatCurrency } from '@/utils/currency';

import { AssetListItem, type AssetItemActions } from './AssetListItem';
import { styles } from './AssetSection.styles';
import { getAssetSectionTotal } from '../utils/assetCalculations';

interface AssetSectionProps {
  section: AssetSectionData;
  /** 이체할 수 있는 섹션이면 항목별 채우기·보내기 동작을 만든다. */
  getItemActions?: (itemId: string) => AssetItemActions | undefined;
}

export function AssetSection({ section, getItemActions }: AssetSectionProps) {
  const totalAmount = getAssetSectionTotal(section);
  const shouldShowTotal = section.items.length > 1 || Boolean(section.summaryPrefix);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>{section.title}</Text>

        {shouldShowTotal ? (
          <Text
            adjustsFontSizeToFit
            minimumFontScale={0.8}
            numberOfLines={1}
            style={styles.total}
          >
            {section.summaryPrefix
              ? `${section.summaryPrefix} ${formatCurrency(totalAmount)}`
              : `${section.id === 'loan' ? '총 대출액' : '총 보유액'} ${formatCurrency(totalAmount)}`}
          </Text>
        ) : null}
      </View>

      <View style={styles.list}>
        {section.items.length > 0 ? (
          section.items.map((item, index) => (
            <AssetListItem
              key={item.id}
              actions={getItemActions?.(item.id)}
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
