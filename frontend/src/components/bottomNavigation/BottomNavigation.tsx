import { forwardRef, useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { usePathname, useRouter } from 'expo-router';
import { TabList, TabSlot, Tabs, TabTrigger } from 'expo-router/ui';
import type { Href } from 'expo-router';
import type { TabTriggerSlotProps } from 'expo-router/ui';
import type { GestureResponderEvent, ImageSourcePropType } from 'react-native';
import { Animated, Image, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { routes } from '@/constants/routes';

import { emitHomeTabReselect } from './homeTabReselectEvents';
import { styles } from './BottomNavigation.styles';

type BottomNavigationItemProps = TabTriggerSlotProps & {
  activeIcon: ImageSourcePropType;
  inactiveIcon: ImageSourcePropType;
  label: string;
  featured?: boolean;
  onFocusedPress?: (event: GestureResponderEvent) => void;
};

const BottomNavigationItem = forwardRef<View, BottomNavigationItemProps>(
  function BottomNavigationItem(
    {
      activeIcon,
      href: _href,
      inactiveIcon,
      isFocused = false,
      label,
      featured = false,
      onFocusedPress,
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

      if (isFocused) {
        onFocusedPress?.(event);
      }

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
          style={[
            styles.iconContainer,
            featured && styles.featuredIconContainer,
            isFocused && featured && styles.featuredIconContainerFocused,
            { transform: [{ scale }, { rotate }] },
          ]}>
          <Image fadeDuration={0} resizeMode="contain" source={icon} style={featured ? styles.featuredIcon : styles.icon} />
        </Animated.View>
        <Text style={[styles.label, isFocused && styles.selectedLabel, featured && styles.featuredLabel]}>{label}</Text>
      </Pressable>
    );
  },
);

export function BottomNavigation() {
  const insets = useSafeAreaInsets();
  const pathname = usePathname();
  const router = useRouter();
  const previousPath = useRef<string>(routes.home);

  useEffect(() => {
    const restorableTabs = [routes.home, routes.ledger, routes.assets, routes.discovery, routes.report];
    if (restorableTabs.some((route) => pathname === route || pathname.startsWith(`${route}/`))) {
      previousPath.current = pathname;
    }
  }, [pathname]);

  const closeCoach = useCallback((event: GestureResponderEvent) => {
    event.preventDefault();
    router.navigate(previousPath.current as Href);
  }, [router]);

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
              onFocusedPress={emitHomeTabReselect}
            />
          </TabTrigger>

          <TabTrigger asChild href={routes.ledger} name="ledger">
            <BottomNavigationItem
              activeIcon={require('@/assets/icons/navigation/ledger_active_tab.png')}
              inactiveIcon={require('@/assets/icons/navigation/ledger_inactive_tab.png')}
              label="가계부"
            />
          </TabTrigger>

          <TabTrigger asChild href={routes.coach as Href} name="coach">
            <BottomNavigationItem
              activeIcon={require('@/assets/characters/tori/tori_chat.png')}
              featured
              inactiveIcon={require('@/assets/characters/tori/tori_chat.png')}
              label="토리"
              onFocusedPress={closeCoach}
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
          {/* 홈에서 여는 리포트 화면도 탭 내비게이터에 등록해 둔다. */}
          <TabTrigger href={routes.report} name="report" style={{ display: 'none' }} />
      </TabList>
    </Tabs>
  );
}
