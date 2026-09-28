package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 640 A-3: 세션은 최근 N턴만 남기되 턴 수는 계속 센다. 식별자는 서버 형식만 받는다. */
class CoachChatSessionTest {

    @Test
    void keepsOnlyTheLastTurnsButCountsAllOfThem() {
        CoachChatSession session = CoachChatSession.start(CoachChatSession.newId());
        for (int i = 1; i <= 4; i++) {
            session = session.append(new CoachChatSession.Turn("질문 " + i, List.of("단축: " + i + "일")), 2);
        }
        assertThat(session.turnCount()).isEqualTo(4);
        assertThat(session.turns()).extracting(CoachChatSession.Turn::userMessage).containsExactly("질문 3", "질문 4");
        assertThat(session.exhausted(4)).isTrue();
        assertThat(session.exhausted(5)).isFalse();
    }

    @Test
    void identifiersFollowTheServerFormat() {
        String id = CoachChatSession.newId();
        assertThat(id).matches("c_[0-9a-f]{16}");
        assertThat(CoachChatSession.isValidId(id)).isTrue();
        assertThat(CoachChatSession.isValidId("c_XYZ")).isFalse();
        assertThat(CoachChatSession.isValidId(null)).isFalse();
        assertThatThrownBy(() -> CoachChatSession.start("../other")).isInstanceOf(IllegalArgumentException.class);
    }
}
