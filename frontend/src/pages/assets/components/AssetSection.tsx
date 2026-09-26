import { Pressable, Text, View } from 'react-native';

import type { AssetSection as AssetSectionData } from '@/types/asset.types';
import { formatCurrency } from '@/utils/currency';

import { AssetListItem } from './AssetListItem';
import { styles } from './AssetSection.styles';
import { getAssetSectionTotal } from '../utils/assetCalculations';

interface AssetSectionProps {
  section: AssetSectionData;
  /** 섹션 제목 옆 버튼(예: 입출금의 계좌 이체). */
  action?: { label: string; onPress: () => void };
}

export function AssetSection({ section, action }: AssetSectionProps) {
  const totalAmount = getAssetSectionTotal(section);
  const shouldShowTotal = section.items.length > 1 || Boolean(section.summaryPrefix);

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.titleRow}>
          <Text style={styles.title}>{section.title}</Text>
          {action ? (
            <Pressable
              accessibilityRole="button"
              hitSlop={8}
              onPress={action.onPress}
              style={({ pressed }) => [styles.action, pressed && styles.actionPressed]}>
              <Text style={styles.actionLabel}>{action.label}</Text>
            </Pressable>
          ) : null}
        </View>

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
