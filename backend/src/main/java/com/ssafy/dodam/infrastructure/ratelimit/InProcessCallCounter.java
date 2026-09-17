package com.ssafy.dodam.infrastructure.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis를 쓸 수 없을 때 대신 세는 프로세스 내 고정 창 카운터.
 *
 * <p>인스턴스가 하나인 현재 배포에서는 Redis 카운터와 결과가 같고, 여러 대로 늘어나면 인스턴스 수만큼
 * 한도가 느슨해진다. 그래도 <b>한도가 없는 것보다는 낫다</b> — 이 대체 경로가 동작하는 상황은 Redis가
 * 죽어 캐시도 함께 죽은, 모든 호출이 과금되는 상황이기 때문이다. 재시작하면 카운터가 초기화되는 것도
 * 같은 이유로 받아들인다.
 */
class InProcessCallCounter {

    /** 사용자 수만큼 자라는 맵이라 상한을 둔다. 넘으면 통째로 버린다(다음 창부터 다시 센다). */
    private static final int MAX_TRACKED_USERS = 10_000;

    private final Map<Long, Window> windows = new ConcurrentHashMap<>();

    /** @return 이번 호출까지 포함한 사용량이 {@code permits} 이내면 true */
    boolean tryAcquire(Long userId, long bucket, int permits) {
        if (windows.size() > MAX_TRACKED_USERS) {
            windows.clear();
        }
        Window updated = windows.compute(userId, (key, current) ->
                current == null || current.bucket() != bucket ? new Window(bucket, 1) : new Window(bucket, current.used() + 1));
        return updated.used() <= permits;
    }

    private record Window(long bucket, int used) {
    }
}
