import { Pressable, Text, View } from 'react-native';

import { styles } from './AgreementItem.styles';

interface AgreementItemProps {
  label: string;
  checked: boolean;
  onPress: () => void;
  emphasized?: boolean;
}

export function AgreementItem({
  label,
  checked,
  onPress,
  emphasized = false,
}: AgreementItemProps) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      style={[
        styles.container,
        emphasized && styles.emphasizedContainer,
      ]}>
      <View style={[styles.checkbox, checked && styles.checkedBox]}>
        {checked && <View style={styles.checkmark} />}
      </View>

      <Text style={[styles.label, emphasized && styles.emphasizedLabel]}>
        {label}
      </Text>
    </Pressable>
  );
}
