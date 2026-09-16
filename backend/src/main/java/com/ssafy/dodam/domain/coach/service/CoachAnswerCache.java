package com.ssafy.dodam.domain.coach.service;

import java.util.Optional;

/**
 * 모델이 쓴 문장의 캐시 포트.
 *
 * <p><b>문장만 저장한다.</b> {@code facts}·{@code highlights}는 매 호출마다 DB에서 다시 만든다 — 싸고,
 * 그래야 응답이 언제나 현재 저장 데이터를 반영한다. 캐시가 낡아서 화면의 숫자가 틀리는 상황이
 * 구조적으로 불가능해진다.
 *
 * <p><b>구현은 절대 예외를 던지지 않는다.</b> 캐시는 비용 절감 장치이지 기능이 아니다. Redis가 죽었다고
 * 코치가 실패하면 캐시가 없느니만 못하다. 조회 실패는 빈 값, 저장 실패는 무시로 처리한다.
 */
public interface CoachAnswerCache {

    /** @param key {@link CoachCacheKey}가 만든 키. null이면 빈 값 */
    Optional<Entry> find(String key);

    void put(String key, Entry entry);

    /**
     * @param message 모델이 쓴 본문
     * @param model   그 문장을 쓴 모델 이름. 응답의 {@code model}을 복원하는 데 쓴다
     */
    record Entry(String message, String model) {
    }
}
