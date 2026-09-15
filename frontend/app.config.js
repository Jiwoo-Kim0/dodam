module.exports = ({ config }) => {
  const kakaoNativeAppKey =
    process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY?.trim();
  const googleWebClientId =
    process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID?.trim();
  const plugins = [...(config.plugins ?? []), 'expo-secure-store'];

  // 웹 클라이언트 ID가 있을 때만 네이티브 설정을 붙인다. 없으면 구글 버튼이 설정 안내 오류를 낸다.
  if (googleWebClientId) {
    plugins.push('@react-native-google-signin/google-signin');
  }

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
