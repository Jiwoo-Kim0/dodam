import { useCallback, useEffect, useRef, useState } from 'react';

import { coachApi, isApiError } from '@/api';
import { getAuthSessionVersion } from '@/services/authSessionService';
import type {
  CoachChatChipId,
  CoachChatContext,
  CoachChatRequest,
  CoachChatResponse,
} from '@/types/coachApi.types';

/** 화면에 쌓이는 대화 한 줄. 토리 쪽은 응답 전체를 들고 있어야 결과 카드를 그릴 수 있다. */
export type CoachChatTurn =
  | { id: string; role: 'user'; text: string }
  | { id: string; role: 'coach'; response: CoachChatResponse };

/**
 * 재시도를 위해 마지막으로 보낸 것을 그대로 들고 있는다.
 * `opening`은 발견에서 들어왔을 때 사용자가 치기 전에 나가는 첫 조언 요청이라 말풍선이 없다.
 */
type PendingRequest =
  | { kind: 'opening' }
  | { kind: 'message'; text: string }
  | { kind: 'chip'; chipId: CoachChatChipId; text: string };

/**
 * 홈에서 연 대화만 화면을 벗어나도 남긴다.
 *
 * <p>서버 세션이 30분 유지되므로 화면 상태도 그만큼은 남아 있는 편이 자연스럽다.
 * 앱을 껐다 켜면 사라지는데, 그때는 서버 세션도 이미 만료됐거나 곧 만료된다.
 *
 * <p>**로그인 세션에 묶는다.** 로그아웃·세션 만료·재로그인 때마다 `authSessionVersion`이 올라가므로,
 * 값이 달라져 있으면 이전 사용자의 대화로 보고 버린다. 이렇게 하지 않으면 A로 대화한 뒤
 * 로그아웃하고 B로 들어왔을 때 B 화면에 A의 금융 대화가 그대로 남는다.
 *
 * <p>**발견에서 연 대화는 여기 담지 않는다.** 계약(ai-coach.md §13-5-1)이 발견에서 열 때는
 * 이전 대화 캐시를 쓰지 않도록 정해 두었다. 담아 두면 항목 A의 대화가 항목 B 화면이나
 * 홈 화면에 그대로 남아 맥락이 섞인다.
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
 * 토리에게 묻기 한 화면의 대화 상태.
 *
 * <p>`context`가 있으면 발견 상세에서 들어온 것이다. 빈 대화로 시작해 첫 조언을 한 번 받고,
 * 그 뒤 모든 요청에 같은 `context`를 붙인다(계약 §13-5-1).
 *
 * <p>`conversationId`는 서버가 만들고 30분·10턴 제한이 있다. 그대로 돌려보내면 대화가 이어지고,
 * 만료된 id를 보내도 서버가 같은 id로 새 대화를 시작하므로 FE가 먼저 지울 필요는 없다.
 */
