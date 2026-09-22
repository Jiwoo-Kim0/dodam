import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Fragment, useCallback, useEffect, useRef } from 'react';
import {
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { routes } from '@/constants/routes';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { COACH_CHAT_CHIPS, type CoachChatContext } from '@/types/coachApi.types';

import { CoachChatInputBar } from './components/CoachChatInputBar';
import { CoachChatResultCard } from './components/CoachChatResultCard';
import { CoachTypingBubble } from './components/CoachTypingBubble';
import { styles } from './CoachChatScreen.styles';
import { useCoachChat } from './hooks/useCoachChat';

type CoachChatScreenProps = {
  /** 발견 상세에서 열면 그 문제를 화면 맥락으로 넘긴다. */
  context?: CoachChatContext;
};

export function CoachChatScreen({ context }: CoachChatScreenProps) {
  const router = useRouter();
  const scrollRef = useRef<ScrollView>(null);
  const { user } = useCurrentUser();
  /*
   * 말풍선이 남는 폭을 다 쓰면 한 줄이 너무 길어 읽기 어렵다. 화면 폭에 비례해 잘라 둔다.
   * 퍼센트 상한은 부모 폭이 정해지기 전에 계산돼 제한이 걸리지 않아 실제 값으로 준다.
   */
  const { width: windowWidth } = useWindowDimensions();
  const bubbleMaxWidth = Math.round(windowWidth * 0.72);
  const { turns, isSending, error, sendChip, sendMessage, retry, startNewConversation } =
    useCoachChat(context);

  const scrollToEnd = useCallback(() => {
    scrollRef.current?.scrollToEnd({ animated: true });
  }, []);

  /*
   * 키보드가 열리면 보이는 영역만 줄고 내용 길이는 그대로라 `onContentSizeChange`가 안 돈다.
   * 마지막 답변이 키보드 뒤로 숨는 걸 막으려고 열릴 때 한 번 끝으로 내린다.
   */
  useEffect(() => {
    const subscription = Keyboard.addListener('keyboardDidShow', scrollToEnd);

    return () => subscription.remove();
  }, [scrollToEnd]);

  const handleBackPress = useCallback(() => {
    if (router.canGoBack()) {
      router.back();
      return;
    }

    router.replace(routes.home);
  }, [router]);

  const lastCoachTurn = turns.findLast((turn) => turn.role === 'coach');
  // 세션 턴 상한에 걸리면 같은 대화로는 더 못 묻는다. 새 대화로만 이어 갈 수 있다.
  const hitTurnLimit = lastCoachTurn?.response.degradedReason === 'SESSION_TURN_LIMIT';
  /* 턴 상한에 걸리면 눌러도 보낼 수 없어, 질문 대신 새 대화 안내만 남긴다. */
  const suggestedQuestions = hitTurnLimit
    ? []
    : (lastCoachTurn?.response.suggestedQuestions ?? []);
  const canAsk = !isSending && !hitTurnLimit;
  const canReset = turns.length > 0 && !isSending;

  return (
    <SafeAreaLayout edges={['top', 'bottom', 'left', 'right']} style={styles.screen}>
      <AppHeader
        onBackPress={handleBackPress}
        right={
          /* 대화가 없으면 지울 것도 없다. 응답을 기다리는 중에도 막는다. */
          <Pressable
            accessibilityHint="지금까지의 대화를 지우고 처음부터 시작해요"
            accessibilityLabel="새 대화 시작"
            accessibilityRole="button"
            accessibilityState={{ disabled: !canReset }}
            disabled={!canReset}
            hitSlop={6}
            onPress={startNewConversation}
            style={({ pressed }) => [styles.headerAction, pressed && styles.pressed]}>
            <SymbolView
              name={{ android: 'refresh', ios: 'arrow.clockwise', web: 'refresh' }}
              size={20}
              tintColor={canReset ? colors.textPrimary : colors.textTertiary}
            />
          </Pressable>
        }
        showBackButton
        title="토리에게 묻기"
      />

      {/*
        입력창이 키보드에 가리지 않게 대화 영역과 입력 바를 함께 밀어 올린다.
        안드로이드는 edge-to-edge가 켜져 있어 네이티브 `adjustResize`만으로는 창이 줄지 않는다.
        `height`로 직접 높이를 줄여야 하며, 이 값은 앱의 다른 입력 화면과 같다.
      */}
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.chatArea}>
        {/*
          공통 BouncyScrollView는 transform으로 스크롤을 흉내 내서, 내용이 계속 늘어나는
          대화 화면에서는 끝으로 내리는 위치가 어긋난다. 여기서는 기본 ScrollView를 쓴다.
        */}
        <ScrollView
          contentContainerStyle={styles.scrollContent}
          keyboardDismissMode="on-drag"
          keyboardShouldPersistTaps="handled"
          onContentSizeChange={scrollToEnd}
          ref={scrollRef}>
          {/*
            발견에서 들어오면 첫 조언이 그 자리를 대신한다. 일반 인사까지 앞에 두면
            정작 물어보러 온 항목 이야기가 두 번째 말풍선으로 밀린다.
          */}
          {context ? null : (
            <View style={styles.coachRow}>
              <Image
                contentFit="contain"
                source={require('@/assets/characters/tori/tori_chat.png')}
                style={styles.avatar}
              />
              {/* 말풍선과 결과 카드가 같은 폭 안에 들어가도록 래퍼에서 한 번에 제한한다. */}
              <View style={[styles.coachContent, { maxWidth: bubbleMaxWidth }]}>
                <View style={styles.coachBubble}>
                  {/* 이름은 아직 못 받아왔을 수 있다. 그때는 호칭 없이 인사한다. */}
                  <Text style={styles.coachText}>
                    {user?.name ? `안녕하세요, ${user.name}님! ` : '안녕하세요! '}
                    저는 토리예요.{'\n'}
                    바꾸면 뭐가 달라질지 궁금하시죠? 정확하게 계산해서 알려드릴게요.{'\n'}
                    궁금한 건 언제든지 질문 주세요!
                  </Text>
                </View>
              </View>
            </View>
          )}

          {/*
            첫 인사말에 따라붙는 예시 질문이다. 무엇을 물을 수 있는지 보여 주는 역할이라
            대화가 시작되면 감춘다. 그 뒤로는 서버가 준 맞춤 질문을 그린다.
            발견에서 들어온 화면은 첫 조언과 함께 맞춤 질문이 오므로 정적 칩을 쓰지 않는다.
          */}
          {turns.length === 0 && !context ? (
            <View style={styles.chipList}>
              {COACH_CHAT_CHIPS.map((chip) => (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ disabled: !canAsk }}
                  disabled={!canAsk}
                  key={chip.id}
                  onPress={() => {
                    void sendChip(chip.id, chip.label);
                  }}
                  style={({ pressed }) => [
                    styles.chip,
                    !canAsk && styles.chipDisabled,
                    pressed && canAsk && styles.pressed,
                  ]}>
                  <Text style={[styles.chipText, !canAsk && styles.chipTextDisabled]}>
                    {chip.label}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          {turns.map((turn) => (
            <Fragment key={turn.id}>
              {turn.role === 'user' ? (
                <View style={styles.userBubble}>
                  <Text style={styles.userText}>{turn.text}</Text>
                </View>
              ) : (
                <View style={styles.coachRow}>
                  <Image
                    contentFit="contain"
                    source={require('@/assets/characters/tori/tori_chat.png')}
                    style={styles.avatar}
                  />
                  <View style={[styles.coachContent, { maxWidth: bubbleMaxWidth }]}>
                    <View style={styles.coachBubble}>
                      <Text style={styles.coachText}>{turn.response.message}</Text>
                    </View>
                    <CoachChatResultCard response={turn.response} />
                  </View>
                </View>
              )}
            </Fragment>
          ))}

          {/*
            서버가 맥락에 맞춰 골라 준 질문이다(계약 §13-5-2). 칩 id가 아니라 문장이라
            자유 입력과 같은 경로로 보낸다. 답변을 기다리는 동안에는 감춘다.
          */}
          {!isSending && suggestedQuestions.length > 0 ? (
            <View style={styles.chipList}>
              {suggestedQuestions.map((question) => (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ disabled: !canAsk }}
                  disabled={!canAsk}
                  key={question}
                  onPress={() => {
                    void sendMessage(question);
                  }}
                  style={({ pressed }) => [
                    styles.chip,
                    !canAsk && styles.chipDisabled,
                    pressed && canAsk && styles.pressed,
                  ]}>
                  <Text style={[styles.chipText, !canAsk && styles.chipTextDisabled]}>
                    {question}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : null}

          {isSending ? (
            <View style={styles.coachRow}>
              <Image
                contentFit="contain"
                source={require('@/assets/characters/tori/tori_chat.png')}
                style={styles.avatar}
              />
              <CoachTypingBubble />
            </View>
          ) : null}

          {error ? (
            <View style={styles.noticeBlock}>
              <Text style={styles.noticeText}>{error}</Text>
              <Pressable
                accessibilityRole="button"
                onPress={retry}
                style={({ pressed }) => [styles.noticeButton, pressed && styles.pressed]}>
                <Text style={styles.noticeButtonText}>다시 시도</Text>
              </Pressable>
            </View>
          ) : null}

          {hitTurnLimit ? (
            <View style={styles.noticeBlock}>
              <Text style={styles.noticeText}>
                이번 대화에서 물어볼 수 있는 횟수를 다 썼어요.
              </Text>
              <Pressable
                accessibilityRole="button"
                onPress={startNewConversation}
                style={({ pressed }) => [styles.noticeButton, pressed && styles.pressed]}>
                <Text style={styles.noticeButtonText}>새 대화 시작</Text>
              </Pressable>
            </View>
          ) : null}
        </ScrollView>

        {/*
          서버가 준 고지 문구다. 대화와 같이 흘러가면 위로 사라져서, 입력창 바로 위에 붙여 둔다.
          답변이 하나도 없을 때는 아직 고지할 계산 결과가 없어 띄우지 않는다.
        */}
        {lastCoachTurn ? (
          <Text style={styles.disclaimer}>{lastCoachTurn.response.disclaimer}</Text>
        ) : null}

        {/* 못 묻는 이유를 입력창이 직접 말해 준다. 답변 대기와 턴 상한은 안내가 달라야 한다. */}
        <CoachChatInputBar
          disabled={!canAsk}
          onSend={sendMessage}
          placeholder={
            hitTurnLimit
              ? '새 대화를 시작하면 다시 물어볼 수 있어요'
              : isSending
                ? '답변을 기다리고 있어요'
                : '궁금한 걸 물어보세요'
          }
        />
      </KeyboardAvoidingView>
    </SafeAreaLayout>
  );
}
