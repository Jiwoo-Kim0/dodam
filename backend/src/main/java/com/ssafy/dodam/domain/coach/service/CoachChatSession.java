package com.ssafy.dodam.domain.coach.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 한 대화의 상태(640 A-3). <b>사용자 문장과 서버 facts만</b> 남긴다 — 모델 응답 전문·프롬프트는 저장하지 않는다
 * (ai-coach.md §6). 모델에 재생할 때는 assistant 자리에 facts 요약을 넣는다: 앞 턴의 숫자를 모델이 다시 말해도
 * 서버가 준 값이므로 숫자 검증도 이 facts를 허용 목록에 더한다.
 *
 * @param conversationId 서버가 만든 식별자({@code c_} + 16 hex). 요청으로 들어오는 값은 형식을 검사한다
 * @param turnCount      지금까지 처리한 턴 수. {@code turns}는 최근 N개로 잘리지만 이 값은 계속 오른다(턴 상한 판정용)
 * @param turns          최근 턴(오래된 것부터)
 */
public record CoachChatSession(String conversationId, int turnCount, List<Turn> turns) {

    public static final Pattern ID_PATTERN = Pattern.compile("c_[0-9a-f]{16}");
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 턴 하나에 남기는 facts 줄 수. 모델 재생용 요약이므로 결과 카드 전체를 옮기지 않는다. */
    static final int FACT_LINES_PER_TURN = 8;

    public CoachChatSession {
        if (conversationId == null || !ID_PATTERN.matcher(conversationId).matches()) {
            throw new IllegalArgumentException("대화 식별자 형식이 아닙니다.");
        }
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    /** @param userMessage 사용자 문장(칩이면 칩 표시 문구) @param facts "라벨: 값" 줄. 도구가 없던 턴이면 비어 있다 */
    public record Turn(String userMessage, List<String> facts) {
        public Turn {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }

    public static String newId() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return "c_" + HexFormat.of().formatHex(bytes);
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public static CoachChatSession start(String conversationId) {
        return new CoachChatSession(conversationId, 0, List.of());
    }

    public boolean exhausted(int maxTurns) {
        return turnCount >= maxTurns;
    }

    /** 턴을 덧붙이고 최근 {@code historyLimit}개만 남긴다. */
    public CoachChatSession append(Turn turn, int historyLimit) {
        List<Turn> next = new ArrayList<>(turns);
        next.add(turn);
        while (next.size() > Math.max(0, historyLimit)) {
            next.remove(0);
        }
        return new CoachChatSession(conversationId, turnCount + 1, next);
    }
}