export function useCoachChat(context?: CoachChatContext) {
  const hasContext = context !== undefined;
  // 라우트가 매 렌더 새 객체를 만들어 참조로는 비교할 수 없다. 값으로 같은 항목인지 본다.
  const contextKey = context ? `${context.diagnosisId}:${context.issueId}` : null;

  // 화면에 들어올 때마다 캐시가 지금 로그인한 사람의 것인지 확인한다.
  const [turns, setTurnsState] = useState<readonly CoachChatTurn[]>(() => {
    syncCacheWithSession();
    return hasContext ? [] : cachedTurns;
  });
  const [conversationId, setConversationIdState] = useState<string | null>(() =>
    hasContext ? null : cachedConversationId,
  );
  const [isSending, setIsSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /*
   * 응답이 8~15초 걸려 그 사이 화면이 여러 번 그려진다. 비동기 안에서 읽는 값은 전부 ref에
   * 둬서, 늦게 도착한 응답이 낡은 값 위에 쌓이지 않게 한다.
   */
  const turnsRef = useRef<readonly CoachChatTurn[]>(turns);
  const conversationIdRef = useRef<string | null>(conversationId);
  const contextRef = useRef<CoachChatContext | undefined>(context);
  /*
   * 렌더 중에 ref를 건드리면 안 되므로 이펙트에서 맞춘다. 아래 첫 조언 이펙트보다 먼저
   * 선언해야 그쪽이 읽을 때 이미 최신이다.
   */
  useEffect(() => {
    contextRef.current = context;
  });

  // 화면 상태와 모듈 캐시가 어긋나지 않도록 항상 같이 바꾼다.
  const setTurns = useCallback(
    (update: (current: readonly CoachChatTurn[]) => readonly CoachChatTurn[]) => {
      // 화면이 떠 있는 동안 로그아웃될 수 있다. 남길 때도 세션을 확인한다.
      syncCacheWithSession();
      const next = update(turnsRef.current);
      turnsRef.current = next;
      if (!hasContext) cachedTurns = next;
      setTurnsState(next);
    },
    [hasContext],
  );
  const setConversationId = useCallback(
    (next: string | null) => {
      syncCacheWithSession();
      conversationIdRef.current = next;
      if (!hasContext) cachedConversationId = next;
      setConversationIdState(next);
    },
    [hasContext],
  );

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
      if (request.kind !== 'opening') {
        setTurns((current) => [
          ...current,
          { id: turnId('user'), role: 'user', text: request.text },
        ]);
      }

      const controller = new AbortController();
      abortRef.current = controller;
      /*
       * 응답이 8~15초 걸리는 동안 로그아웃될 수 있다. 그때 늦게 도착한 답을 그대로 저장하면
       * 다음 사용자의 캐시에 이전 계정의 금융 대화가 들어간다. 보낸 세션에서만 결과를 받는다.
       */
      const sentSessionVersion = getAuthSessionVersion();
      const isSameSession = () => sentSessionVersion === getAuthSessionVersion();
      const sentConversationId = conversationIdRef.current;

      try {
        const screenContext = contextRef.current;
        const body: CoachChatRequest = {
          // 첫 조언은 문장도 칩도 없이 맥락만 보낸다(계약 §13-5).
          ...(request.kind === 'opening'
            ? {}
            : request.kind === 'chip'
              ? { chipId: request.chipId }
              : { message: request.text }),
          ...(screenContext ? { context: screenContext } : {}),
          ...(sentConversationId ? { conversationId: sentConversationId } : {}),
        };
        const response = await coachApi.chat(body, controller.signal);
        if (!isSameSession()) return;

        /*
         * 서버는 요청 맥락이 세션과 다르면 새 대화를 시작하고 다른 id를 준다(계약 §13-5-1).
         * 그때 앞선 대화를 남겨 두면 다른 항목의 말풍선이 그대로 보인다. 방금 한 질문과
         * 그 답만 남기고 걷어낸다.
         */
        const startedNewConversation =
          sentConversationId !== null && response.conversationId !== sentConversationId;

        setConversationId(response.conversationId);
        setTurns((current) => {
          const coachTurn: CoachChatTurn = {
            id: turnId('coach'),
            role: 'coach',
            response,
          };
          if (!startedNewConversation) return [...current, coachTurn];

          const askedTurn = current[current.length - 1];
          return askedTurn ? [askedTurn, coachTurn] : [coachTurn];
        });
        pendingRef.current = null;
      } catch (sendError) {
        if (!isSameSession()) return;

        // 실패한 질문은 말풍선을 걷어낸다. 재시도하면 같은 질문이 다시 올라온다.
        if (request.kind !== 'opening') {
          setTurns((current) => current.slice(0, -1));
        }
        setError(errorMessage(sendError));
      } finally {
        inFlightRef.current = false;
        abortRef.current = null;
        setIsSending(false);
        // 세션이 바뀐 뒤라면 재시도 대상도 남기지 않는다. 이전 계정 질문이 다시 올라가면 안 된다.
        if (!isSameSession()) pendingRef.current = null;
      }
    },
    [setConversationId, setTurns],
  );

  /*
   * 발견에서 들어오면 사용자가 치기 전에 그 항목에 대한 첫 문장을 받는다.
   * 항목당 한 번만 보낸다 — 라우트 파라미터가 매 렌더 새 객체라 `context`로는 막을 수 없고,
   * 같은 화면에서 다른 항목으로 바뀌는 경우도 없지만 키로 비교해 두면 중복 진입이 생기지 않는다.
   */
  const openedContextKeyRef = useRef<string | null>(null);
  useEffect(() => {
    if (contextKey === null) return;
    if (openedContextKeyRef.current === contextKey) return;

    openedContextKeyRef.current = contextKey;
    void send({ kind: 'opening' });
  }, [contextKey, send]);

  return {
    turns,
    conversationId,
    isSending,
    error,
    /** 시연 칩. 서버가 LLM 없이 도구를 바로 실행한다. */
    sendChip: useCallback(
      (chipId: CoachChatChipId, label: string) => send({ kind: 'chip', chipId, text: label }),
      [send],
    ),
    /** 맞춤 질문(`suggestedQuestions`)도 이 경로로 보낸다. 칩 id가 아니라 문장이다. */
    sendMessage: useCallback(
      (message: string) => send({ kind: 'message', text: message }),
      [send],
    ),
    retry: useCallback(() => {
      const pending = pendingRef.current;
      if (pending) void send(pending);
    }, [send]),
    /** 세션 턴 상한에 걸렸을 때 처음부터 다시 시작한다. */
    startNewConversation: useCallback(() => {
      abortRef.current?.abort();
      inFlightRef.current = false;
      pendingRef.current = null;
      setConversationId(null);
      setTurns(() => []);
      setError(null);
      // 발견에서 온 화면은 첫 조언이 맨 위에 있어야 맥락이 유지된다. 비운 뒤 다시 받는다.
      if (contextRef.current) void send({ kind: 'opening' });
    }, [send, setConversationId, setTurns]),
    cancel: useCallback(() => {
      abortRef.current?.abort();
    }, []),
  };
}
