package com.ssafy.dodam.domain.subscription.service;

import com.ssafy.dodam.domain.subscription.dto.request.ChangeSubscriptionStatusRequest;
import com.ssafy.dodam.domain.subscription.dto.request.UpdateSubscriptionRequest;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionResponse;
import com.ssafy.dodam.domain.subscription.entity.Subscription;
import com.ssafy.dodam.domain.subscription.entity.SubscriptionStatus;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 7 정기결제 수정·정지/재개·취소(S15P21D105-514).
 *
 * <p>종료 상태(CANCELED·EXPIRED)는 어떤 변경도 409 SUBSCRIPTION_NOT_EDITABLE. 값이 전부 같으면 `updated_at`을 건드리지 않고
 * 현재 상태를 200으로 돌려준다(중복 요청 방지). 다음 결제일은 {@link NextPaymentDateCalculator} 규칙으로 다시 잡는다.
 * 금융망에 변경을 전달하는 경로는 없다(명세 7의 "SSAFY 연동"은 조회 데이터 출처를 뜻하며, 정기결제 변경 API는 금융망에 없다).
 */
@Service
@Transactional
public class SubscriptionManagementService {

    static final String END_DATE_BEFORE_TODAY_MESSAGE = "종료일은 오늘 이후여야 합니다.";
    static final String END_DATE_BEFORE_NEXT_PAYMENT_MESSAGE = "종료일은 다음 결제일 이후여야 합니다.";
    static final String STATUS_OR_ACTION_REQUIRED_MESSAGE = "status 또는 action 중 하나를 보내야 합니다.";
    static final String STATUS_ACTION_CONFLICT_MESSAGE = "status와 action이 서로 다른 상태를 가리킵니다.";
    static final String PAUSE_RESUME_ONLY_MESSAGE = "정지/재개는 PAUSED 또는 ACTIVE로만 바꿀 수 있습니다. 취소는 DELETE를 사용합니다.";
    static final String UNKNOWN_ACTION_MESSAGE = "지원하지 않는 action입니다. pause 또는 resume을 사용합니다.";
    static final String EXPIRED_NOT_ALLOWED_MESSAGE = "EXPIRED는 직접 지정할 수 없습니다.";

    private final SubscriptionService subscriptionService;
    private final SubscriptionResponseAssembler assembler;
    private final SubscriptionClock clock;

    public SubscriptionManagementService(
            SubscriptionService subscriptionService,
            SubscriptionResponseAssembler assembler,
            SubscriptionClock clock
    ) {
        this.subscriptionService = subscriptionService;
        this.assembler = assembler;
        this.clock = clock;
    }

