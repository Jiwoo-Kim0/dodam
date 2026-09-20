import { useCallback, useRef, useState } from 'react';

import { coachApi, isApiError } from '@/api';
import { getAuthSessionVersion } from '@/services/authSessionService';
import type {
  CoachChatChipId,
  CoachChatContext,
  CoachChatRequest,
  CoachChatResponse,
} from '@/types/coachApi.types';

/** 화면에 쌓이는 대화 한 줄. 도리 쪽은 응답 전체를 들고 있어야 결과 카드를 그릴 수 있다. */
export type CoachChatTurn =
  | { id: string; role: 'user'; text: string }
  | { id: string; role: 'coach'; response: CoachChatResponse };

/** 재시도를 위해 마지막으로 보낸 것을 그대로 들고 있는다. */
type PendingRequest = { text: string; chipId?: CoachChatChipId };

/**
 * 화면을 벗어났다 돌아와도 대화가 이어지도록 모듈에 남긴다.
 *
 * <p>서버 세션이 30분 유지되므로 화면 상태도 그만큼은 남아 있는 편이 자연스럽다.
 * 앱을 껐다 켜면 사라지는데, 그때는 서버 세션도 이미 만료됐거나 곧 만료된다.
 *
 * <p>**로그인 세션에 묶는다.** 로그아웃·세션 만료·재로그인 때마다 `authSessionVersion`이 올라가므로,
 * 값이 달라져 있으면 이전 사용자의 대화로 보고 버린다. 이렇게 하지 않으면 A로 대화한 뒤
 * 로그아웃하고 B로 들어왔을 때 B 화면에 A의 금융 대화가 그대로 남는다.
 */
let cachedSessionVersion: number | null = null;
let cachedTurns: readonly CoachChatTurn[] = [];
let cachedConversationId: string | null = null;

/** 캐시를 현재 로그인 세션 기준으로 맞춘다. 세션이 바뀌었으면 비운다. */
function syncCacheWithSession() {
  const currentVersion = getAuthSessionVersion();
  if (cachedSessionVersion !== currentVersion) {
    cachedSessionVersion = currentVersion;
    cachedTurns = [];
    cachedConversationId = null;
  }
}

function turnId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function errorMessage(error: unknown) {
  if (isApiError(error)) {
    return error.errors[0]?.reason ?? error.message;
  }

  return '답변을 받지 못했어요. 다시 시도해 주세요.';
}

/**
 * 도리에게 묻기 한 화면의 대화 상태.
 *
 * <p>`conversationId`는 서버가 만들고 30분·10턴 제한이 있다. 그대로 돌려보내면 대화가 이어지고,
 * 만료된 id를 보내도 서버가 같은 id로 새 대화를 시작하므로 FE가 먼저 지울 필요는 없다.
 */
export function useCoachChat(context?: CoachChatContext) {
  // 화면에 들어올 때마다 캐시가 지금 로그인한 사람의 것인지 확인한다.
  const [turns, setTurnsState] = useState<readonly CoachChatTurn[]>(() => {
    syncCacheWithSession();
    return cachedTurns;
  });
  const [conversationId, setConversationIdState] = useState<string | null>(
    () => cachedConversationId,
  );
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // 화면 상태와 모듈 캐시가 어긋나지 않도록 항상 같이 바꾼다.
  const setTurns = useCallback(
    (update: (current: readonly CoachChatTurn[]) => readonly CoachChatTurn[]) => {
      // 화면이 떠 있는 동안 로그아웃될 수 있다. 남길 때도 세션을 확인한다.
      syncCacheWithSession();
      const next = update(cachedTurns);
      cachedTurns = next;
      setTurnsState(next);
    },
    [],
  );
  const setConversationId = useCallback((next: string | null) => {
    syncCacheWithSession();
    cachedConversationId = next;
    setConversationIdState(next);
  }, []);
  const pendingRef = useRef<PendingRequest | null>(null);
  // 응답이 8~15초 걸려서, 버튼 비활성만으로는 연타를 다 막지 못한다.
  const inFlightRef = useRef(false);
  const abortRef = useRef<AbortController | null>(null);

  const send = useCallback(
    async (request: PendingRequest) => {
      if (inFlightRef.current) return;

      inFlightRef.current = true;
      pendingRef.current = request;
      setError(null);
      setIsSending(true);
      // 사용자가 무엇을 물었는지 먼저 올린다. 응답을 기다리는 동안 화면이 비지 않게 한다.
      setTurns((current) => [
        ...current,
        { id: turnId('user'), role: 'user', text: request.text },
      ]);

      const controller = new AbortController();
      abortRef.current = controller;

      try {
        const body: CoachChatRequest = {
          ...(request.chipId ? { chipId: request.chipId } : { message: request.text }),
          ...(context ? { context } : {}),
          ...(conversationId ? { conversationId } : {}),
        };
        const response = await coachApi.chat(body, controller.signal);
        setConversationId(response.conversationId);
        setTurns((current) => [
          ...current,
          { id: turnId('coach'), role: 'coach', response },
        ]);
        pendingRef.current = null;
      } catch (sendError) {
        // 실패한 질문은 말풍선을 걷어낸다. 재시도하면 같은 질문이 다시 올라온다.
        setTurns((current) => current.slice(0, -1));
        setError(errorMessage(sendError));
      } finally {
        inFlightRef.current = false;
        abortRef.current = null;
        setIsSending(false);
      }
    },
    [context, conversationId, setConversationId, setTurns],
  );

  return {
    turns,
    conversationId,
    isSending,
    error,
    /** 시연 칩. 서버가 LLM 없이 도구를 바로 실행한다. */
    sendChip: useCallback(
      (chipId: CoachChatChipId, label: string) => send({ chipId, text: label }),
      [send],
    ),
    sendMessage: useCallback((message: string) => send({ text: message }), [send]),
    retry: useCallback(() => {
      const pending = pendingRef.current;
      if (pending) void send(pending);
    }, [send]),
    /** 세션 턴 상한에 걸렸을 때 처음부터 다시 시작한다. */
    startNewConversation: useCallback(() => {
      abortRef.current?.abort();
      pendingRef.current = null;
      setConversationId(null);
      setTurns(() => []);
      setError(null);
    }, [setConversationId, setTurns]),
    cancel: useCallback(() => {
      abortRef.current?.abort();
    }, []),
  };
}
