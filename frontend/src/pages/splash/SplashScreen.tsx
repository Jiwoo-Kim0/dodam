import { Image } from 'expo-image';
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';

import { styles } from './SplashScreen.styles';

export function SplashScreen() {
  return (
    <View style={styles.container}>
      <StatusBar style="dark" />

      <Image
        source={require('@/assets/logos/dodam_logo.gif')}
        style={styles.logo}
        contentFit="contain"
        autoplay
      />
    </View>
  );
}