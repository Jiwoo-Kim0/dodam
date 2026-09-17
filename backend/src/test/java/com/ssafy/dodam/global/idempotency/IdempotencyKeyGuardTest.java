package com.ssafy.dodam.global.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class IdempotencyKeyGuardTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ObjectMapper json = new ObjectMapper();
    private final IdempotencyKeyGuard guard = new IdempotencyKeyGuard(redis, json, Duration.ofHours(24));
    private final AtomicInteger executions = new AtomicInteger();

    record Body(long amount, String memo) {
    }

    record Result(long balance) {
    }

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void withoutHeaderRunsWithoutTouchingTheStore() {
        Result result = guard.execute(1L, "op", null, List.of("a"), Result.class, this::run);

        assertThat(result.balance()).isEqualTo(1);
        verify(redis, never()).opsForValue();
    }

    @Test
    void firstRequestReservesRunsAndStoresTheResponse() {
        when(values.setIfAbsent(eq("idem:op:1:key-1"), eq("IN_PROGRESS"), any(Duration.class))).thenReturn(true);

        Result result = guard.execute(1L, "op", "key-1", List.of("1234567890", new Body(100, "x")), Result.class, this::run);

        assertThat(result.balance()).isEqualTo(1);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(eq("idem:op:1:key-1"), stored.capture(), any(Duration.class));
        // 원문 계좌번호는 저장값에 남지 않고 지문 해시만 남는다
        assertThat(stored.getValue()).doesNotContain("1234567890").contains("\"fingerprint\"").contains("balance");
    }

    @Test
    void sameKeyAndBodyReplaysWithoutRunningAgain() throws Exception {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false);
        List<Object> fingerprint = List.of("1234567890", new Body(100, "x"));
        guard.execute(1L, "op", "k", fingerprint, Result.class, this::run);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(anyString(), stored.capture(), any(Duration.class));
        when(values.get("idem:op:1:k")).thenReturn(stored.getValue());

        Result replay = guard.execute(1L, "op", "k", List.of("1234567890", new Body(100, "x")), Result.class, this::run);

        assertThat(replay.balance()).isEqualTo(1);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedAndInProgressIsConflict() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true, false, false);
        guard.execute(1L, "op", "k", new Body(100, "x"), Result.class, this::run);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(values).set(anyString(), stored.capture(), any(Duration.class));
        when(values.get("idem:op:1:k")).thenReturn(stored.getValue(), "IN_PROGRESS");

        assertThatThrownBy(() -> guard.execute(1L, "op", "k", new Body(200, "x"), Result.class, this::run))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThatThrownBy(() -> guard.execute(1L, "op", "k", new Body(100, "x"), Result.class, this::run))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS);
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void failedActionReleasesTheReservationSoTheKeyCanBeRetried() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThatThrownBy(() -> guard.execute(1L, "op", "k", new Body(1, "x"), Result.class, () -> {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ACCOUNT_BALANCE);
        })).isInstanceOf(BusinessException.class);

        verify(redis).delete("idem:op:1:k");
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void storeOutageFailsOpenAndBadKeyIsRejected() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenThrow(new IllegalStateException("down"));

        assertThat(guard.execute(1L, "op", "k", new Body(1, "x"), Result.class, this::run).balance()).isEqualTo(1);
        for (String bad : List.of("", " ", "a b", "한글", "x".repeat(129))) {
            assertThatThrownBy(() -> guard.execute(1L, "op", bad, new Body(1, "x"), Result.class, this::run))
                    .isInstanceOf(InvalidRequestException.class);
        }
        assertThat(executions.get()).isEqualTo(1);
    }

    @Test
    void keysAreScopedByUserAndOperation() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        guard.execute(1L, "repay", "k", new Body(1, "x"), Result.class, this::run);
        guard.execute(2L, "repay", "k", new Body(1, "x"), Result.class, this::run);
        guard.execute(1L, "interest", "k", new Body(1, "x"), Result.class, this::run);

        verify(values).setIfAbsent(eq("idem:repay:1:k"), anyString(), any(Duration.class));
        verify(values).setIfAbsent(eq("idem:repay:2:k"), anyString(), any(Duration.class));
        verify(values).setIfAbsent(eq("idem:interest:1:k"), anyString(), any(Duration.class));
        assertThat(executions.get()).isEqualTo(3);
    }

    private Result run() {
        return new Result(executions.incrementAndGet());
    }
}
