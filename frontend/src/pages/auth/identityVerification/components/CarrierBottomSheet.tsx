import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { styles } from './CarrierBottomSheet.styles';

const CARRIER_OPTIONS = [
  'SKT',
  'KT',
  'LGU+',
  '알뜰폰(SKT)',
  '알뜰폰(KT)',
  '알뜰폰(LGU+)',
] as const;

export type Carrier = (typeof CARRIER_OPTIONS)[number];

interface CarrierBottomSheetProps {
  visible: boolean;
  selectedCarrier: Carrier | null;
  onSelect: (carrier: Carrier) => void;
  onClose: () => void;
}

export function CarrierBottomSheet({
  visible,
  selectedCarrier,
  onSelect,
  onClose,
}: CarrierBottomSheetProps) {
  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel="통신사 선택 닫기"
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.header}>
            <Text style={styles.title}>통신사 선택</Text>

            <Pressable
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="닫기"
              hitSlop={8}
              style={styles.closeButton}>
              <Text style={styles.closeIcon}>×</Text>
            </Pressable>
          </View>

          <ScrollView style={styles.optionList} keyboardShouldPersistTaps="handled">
            {CARRIER_OPTIONS.map((carrier) => {
              const isSelected = carrier === selectedCarrier;

              return (
                <Pressable
                  key={carrier}
                  onPress={() => onSelect(carrier)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: isSelected }}
                  style={({ pressed }) => [
                    styles.option,
                    isSelected && styles.selectedOption,
                    pressed && styles.pressedOption,
                  ]}>
                  <Text style={styles.optionLabel}>{carrier}</Text>
                </Pressable>
              );
            })}
          </ScrollView>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
