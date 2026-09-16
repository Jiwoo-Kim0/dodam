import type { ConfigContext } from 'expo/config';

const KAKAO_NATIVE_APP_KEY =
  process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY?.trim();

export default ({ config }: ConfigContext) => {
  if (!KAKAO_NATIVE_APP_KEY) {
    throw new Error(
      'EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY is required to configure Kakao login.',
    );
  }

  return {
    ...config,
    plugins: [
      ...(config.plugins ?? []),
      [
        '@react-native-kakao/core',
        {
          nativeAppKey: KAKAO_NATIVE_APP_KEY,
          android: {
            authCodeHandlerActivity: true,
          },
        },
      ],
    ],
  };
};
