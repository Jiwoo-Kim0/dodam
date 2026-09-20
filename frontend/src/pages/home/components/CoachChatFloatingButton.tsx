import { Image } from 'expo-image';
import { Pressable, View } from 'react-native';

import { styles } from './CoachChatFloatingButton.styles';

type CoachChatFloatingButtonProps = {
  onPress: () => void;
};

/**
 * 홈 어디를 보고 있든 도리에게 물어볼 수 있게 화면에 띄워 두는 버튼이다.
 * 스크롤뷰 뒤에 두어 목록 위에 뜨고, 탭바 높이만큼 띄워 가리지 않게 한다.
 */
export function CoachChatFloatingButton({ onPress }: CoachChatFloatingButtonProps) {
  return (
    <Pressable
      accessibilityHint="도리에게 묻기 화면을 열어요"
      accessibilityLabel="도리에게 묻기"
      accessibilityRole="button"
      onPress={onPress}
      style={({ pressed }) => [styles.button, pressed && styles.pressed]}>
      <View style={styles.iconWrap}>
        <Image
          contentFit="contain"
          source={require('@/assets/characters/dori/dori_questionmark.png')}
          style={styles.icon}
        />
      </View>
    </Pressable>
  );
}
