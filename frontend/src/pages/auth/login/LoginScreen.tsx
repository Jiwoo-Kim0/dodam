import { View } from 'react-native';
import { AuthScreenLayout } from '@/layout/authScreenLayout/AuthScreenLayout';
import { SocialLoginButton } from './components/SocialLoginButton';
import { styles } from './LoginScreen.styles';

export function LoginScreen() {
  return (
    <AuthScreenLayout title={'로그인하고\n시작하기'}>
      <View style={styles.buttonGroup}>
      <SocialLoginButton provider="kakao" onPress={() => {}} />
      <SocialLoginButton provider="google" onPress={() => {}} />
      <SocialLoginButton provider="naver" onPress={() => {}} />
    </View>
    </AuthScreenLayout>
  );
}