import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { SymbolView } from 'expo-symbols';
import { Fragment, useCallback, useRef } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';

import { AppHeader } from '@/components/appHeader/AppHeader';
import { routes } from '@/constants/routes';
import { SafeAreaLayout } from '@/layout/safeAreaLayout/SafeAreaLayout';
import { colors } from '@/styles';
import { COACH_CHAT_CHIPS, type CoachChatContext } from '@/types/coachApi.types';

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
  const { turns, isSending, error, sendChip, retry, startNewConversation } =
    useCoachChat(context);

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
  const canAsk = !isSending && !hitTurnLimit;
  const canReset = turns.length > 0 && !isSending;

  return (
    <SafeAreaLayout edges={['top', 'left', 'right']} style={styles.screen}>
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
        title="도리에게 묻기"
      />

      {/*
        공통 BouncyScrollView는 transform으로 스크롤을 흉내 내서, 아래 정렬(flex-end)과
        내용이 늘어나는 대화 화면에서는 위치가 어긋난다. 여기서는 기본 ScrollView를 쓴다.
      */}
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        onContentSizeChange={() => scrollRef.current?.scrollToEnd({ animated: true })}
        ref={scrollRef}>
        <View style={styles.coachRow}>
          <Image
            contentFit="contain"
            source={require('@/assets/characters/dori/dori_questionmark.png')}
            style={styles.avatar}
          />
          <View style={styles.coachBubble}>
            <Text style={styles.coachText}>
              바꾸면 어떻게 될지 궁금한 걸 물어보세요.{'\n'}
              달성일과 절감액은 서버가 계산해 드려요.
            </Text>
          </View>
        </View>

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
                  source={require('@/assets/characters/dori/dori_memo.png')}
                  style={styles.avatar}
                />
                <View style={styles.coachContent}>
                  <View style={styles.coachBubble}>
                    <Text style={styles.coachText}>{turn.response.message}</Text>
                  </View>
                  <CoachChatResultCard response={turn.response} />
                </View>
              </View>
            )}
          </Fragment>
        ))}

        {isSending ? (
          <View style={styles.coachRow}>
            <Image
              contentFit="contain"
              source={require('@/assets/characters/dori/dori_memo.png')}
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

        {/* 상한에 걸리면 더 물을 수 없으므로 다음 질문 후보를 감춘다. */}

        {/*
          다음에 물어볼 것을 내 쪽(오른쪽)에 둔다. 누르면 그대로 내 말풍선이 되므로,
          하단 고정 바보다 "내가 고른 질문"이라는 게 잘 드러난다.
        */}
        <View style={styles.chipList}>
          {(hitTurnLimit ? [] : COACH_CHAT_CHIPS).map((chip) => (
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

        {/* 서버가 준 고지 문구다. 답변이 있을 때만 맨 아래에 한 번 둔다. */}
        {lastCoachTurn ? (
          <Text style={styles.disclaimer}>{lastCoachTurn.response.disclaimer}</Text>
        ) : null}
      </ScrollView>
    </SafeAreaLayout>
  );
}
