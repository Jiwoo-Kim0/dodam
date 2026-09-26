import { Image, Pressable, Text, View } from 'react-native';

import type { AssetItem } from '@/types/asset.types';
import { formatCurrency } from '@/utils/currency';
import { getInstitutionLogo } from '@/utils/institution';

import { styles } from './AssetListItem.styles';

export interface AssetItemActions {
  expanded: boolean;
  onToggle: () => void;
  onFill: () => void;
  onSend: () => void;
}

interface AssetListItemProps {
  item: AssetItem;
  isLast: boolean;
  /** 입출금 계좌처럼 이체할 수 있는 항목. 누르면 아래에 채우기·보내기가 펼쳐진다. */
  actions?: AssetItemActions;
}

export function AssetListItem({ item, isLast, actions }: AssetListItemProps) {
  const icon = getInstitutionLogo(item.institutionId);
  const content = (
    <>
      <Image
        source={icon}
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
    </>
  );
  const label = `${item.productName}, ${item.description}, ${formatCurrency(item.amount)}`;

  if (!actions) {
    return (
      <View accessible accessibilityLabel={label} style={[styles.container, !isLast && styles.divider]}>
        {content}
      </View>
    );
  }

  return (
    <View style={!isLast && styles.divider}>
      <Pressable
        accessibilityLabel={label}
        accessibilityRole="button"
        accessibilityState={{ expanded: actions.expanded }}
        onPress={actions.onToggle}
        style={({ pressed }) => [styles.container, pressed && styles.pressed]}>
        {content}
      </Pressable>
      {actions.expanded ? (
        <View style={styles.actions}>
          <Pressable
            accessibilityRole="button"
            onPress={actions.onFill}
            style={({ pressed }) => [styles.actionButton, pressed && styles.pressed]}>
            <Text style={styles.actionLabel}>채우기</Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            onPress={actions.onSend}
            style={({ pressed }) => [styles.actionButton, styles.actionButtonPrimary, pressed && styles.pressed]}>
            <Text style={[styles.actionLabel, styles.actionLabelPrimary]}>보내기</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
}
