import { SymbolView } from 'expo-symbols';
import { Modal, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { colors, componentSize } from '@/styles';

import { styles } from './HomeMenuBottomSheet.styles';

interface HomeMenuBottomSheetProps {
  isConnected: boolean;
  isProcessing: boolean;
  onClose: () => void;
  onFinancePress: () => void;
  onLogoutPress: () => void;
  onSubscriptionsPress: () => void;
  visible: boolean;
}

export function HomeMenuBottomSheet({
  isConnected,
  isProcessing,
  onClose,
  onFinancePress,
  onLogoutPress,
  onSubscriptionsPress,
  visible,
}: HomeMenuBottomSheetProps) {
  return (
    <Modal
      animationType="fade"
      onRequestClose={onClose}
      statusBarTranslucent
      transparent
      visible={visible}>
      <View style={styles.overlay}>
        <Pressable
          accessibilityLabel="메뉴 닫기"
          accessibilityRole="button"
          onPress={onClose}
          style={styles.backdrop}
        />

        <SafeAreaView edges={['bottom']} style={styles.sheet}>
          <View style={styles.handle} />
          <View style={styles.header}>
            <View>
              <Text style={styles.title}>메뉴</Text>
              <Text style={styles.description}>구독과 계정, 금융 연결을 관리해요.</Text>
            </View>
            <Pressable
              accessibilityLabel="메뉴 닫기"
              accessibilityRole="button"
              hitSlop={8}
              onPress={onClose}
              style={({ pressed }) => [
                styles.closeButton,
                pressed && styles.pressed,
              ]}>
              <SymbolView
                name={{ android: 'close', ios: 'xmark', web: 'close' }}
                size={componentSize.icon.close}
                tintColor={colors.textSecondary}
              />
            </Pressable>
          </View>

          <View style={styles.menuList}>
            <Pressable
              accessibilityRole="button"
              accessibilityState={{ disabled: isProcessing }}
              disabled={isProcessing}
              onPress={onSubscriptionsPress}
              style={({ pressed }) => [
                styles.menuItem,
                pressed && styles.pressed,
                isProcessing && styles.disabled,
              ]}>
              <View style={styles.iconBackground}>
                <SymbolView
                  name={{
                    android: 'autorenew',
                    ios: 'arrow.triangle.2.circlepath',
                    web: 'autorenew',
                  }}
                  size={componentSize.icon.small + 4}
                  tintColor={colors.primary.green}
                />
              </View>
              <View style={styles.menuCopy}>
                <Text style={styles.menuLabel}>내 구독 보기</Text>
                <Text style={styles.menuDescription}>
                  매달 나가는 구독료를 한눈에 확인해요.
                </Text>
              </View>
              <Text style={styles.chevron}>›</Text>
            </Pressable>

            <Pressable
              accessibilityRole="button"
              accessibilityState={{ disabled: isProcessing }}
              disabled={isProcessing}
              onPress={onFinancePress}
              style={({ pressed }) => [
                styles.menuItem,
                pressed && styles.pressed,
                isProcessing && styles.disabled,
              ]}>
              <View style={styles.iconBackground}>
                <SymbolView
                  name={{
                    android: 'link',
                    ios: 'link',
                    web: 'link',
                  }}
                  size={componentSize.icon.small + 4}
                  tintColor={colors.primary.green}
                />
              </View>
              <View style={styles.menuCopy}>
                <Text style={styles.menuLabel}>
                  {isConnected ? '금융 연결 해제' : '금융 정보 연결'}
                </Text>
                <Text style={styles.menuDescription}>
                  {isConnected
                    ? '연결된 금융 데이터를 더 이상 동기화하지 않아요.'
                    : '자산과 소비 내역을 한곳에서 확인해요.'}
                </Text>
              </View>
              <Text style={styles.chevron}>›</Text>
            </Pressable>

            <Pressable
              accessibilityRole="button"
              accessibilityState={{ disabled: isProcessing }}
              disabled={isProcessing}
              onPress={onLogoutPress}
              style={({ pressed }) => [
                styles.menuItem,
                pressed && styles.pressed,
                isProcessing && styles.disabled,
              ]}>
              <View style={[styles.iconBackground, styles.logoutIconBackground]}>
                <SymbolView
                  name={{ android: 'logout', ios: 'rectangle.portrait.and.arrow.right', web: 'logout' }}
                  size={componentSize.icon.small + 4}
                  tintColor={colors.primary.orange}
                />
              </View>
              <View style={styles.menuCopy}>
                <Text style={[styles.menuLabel, styles.logoutLabel]}>로그아웃</Text>
                <Text style={styles.menuDescription}>현재 기기에서 안전하게 로그아웃해요.</Text>
              </View>
              <Text style={styles.chevron}>›</Text>
            </Pressable>
          </View>
        </SafeAreaView>
      </View>
    </Modal>
  );
}
