import { Image } from 'expo-image';
import { Pressable, Text, View } from 'react-native';

import { styles } from './SocialLoginButton.styles';

export type SocialProvider = 'kakao' | 'google' | 'naver';

interface SocialLoginButtonProps {
  provider: SocialProvider;
  onPress: () => void;
  label?: string;
  selected?: boolean;
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
  label,
  selected,
}: SocialLoginButtonProps) {
  const { label: defaultLabel, icon } = providers[provider];
  const displayLabel = label ?? defaultLabel;
  const isSelectable = selected !== undefined;

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole={isSelectable ? 'radio' : 'button'}
      accessibilityLabel={displayLabel}
      accessibilityState={isSelectable ? { selected } : undefined}
      style={({ pressed }) => [
        styles.button,
        buttonStyles[provider],
        selected && styles.selectedButton,
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
          {displayLabel}
        </Text>
      </View>
    </Pressable>
  );
}
