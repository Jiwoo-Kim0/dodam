package com.ssafy.dodam.domain.coach.service;

import java.util.Optional;

/**
 * 대화 세션 저장소(640 A-3). 키는 반드시 사용자 ID를 포함한다 — {@code conversationId}만으로 찾으면 남의 대화 식별자를
 * 알아낸 사용자가 그 대화의 facts를 이어받는다. 저장소 장애는 "새 대화"와 같게 다룬다(캐시와 같은 태도).
 */
public interface CoachChatSessionStore {

    Optional<CoachChatSession> find(Long userId, String conversationId);

    void save(Long userId, CoachChatSession session);
}
