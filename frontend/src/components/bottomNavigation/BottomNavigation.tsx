import { forwardRef, useCallback, useMemo, useState } from 'react';

import { TabList, TabSlot, Tabs, TabTrigger } from 'expo-router/ui';
import type { TabTriggerSlotProps } from 'expo-router/ui';
import type { GestureResponderEvent, ImageSourcePropType } from 'react-native';
import { Animated, Image, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { routes } from '@/constants/routes';

import { styles } from './BottomNavigation.styles';

type BottomNavigationItemProps = TabTriggerSlotProps & {
  activeIcon: ImageSourcePropType;
  inactiveIcon: ImageSourcePropType;
  label: string;
};

const BottomNavigationItem = forwardRef<View, BottomNavigationItemProps>(
  function BottomNavigationItem(
    {
      activeIcon,
      href: _href,
      inactiveIcon,
      isFocused = false,
      label,
      onPress,
      ...pressableProps
    },
    ref,
  ) {
    const icon = isFocused ? activeIcon : inactiveIcon;
    const [scale] = useState(() => new Animated.Value(1));
    const [rotation] = useState(() => new Animated.Value(0));
    const rotate = useMemo(
      () =>
        rotation.interpolate({
          inputRange: [-1, 0, 1],
          outputRange: ['-8deg', '0deg', '8deg'],
        }),
      [rotation],
    );

    const playIconAnimation = useCallback(() => {
      scale.stopAnimation();
      rotation.stopAnimation();
      scale.setValue(1);
      rotation.setValue(0);

      Animated.parallel([
        Animated.sequence([
          Animated.timing(scale, {
            duration: 90,
            toValue: 1.18,
            useNativeDriver: true,
          }),
          Animated.timing(scale, {
            duration: 80,
            toValue: 0.96,
            useNativeDriver: true,
          }),
          Animated.timing(scale, {
            duration: 90,
            toValue: 1.08,
            useNativeDriver: true,
          }),
          Animated.timing(scale, {
            duration: 80,
            toValue: 1,
            useNativeDriver: true,
          }),
        ]),
        Animated.sequence([
          Animated.timing(rotation, {
            duration: 70,
            toValue: -1,
            useNativeDriver: true,
          }),
          Animated.timing(rotation, {
            duration: 90,
            toValue: 1,
            useNativeDriver: true,
          }),
          Animated.timing(rotation, {
            duration: 80,
            toValue: -0.5,
            useNativeDriver: true,
          }),
          Animated.timing(rotation, {
            duration: 100,
            toValue: 0,
            useNativeDriver: true,
          }),
        ]),
      ]).start();
    }, [rotation, scale]);

    const handlePress = (event: GestureResponderEvent) => {
      playIconAnimation();
      onPress?.(event);
    };

    return (
      <Pressable
        {...pressableProps}
        accessibilityLabel={label}
        accessibilityRole="tab"
        accessibilityState={{ selected: isFocused }}
        onPress={handlePress}
        ref={ref}
        style={styles.tab}>
        <Animated.View
          style={[styles.iconContainer, { transform: [{ scale }, { rotate }] }]}>
          <Image fadeDuration={0} resizeMode="contain" source={icon} style={styles.icon} />
        </Animated.View>
        <Text style={[styles.label, isFocused && styles.selectedLabel]}>{label}</Text>
      </Pressable>
    );
  },
);

export function BottomNavigation() {
  const insets = useSafeAreaInsets();

  return (
    <Tabs options={{ backBehavior: 'history' }} style={styles.container}>
      <TabSlot style={styles.content} />

      <TabList
        style={[
          styles.tabList,
          {
            height: styles.tabList.height + insets.bottom,
            paddingBottom: insets.bottom,
          },
        ]}>
          <TabTrigger asChild href={routes.home} name="home">
            <BottomNavigationItem
              activeIcon={require('@/assets/icons/navigation/home_active_tab.png')}
              inactiveIcon={require('@/assets/icons/navigation/home_inactive_tab.png')}
              label="홈"
            />
          </TabTrigger>

          <TabTrigger asChild href={routes.ledger} name="ledger">
            <BottomNavigationItem
              activeIcon={require('@/assets/icons/navigation/ledger_active_tab.png')}
              inactiveIcon={require('@/assets/icons/navigation/ledger_inactive_tab.png')}
              label="가계부"
            />
          </TabTrigger>

          <TabTrigger asChild href={routes.assets} name="assets">
            <BottomNavigationItem
              activeIcon={require('@/assets/icons/navigation/assets_active_tab.png')}
              inactiveIcon={require('@/assets/icons/navigation/assets_inactive_tab.png')}
              label="자산"
            />
          </TabTrigger>

          <TabTrigger asChild href={routes.discovery} name="discovery">
            <BottomNavigationItem
              activeIcon={require('@/assets/icons/navigation/discovery_active_tab.png')}
              inactiveIcon={require('@/assets/icons/navigation/discovery_inactive_tab.png')}
              label="발견"
            />
          </TabTrigger>

          <TabTrigger asChild href={routes.report} name="report">
            <BottomNavigationItem
              activeIcon={require('@/assets/icons/navigation/report_active_tab.png')}
              inactiveIcon={require('@/assets/icons/navigation/report_inactive_tab.png')}
              label="리포트"
            />
          </TabTrigger>
      </TabList>
    </Tabs>
  );
}
