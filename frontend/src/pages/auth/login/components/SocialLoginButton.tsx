import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { styles } from './SocialLoginButton.styles';

export type SocialProvider = 'kakao' | 'google' | 'naver';

interface SocialLoginButtonProps {
  provider: SocialProvider;
  onPress: () => void;
}

const providers = {
  kakao: {
    label: '카카오 로그인',
    icon: require('@/assets/icons/social/kakao-icon.svg'),
  },
  google: {
    label: 'Google로 로그인',
    icon: require('@/assets/icons/social/google-icon.svg'),
  },
  naver: {
    label: '네이버 로그인',
    icon: require('@/assets/icons/social/naver-icon.svg'),
  },
} as const;

const buttonStyles = {
  kakao: styles.kakaoButton,
  google: styles.googleButton,
  naver: styles.naverButton,
};

const labelStyles = {
  kakao: styles.kakaoLabel,
  google: styles.googleLabel,
  naver: styles.naverLabel,
};

const iconStyles = {
  kakao: styles.kakaoIcon,
  google: styles.googleIcon,
  naver: styles.naverIcon,
};

export function SocialLoginButton({
  provider,
  onPress,
}: SocialLoginButtonProps) {
  const { label, icon } = providers[provider];

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [
        styles.button,
        buttonStyles[provider],
        pressed && styles.pressed,
      ]}
    >
      <View style={styles.content}>
        <View style={styles.iconSlot}>
          <Image
            source={icon}
            style={[styles.icon, iconStyles[provider]]}
            contentFit="contain"
          />
        </View>

        <Text style={[styles.label, labelStyles[provider]]}>
          {label}
        </Text>
      </View>
    </Pressable>
  );
}
