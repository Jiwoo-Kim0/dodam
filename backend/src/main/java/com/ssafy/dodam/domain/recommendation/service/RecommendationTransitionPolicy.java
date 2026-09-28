package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 개선안 상태 전이 허용표(549).
 *
 * <pre>
 * 현재 \ 요청  accept    reject    complete   defer
 * PENDING     ACCEPTED  REJECTED  ✗          DEFERRED
 * DEFERRED    ACCEPTED  REJECTED  ✗          ✗
 * ACCEPTED    ✗         REJECTED  COMPLETED  DEFERRED
 * REJECTED    ✗         ✗         ✗          ✗
 * COMPLETED   ✗         ✗         ✗          ✗
 * </pre>
 *
 * <p>같은 상태 재요청도 ✗다 — 이력 행이 중복 생성되지 않게 하는 "중복 요청 방지"의 해석이다. REJECTED·COMPLETED는 종료 상태로
 * 되살리지 않는다(새 진단에서 재생성). GoalStatus의 전이 규칙과 같은 방식이다.
 */
public final class RecommendationTransitionPolicy {
    private static final Map<RecommendationStatus, Set<RecommendationStatus>> ALLOWED = new EnumMap<>(RecommendationStatus.class);

    static {
        ALLOWED.put(RecommendationStatus.PENDING,
                EnumSet.of(RecommendationStatus.ACCEPTED, RecommendationStatus.REJECTED, RecommendationStatus.DEFERRED));
        ALLOWED.put(RecommendationStatus.DEFERRED, EnumSet.of(RecommendationStatus.ACCEPTED, RecommendationStatus.REJECTED));
        ALLOWED.put(RecommendationStatus.ACCEPTED,
                EnumSet.of(RecommendationStatus.REJECTED, RecommendationStatus.COMPLETED, RecommendationStatus.DEFERRED));
        ALLOWED.put(RecommendationStatus.REJECTED, EnumSet.noneOf(RecommendationStatus.class));
        ALLOWED.put(RecommendationStatus.COMPLETED, EnumSet.noneOf(RecommendationStatus.class));
    }

    private RecommendationTransitionPolicy() {
    }

    public static boolean canTransition(RecommendationStatus from, RecommendationStatus to) {
        return from != null && to != null && ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
