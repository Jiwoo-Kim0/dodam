import { useEffect, useEffectEvent, useRef, useState } from 'react';
import { Alert } from 'react-native';

type SpeechModule = typeof import('expo-speech-recognition').ExpoSpeechRecognitionModule;

/**
 * 네이티브 모듈이 없는 빌드(이 라이브러리를 넣기 전에 만든 개발 빌드·APK)에서는 import만으로
 * `requireNativeModule`이 던져 대화 화면 전체가 죽는다. 그래서 감싸서 불러오고, 없으면 음성 입력만 숨긴다.
 */
function loadSpeechModule(): SpeechModule | null {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    return require('expo-speech-recognition').ExpoSpeechRecognitionModule as SpeechModule;
  } catch {
    return null;
  }
}

const speechModule = loadSpeechModule();

type UseVoiceInputOptions = {
  /** 인식 중간 결과도 계속 넘어온다. 매번 지금까지 들은 문장 전체다. */
  onTranscript: (transcript: string) => void;
};

export function useVoiceInput({ onTranscript }: UseVoiceInputOptions) {
  const [isListening, setIsListening] = useState(false);
  const isListeningRef = useRef(false);
  const [isAvailable] = useState(() => {
    try {
      return speechModule?.isRecognitionAvailable() ?? false;
    } catch {
      return false;
    }
  });

  const handleTranscript = useEffectEvent(onTranscript);

  useEffect(() => {
    if (!speechModule) return;

    const setListening = (value: boolean) => {
      isListeningRef.current = value;
      setIsListening(value);
    };

    const subscriptions = [
      speechModule.addListener('start', () => setListening(true)),
      speechModule.addListener('end', () => setListening(false)),
      speechModule.addListener('result', (event) => {
        handleTranscript(event.results[0]?.transcript ?? '');
      }),
      speechModule.addListener('error', (event) => {
        setListening(false);
        // 말을 안 했거나 직접 멈춘 경우는 실패로 알릴 일이 아니다.
        if (event.error === 'no-speech' || event.error === 'aborted') return;
        if (event.error === 'not-allowed') {
          Alert.alert('마이크 권한이 필요해요', '설정에서 마이크 권한을 허용하면 말로 물어볼 수 있어요.');
          return;
        }
        Alert.alert('음성을 알아듣지 못했어요', '잠시 후 다시 말해 주세요.');
      }),
    ];

    return () => {
      subscriptions.forEach((subscription) => subscription.remove());
      // 듣는 중에 화면을 벗어나면 마이크가 켜진 채로 남지 않게 끊는다.
      if (isListeningRef.current) speechModule.abort();
    };
  }, []);

  const start = async () => {
    if (!speechModule) return;

    const permission = await speechModule.requestPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('마이크 권한이 필요해요', '설정에서 마이크 권한을 허용하면 말로 물어볼 수 있어요.');
      return;
    }

    // 한 문장을 말하고 멈추면 알아서 끝난다. 듣는 동안 입력창에 바로바로 채워 준다.
    speechModule.start({ lang: 'ko-KR', interimResults: true, continuous: false });
  };

  const stop = () => {
    speechModule?.stop();
  };

  /** 결과를 더 받지 않고 바로 끊는다. 보내기 직후 늦게 온 결과가 입력창을 다시 채우는 걸 막는다. */
  const abort = () => {
    if (isListeningRef.current) speechModule?.abort();
  };

  return { abort, isAvailable, isListening, start, stop };
}
