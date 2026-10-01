import { SymbolView } from 'expo-symbols';
import { useRef, useState } from 'react';
import { Pressable, Text, TextInput, View } from 'react-native';

import { colors } from '@/styles';

import { useVoiceInput } from '../hooks/useVoiceInput';
import { styles } from './CoachChatInputBar.styles';

/** 서버 상한과 같다(`app.coach.chat.max-message-length`). 넘겨 보내면 400이라 여기서 먼저 막는다. */
const MAX_MESSAGE_LENGTH = 300;
/** 남은 글자 수를 미리 알려 주기 시작하는 지점. */
const COUNTER_VISIBLE_FROM = 240;

type CoachChatInputBarProps = {
  disabled: boolean;
  onSend: (message: string) => void;
  /** 못 묻는 상태마다 안내가 달라서 문구는 화면이 정한다. */
  placeholder: string;
};

export function CoachChatInputBar({ disabled, onSend, placeholder }: CoachChatInputBarProps) {
  const [text, setText] = useState('');
  const trimmed = text.trim();
  const isTooLong = text.length > MAX_MESSAGE_LENGTH;
  const canSend = trimmed.length > 0 && !isTooLong && !disabled;

  /** 말하기 시작할 때 이미 쳐 둔 글. 들은 문장은 그 뒤에 이어 붙인다. */
  const textBeforeVoiceRef = useRef('');
  const voice = useVoiceInput({
    onTranscript: (transcript) => {
      const base = textBeforeVoiceRef.current;
      setText(base && transcript ? `${base} ${transcript}` : base || transcript);
    },
  });

  const handleSend = () => {
    if (!canSend) return;

    voice.abort();
    onSend(trimmed);
    setText('');
  };

  const handleVoicePress = () => {
    if (voice.isListening) {
      voice.stop();
      return;
    }

    textBeforeVoiceRef.current = text.trimEnd();
    void voice.start();
  };

  return (
    <View style={styles.bar}>
      {text.length >= COUNTER_VISIBLE_FROM ? (
        <Text style={[styles.counter, isTooLong && styles.counterOver]}>
          {`${text.length} / ${MAX_MESSAGE_LENGTH}`}
        </Text>
      ) : null}

      <View style={styles.inputRow}>
        <TextInput
          accessibilityLabel="토리에게 물어볼 내용"
          editable={!disabled}
          multiline
          onChangeText={setText}
          onSubmitEditing={handleSend}
          placeholder={voice.isListening ? '듣고 있어요. 말씀해 주세요' : placeholder}
          placeholderTextColor={colors.textTertiary}
          returnKeyType="send"
          style={styles.input}
          /*
           * 여러 줄 입력의 엔터는 기본이 줄바꿈이다. `submit`으로 두면 엔터가 전송이 되고
           * 키보드도 닫히지 않아 이어서 물어보기 좋다.
           */
          submitBehavior="submit"
          value={text}
        />

        {voice.isAvailable ? (
          <Pressable
            accessibilityLabel={voice.isListening ? '음성 입력 멈추기' : '음성으로 입력하기'}
            accessibilityRole="button"
            accessibilityState={{ disabled }}
            disabled={disabled}
            onPress={handleVoicePress}
            style={({ pressed }) => [
              styles.voiceButton,
              voice.isListening && styles.voiceButtonListening,
              pressed && !disabled && styles.pressed,
            ]}>
            <SymbolView
              name={
                voice.isListening
                  ? { android: 'stop', ios: 'stop.fill', web: 'stop' }
                  : { android: 'mic', ios: 'mic.fill', web: 'mic' }
              }
              size={18}
              tintColor={
                voice.isListening ? colors.white : disabled ? colors.textTertiary : colors.textSecondary
              }
            />
          </Pressable>
        ) : null}

        <Pressable
          accessibilityLabel="보내기"
          accessibilityRole="button"
          accessibilityState={{ disabled: !canSend }}
          disabled={!canSend}
          onPress={handleSend}
          style={({ pressed }) => [
            styles.sendButton,
            !canSend && styles.sendButtonDisabled,
            pressed && canSend && styles.pressed,
          ]}>
          <SymbolView
            name={{ android: 'arrow_upward', ios: 'arrow.up', web: 'arrow_upward' }}
            size={18}
            tintColor={canSend ? colors.white : colors.textTertiary}
          />
        </Pressable>
      </View>
    </View>
  );
}
