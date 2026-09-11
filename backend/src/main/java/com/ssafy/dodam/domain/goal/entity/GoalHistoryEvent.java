package com.ssafy.dodam.domain.goal.entity;

/**
 * `goal_history.event` 값. 어떤 변화로 스냅샷이 남았는지.
 *
 * <ul>
 *   <li>GOAL_CREATED — 목표 생성(확보 금액 0)</li>
 *   <li>GOAL_UPDATED — 이름·목표 금액·목표일·월 예산 수정</li>
 *   <li>STATUS_CHANGED — 상태 전이(수정과 함께 오면 이 값)</li>
 *   <li>ACCOUNTS_UPDATED — 연결 계좌 집합 변경(509)</li>
 *   <li>AMOUNT_UPDATED — 연결 계좌 잔액 변화로 확보 금액이 달라짐(510 계산 API 호출 시)</li>
 *   <li>GOAL_DELETED — soft delete</li>
 * </ul>
 */
public enum GoalHistoryEvent {
    GOAL_CREATED,
    GOAL_UPDATED,
    STATUS_CHANGED,
    ACCOUNTS_UPDATED,
    AMOUNT_UPDATED,
    GOAL_DELETED
}
