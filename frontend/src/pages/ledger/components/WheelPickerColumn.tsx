import { memo, useEffect, useRef } from 'react';
import {
  FlatList,
  Text,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';

import { componentSize } from '@/styles';

import { styles } from './YearMonthPickerBottomSheet.styles';

export const WHEEL_ITEM_HEIGHT = componentSize.touchTarget;

interface WheelPickerColumnProps {
  options: readonly number[];
  selectedValue: number;
  suffix: string;
  onSelect: (value: number) => void;
}

export const WheelPickerColumn = memo(function WheelPickerColumn({
  options,
  selectedValue,
  suffix,
  onSelect,
}: WheelPickerColumnProps) {
  const listRef = useRef<FlatList<number>>(null);
  const selectedIndex = Math.max(options.indexOf(selectedValue), 0);

  useEffect(() => {
    listRef.current?.scrollToOffset({
      offset: selectedIndex * WHEEL_ITEM_HEIGHT,
      animated: false,
    });
  }, [selectedIndex]);

  const handleScrollEnd = (
    event: NativeSyntheticEvent<NativeScrollEvent>,
  ) => {
    const index = Math.round(
      event.nativeEvent.contentOffset.y / WHEEL_ITEM_HEIGHT,
    );
    const boundedIndex = Math.min(
      Math.max(index, 0),
      options.length - 1,
    );
    const nextValue = options[boundedIndex];

    if (nextValue !== undefined && nextValue !== selectedValue) {
      onSelect(nextValue);
    }
  };

  return (
    <FlatList
      ref={listRef}
      data={options}
      extraData={selectedValue}
      initialScrollIndex={selectedIndex}
      keyExtractor={(option) => option.toString()}
      getItemLayout={(_, index) => ({
        length: WHEEL_ITEM_HEIGHT,
        offset: WHEEL_ITEM_HEIGHT * index,
        index,
      })}
      renderItem={({ item }) => {
        const isSelected = item === selectedValue;

        return (
          <View
            accessibilityRole="radio"
            accessibilityState={{ selected: isSelected }}
            style={styles.option}>
            <Text
              style={[
                styles.optionLabel,
                isSelected && styles.selectedOptionLabel,
              ]}>
              {item}
              {suffix}
            </Text>
          </View>
        );
      }}
      snapToInterval={WHEEL_ITEM_HEIGHT}
      decelerationRate="fast"
      bounces={false}
      overScrollMode="never"
      showsVerticalScrollIndicator={false}
      contentContainerStyle={styles.columnContent}
      onMomentumScrollEnd={handleScrollEnd}
      style={styles.column}
    />
  );
});
