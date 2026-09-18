package com.ssafy.dodam.domain.coach.service;

/**
 * 채팅 전용 외부 호출 예산(640 A-3). {@link CoachCallRateLimiter}와 계약은 같고 키·한도만 다르다 —
 * 채팅 몇 턴이 진단 화면 코치 문장의 예산을 소진하지 않게 분리한다. 호출 1회마다 {@code tryAcquire} 1회다(턴이 아니라 호출).
 */
public interface CoachChatRateLimiter {

    boolean tryAcquire(Long userId);
}