    /**
     * 입력된 필드만 바꾼다. 결제일이 바뀐 ACTIVE 정기결제는 다음 결제일을 새 결제일 기준으로 다시 잡는다.
     * 종료일은 오늘 이후여야 하고 다음 결제일(있으면)보다 앞설 수 없다(DB `chk_subscription_dates`).
     */
    public SubscriptionResponse update(Long userId, String subscriptionId, UpdateSubscriptionRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_FIELDS_TO_UPDATE);
        }
        Subscription subscription = subscriptionService.findOwned(userId, subscriptionId);
        requireEditable(subscription);
        LocalDateTime now = clock.now();
        LocalDate today = now.toLocalDate();

        SubscriptionStatus nextStatus = null;
        if (request.status() != null) {
            nextStatus = SubscriptionStatus.parse(request.status());
            if (nextStatus == SubscriptionStatus.EXPIRED) {
                throw new InvalidRequestException(EXPIRED_NOT_ALLOWED_MESSAGE);
            }
            if (nextStatus != subscription.getStatus() && !subscription.getStatus().canTransitionTo(nextStatus)) {
                throw new BusinessException(ErrorCode.SUBSCRIPTION_STATUS_TRANSITION_NOT_ALLOWED);
            }
        }
        if (request.endDate() != null && request.endDate().isBefore(today)) {
            throw new InvalidRequestException(END_DATE_BEFORE_TODAY_MESSAGE);
        }

        if (request.paymentAmount() != null) {
            subscription.changePaymentAmount(request.paymentAmount(), now);
        }
        boolean paymentDayChanged = request.paymentDay() != null && subscription.changePaymentDay(request.paymentDay(), now);
        if (request.endDate() != null) {
            subscription.changeEndDate(request.endDate(), now);
        }

        if (nextStatus != null && nextStatus != subscription.getStatus()) {
            applyStatus(subscription, nextStatus, now);
        } else if (paymentDayChanged && subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            subscription.changeNextPaymentDate(NextPaymentDateCalculator.nextOnOrAfter(subscription, today), now);
        }

        if (subscription.getEndDate() != null && subscription.getNextPaymentDate() != null
                && subscription.getNextPaymentDate().isAfter(subscription.getEndDate())) {
            throw new InvalidRequestException(END_DATE_BEFORE_NEXT_PAYMENT_MESSAGE);
        }
        return assembler.toResponse(subscription, today);
    }

    /**
     * 정지/재개. `status`(ACTIVE·PAUSED) 또는 `action`(pause·resume) 중 하나로 목표 상태를 정한다.
     * 이미 그 상태면 아무것도 바꾸지 않고 현재 상태를 돌려준다.
     */
    public SubscriptionResponse changeStatus(Long userId, String subscriptionId, ChangeSubscriptionStatusRequest request) {
        SubscriptionStatus target = resolveTarget(request);
        Subscription subscription = subscriptionService.findOwned(userId, subscriptionId);
        requireEditable(subscription);
        LocalDateTime now = clock.now();
        if (subscription.getStatus() != target) {
            if (!subscription.getStatus().canTransitionTo(target)) {
                throw new BusinessException(ErrorCode.SUBSCRIPTION_STATUS_TRANSITION_NOT_ALLOWED);
            }
            applyStatus(subscription, target, now);
        }
        return assembler.toResponse(subscription, now.toLocalDate());
    }

    /** 취소. 상태를 CANCELED로 바꾸고 `canceled_at`을 남긴다. 행은 지우지 않아 목록·이력에 계속 보인다. */
    public void cancel(Long userId, String subscriptionId) {
        Subscription subscription = subscriptionService.findOwned(userId, subscriptionId);
        requireEditable(subscription);
        subscription.cancel(clock.now());
    }

    private void applyStatus(Subscription subscription, SubscriptionStatus next, LocalDateTime now) {
        switch (next) {
            case PAUSED -> subscription.pause(now);
            case ACTIVE -> subscription.resume(NextPaymentDateCalculator.nextOnOrAfter(subscription, now.toLocalDate()), now);
            case CANCELED -> subscription.cancel(now);
            case EXPIRED -> throw new InvalidRequestException(EXPIRED_NOT_ALLOWED_MESSAGE);
        }
    }

    private static void requireEditable(Subscription subscription) {
        if (subscription.getStatus().isTerminal()) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_EDITABLE);
        }
    }

    private static SubscriptionStatus resolveTarget(ChangeSubscriptionStatusRequest request) {
        SubscriptionStatus fromStatus = null;
        if (request.status() != null && !request.status().isBlank()) {
            fromStatus = SubscriptionStatus.parse(request.status());
            if (fromStatus != SubscriptionStatus.ACTIVE && fromStatus != SubscriptionStatus.PAUSED) {
                throw new InvalidRequestException(PAUSE_RESUME_ONLY_MESSAGE);
            }
        }
        SubscriptionStatus fromAction = null;
        if (request.action() != null && !request.action().isBlank()) {
            fromAction = switch (request.action().strip().toLowerCase(Locale.ROOT)) {
                case "pause" -> SubscriptionStatus.PAUSED;
                case "resume" -> SubscriptionStatus.ACTIVE;
                default -> throw new InvalidRequestException(UNKNOWN_ACTION_MESSAGE);
            };
        }
        if (fromStatus == null && fromAction == null) {
            throw new InvalidRequestException(STATUS_OR_ACTION_REQUIRED_MESSAGE);
        }
        if (fromStatus != null && fromAction != null && fromStatus != fromAction) {
            throw new InvalidRequestException(STATUS_ACTION_CONFLICT_MESSAGE);
        }
        return fromStatus != null ? fromStatus : fromAction;
    }
}
