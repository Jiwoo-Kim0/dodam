package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.dto.FinanceSyncLatestResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncRequest;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncStatusResponse;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.entity.FinanceSync;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncStatus;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceSyncRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * API 명세 4. Finance Data — 금융 데이터 동기화 (S15P21D105-499).
 *
 * 실행 흐름 (요청 스레드에서 동기 실행)
 * 1. 연결 행을 잠근 채 진행 중인 동기화가 없는지 확인하고 RUNNING 행을 만든다 (트랜잭션 1)
 * 2. 트랜잭션 밖에서 scope별 {@link FinanceSyncScopeHandler}를 순서대로 실행한다
 * 3. 결과를 모아 COMPLETED/FAILED로 마감하고, 실제로 반영된 도메인이 하나라도 있으면 last_synced_at을 갱신한다 (트랜잭션 2)
 *
 * 프로세스가 2단계에서 죽으면 RUNNING 행이 남는다. {@link #STALE_AFTER}가 지난 RUNNING 행은 다음 요청이 FAILED로 정리한다.
 */
@Service
public class FinanceSyncService {

    public static final Duration STALE_AFTER = Duration.ofMinutes(10);
    private static final String STALE_MESSAGE = "동기화가 비정상 종료되어 만료 처리되었습니다.";
    private static final EnumSet<FinanceSyncStatus> IN_PROGRESS =
            EnumSet.of(FinanceSyncStatus.PENDING, FinanceSyncStatus.RUNNING);

    private static final Logger log = LoggerFactory.getLogger(FinanceSyncService.class);

    private final UserRepository userRepository;
    private final FinanceConnectionRepository financeConnectionRepository;
    private final FinanceSyncRepository financeSyncRepository;
    private final FinanceConnectionService financeConnectionService;
    private final Map<FinanceSyncScope, FinanceSyncScopeHandler> handlers;
    private final TransactionTemplate transactionTemplate;

    public FinanceSyncService(
            UserRepository userRepository,
            FinanceConnectionRepository financeConnectionRepository,
            FinanceSyncRepository financeSyncRepository,
            FinanceConnectionService financeConnectionService,
            List<FinanceSyncScopeHandler> handlers,
            TransactionTemplate transactionTemplate
    ) {
        this.userRepository = userRepository;
        this.financeConnectionRepository = financeConnectionRepository;
        this.financeSyncRepository = financeSyncRepository;
        this.financeConnectionService = financeConnectionService;
        this.handlers = indexHandlers(handlers);
        this.transactionTemplate = transactionTemplate;
    }

    public FinanceSyncResponse sync(Long userId, FinanceSyncRequest request) {
        FinanceSyncScope scope = FinanceSyncScope.from(request == null ? null : request.scope());
        requireActiveUser(userId);
        FinanceConnection connection = financeConnectionService.requireConnected(userId);
        String userKey = financeConnectionService.userKeyOf(connection);

        FinanceSync sync = transactionTemplate.execute(status -> begin(userId, scope));

        List<FinanceSyncScopeResult> results = new ArrayList<>();
        for (FinanceSyncScope target : scope.targets()) {
            results.add(runHandler(target, userId, userKey));
        }

        FinanceSync finished = transactionTemplate.execute(status -> finish(sync.getId(), userId, results));
        return FinanceSyncResponse.of(finished, results);
    }

    /**
     * 가장 최근 동기화(상태 무관)와 진행 여부. 이력이 없으면 sync = null.
     */
    @Transactional(readOnly = true)
    public FinanceSyncStatusResponse getStatus(Long userId) {
        requireActiveUser(userId);
        LocalDateTime staleThreshold = LocalDateTime.now().minus(STALE_AFTER);
        Optional<FinanceSync> latest = financeSyncRepository.findTopByUserIdOrderByStartedAtDescIdDesc(userId);
        boolean inProgress = latest
                .map(sync -> sync.isInProgress() && !sync.isStaleSince(staleThreshold))
                .orElse(false);
        return new FinanceSyncStatusResponse(
                inProgress,
                latest.map(sync -> FinanceSyncResponse.of(sync, null)).orElse(null)
        );
    }

    /**
     * 마지막으로 데이터가 반영된 시각(finance_connection.last_synced_at)과 마지막 성공 이력.
     */
    @Transactional(readOnly = true)
    public FinanceSyncLatestResponse getLatest(Long userId) {
        requireActiveUser(userId);
        LocalDateTime lastSyncedAt = financeConnectionRepository.findByUserId(userId)
                .map(FinanceConnection::getLastSyncedAt)
                .orElse(null);
        FinanceSyncResponse lastCompleted = financeSyncRepository
                .findTopByUserIdAndStatusOrderByCompletedAtDescIdDesc(userId, FinanceSyncStatus.COMPLETED)
                .map(sync -> FinanceSyncResponse.of(sync, null))
                .orElse(null);
        return new FinanceSyncLatestResponse(lastSyncedAt, lastCompleted);
    }

    private FinanceSync begin(Long userId, FinanceSyncScope scope) {
        LocalDateTime now = LocalDateTime.now();
        // 연결 행 잠금: 같은 사용자의 동시 동기화 요청·연결 해제를 직렬화한다.
        financeConnectionRepository.findByUserIdForUpdate(userId)
                .filter(connection -> connection.isConnected() && connection.hasUserKey())
                .orElseThrow(() -> new BusinessException(ErrorCode.FINANCE_NOT_CONNECTED));

        LocalDateTime staleThreshold = now.minus(STALE_AFTER);
        for (FinanceSync running : financeSyncRepository.findAllByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            if (running.isStaleSince(staleThreshold)) {
                log.warn("Expiring stale finance sync {} for user {}", running.getId(), userId);
                running.fail(running.getProgress() == null ? 0 : running.getProgress(), STALE_MESSAGE, now);
            } else {
                throw new BusinessException(ErrorCode.FINANCE_SYNC_IN_PROGRESS);
            }
        }
        return financeSyncRepository.save(FinanceSync.start(userId, scope, now));
    }

    private FinanceSyncScopeResult runHandler(FinanceSyncScope target, Long userId, String userKey) {
        FinanceSyncScopeHandler handler = handlers.get(target);
        if (handler == null) {
            return FinanceSyncScopeResult.skipped(target);
        }
        try {
            FinanceSyncScopeResult result = handler.sync(userId, userKey);
            return result != null ? result : FinanceSyncScopeResult.completed(target, 0);
        } catch (BusinessException exception) {
            log.warn("Finance sync scope {} failed for user {}: {}", target, userId, exception.getMessage());
            return FinanceSyncScopeResult.failed(target, exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Finance sync scope {} failed unexpectedly for user {}", target, userId, exception);
            return FinanceSyncScopeResult.failed(target, "동기화 처리 중 오류가 발생했습니다.");
        }
    }

    private FinanceSync finish(Long syncId, Long userId, List<FinanceSyncScopeResult> results) {
        LocalDateTime now = LocalDateTime.now();
        FinanceSync sync = financeSyncRepository.findById(syncId).orElseThrow();

        long failed = results.stream().filter(FinanceSyncScopeResult::isFailed).count();
        int progress = results.isEmpty()
                ? 100
                : (int) Math.round((results.size() - failed) * 100.0 / results.size());
        if (failed > 0) {
            String message = results.stream()
                    .filter(FinanceSyncScopeResult::isFailed)
                    .map(result -> result.scope() + ": " + result.message())
                    .collect(Collectors.joining("; "));
            sync.fail(progress, message, now);
        } else {
            sync.complete(progress, now);
        }

        if (results.stream().anyMatch(FinanceSyncScopeResult::isCompleted)) {
            financeConnectionRepository.findByUserId(userId)
                    .ifPresent(connection -> connection.markSynced(now));
        }
        return sync;
    }

    private void requireActiveUser(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
    }

    private static Map<FinanceSyncScope, FinanceSyncScopeHandler> indexHandlers(List<FinanceSyncScopeHandler> handlers) {
        Map<FinanceSyncScope, FinanceSyncScopeHandler> indexed = new EnumMap<>(FinanceSyncScope.class);
        for (FinanceSyncScopeHandler handler : handlers) {
            if (handler.scope() == FinanceSyncScope.ALL) {
                throw new IllegalStateException("FinanceSyncScopeHandler must target a concrete scope, not ALL: " + handler);
            }
            FinanceSyncScopeHandler previous = indexed.putIfAbsent(handler.scope(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate FinanceSyncScopeHandler for scope " + handler.scope() + ": " + previous + ", " + handler
                );
            }
        }
        return indexed;
    }
}
