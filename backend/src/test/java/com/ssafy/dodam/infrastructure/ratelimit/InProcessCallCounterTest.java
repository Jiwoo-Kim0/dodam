package com.ssafy.dodam.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Redis가 죽었을 때만 쓰이는 대체 카운터. 창이 바뀌면 다시 세는지가 핵심이다. */
class InProcessCallCounterTest {

    @Test
    void countsPerUserInsideOneWindow() {
        InProcessCallCounter counter = new InProcessCallCounter();

        assertThat(counter.tryAcquire(1L, 100L, 2)).isTrue();
        assertThat(counter.tryAcquire(1L, 100L, 2)).isTrue();
        assertThat(counter.tryAcquire(1L, 100L, 2)).isFalse();
        assertThat(counter.tryAcquire(2L, 100L, 2)).isTrue();
    }

    @Test
    void startsOverWhenTheWindowRollsOver() {
        InProcessCallCounter counter = new InProcessCallCounter();
        counter.tryAcquire(1L, 100L, 1);
        assertThat(counter.tryAcquire(1L, 100L, 1)).isFalse();

        assertThat(counter.tryAcquire(1L, 101L, 1)).isTrue();
    }
}
