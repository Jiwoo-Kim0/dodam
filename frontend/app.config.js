module.exports = ({ config }) => {
  const kakaoNativeAppKey =
    process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY?.trim();
  const plugins = [...(config.plugins ?? []), 'expo-secure-store'];

  if (kakaoNativeAppKey) {
    plugins.push([
      '@react-native-kakao/core',
      {
        nativeAppKey: kakaoNativeAppKey,
        android: { authCodeHandlerActivity: true },
        ios: { handleKakaoOpenUrl: true },
      },
    ]);
  }

  return {
    ...config,
    plugins,
    android: {
      ...config.android,
      googleServicesFile:
        process.env.GOOGLE_SERVICES_JSON ?? './google-services.json',
    },
  };
};
