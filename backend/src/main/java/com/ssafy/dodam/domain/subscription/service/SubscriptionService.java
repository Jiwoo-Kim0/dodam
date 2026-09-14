package com.ssafy.dodam.domain.subscription.service;

import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionDetailResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionPaymentHistoryResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionSummaryResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionSummaryResponse.CategoryAmount;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionSummaryResponse.Upcoming;
import com.ssafy.dodam.domain.subscription.entity.Subscription;
import com.ssafy.dodam.domain.subscription.entity.SubscriptionCategory;
import com.ssafy.dodam.domain.subscription.entity.SubscriptionStatus;
import com.ssafy.dodam.domain.subscription.repository.SubscriptionPaymentHistoryRepository;
import com.ssafy.dodam.domain.subscription.repository.SubscriptionRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 7 정기결제 목록·상세·이력·요약(S15P21D105-512).
 *
 * <p>정기결제는 금융망 동기화·seed가 적재하고 이 서비스는 읽기만 한다. 사용자당 건수는 수십 건을 넘지 않는다는 전제로
 * 목록 필터·정렬·페이지는 메모리에서 한다(Goal 목록과 같은 방식). 변경은 {@link SubscriptionManagementService}(514).
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionService {

    static final String SUBSCRIPTION_NOT_FOUND_MESSAGE = "정기결제를 찾을 수 없습니다.";
    static final String UNCATEGORIZED = SubscriptionCategory.ETC.name();

    /** 결제 중인 것이 먼저(status 선언 순), 같은 상태 안에서는 다음 결제일이 가까운 순(없으면 뒤), 이름 순. */
    private static final Comparator<Subscription> LIST_ORDER = Comparator
            .comparing(Subscription::getStatus)
            .thenComparing(Subscription::getNextPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Subscription::getName)
            .thenComparing(Subscription::getId);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPaymentHistoryRepository paymentHistoryRepository;
    private final SubscriptionResponseAssembler assembler;
    private final QueryPeriodPolicy queryPeriodPolicy;
    private final SubscriptionClock clock;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPaymentHistoryRepository paymentHistoryRepository,
            SubscriptionResponseAssembler assembler,
            QueryPeriodPolicy queryPeriodPolicy,
            SubscriptionClock clock
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.assembler = assembler;
        this.queryPeriodPolicy = queryPeriodPolicy;
        this.clock = clock;
    }

    public PageResponse<SubscriptionResponse> getSubscriptions(
            Long userId, String status, String category, int page, int size
    ) {
        SubscriptionStatus statusFilter = SubscriptionStatus.parseFilter(status);
        String categoryFilter = SubscriptionCategory.normalize(category);
        LocalDate today = clock.today();

        List<Subscription> subscriptions = subscriptionRepository.findAllByUserId(userId).stream()
                .filter(subscription -> statusFilter == null || subscription.getStatus() == statusFilter)
                .filter(subscription -> categoryFilter == null
                        || categoryFilter.equals(SubscriptionCategory.normalize(subscription.getCategory())))
                .sorted(LIST_ORDER)
                .toList();

        Pageable pageable = PageRequest.of(page, size);
        int from = (int) Math.min(pageable.getOffset(), subscriptions.size());
        int to = Math.min(from + size, subscriptions.size());
        List<Subscription> pageContent = subscriptions.subList(from, to);
        List<SubscriptionResponse> responses = assembler.toResponses(pageContent, today);

        return PageResponse.from(new PageImpl<>(responses, pageable, subscriptions.size()), response -> response);
    }

    public SubscriptionDetailResponse getSubscription(Long userId, String subscriptionId) {
        Subscription subscription = findOwned(userId, subscriptionId);
        return new SubscriptionDetailResponse(
                assembler.toResponse(subscription, clock.today()),
                SubscriptionDetailResponse.PaymentStats.from(paymentHistoryRepository.findAllBySubscriptionId(subscriptionId))
        );
    }

    /**
     * 기간 정책은 거래내역과 같은 {@link QueryPeriodPolicy}(기본 최근 3개월, 최대 12개월). 최신순.
     */
    public PageResponse<SubscriptionPaymentHistoryResponse> getHistory(
            Long userId, String subscriptionId, LocalDate startDate, LocalDate endDate, int page, int size
    ) {
        findOwned(userId, subscriptionId);
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate, clock.today());
        return PageResponse.from(
                paymentHistoryRepository.findAllBySubscriptionIdAndPaymentDateBetweenOrderByPaymentDateDescIdDesc(
                        subscriptionId, period.startDate(), period.endDate(), PageRequest.of(page, size)),
                SubscriptionPaymentHistoryResponse::from
        );
    }

    public SubscriptionSummaryResponse getSummary(Long userId) {
        List<Subscription> subscriptions = subscriptionRepository.findAllByUserId(userId);
        LocalDate today = clock.today();
        LocalDate within7 = today.plusDays(6);
        LocalDate within30 = today.plusDays(29);

        long monthlyTotal = 0;
        LocalDate nextPaymentDate = null;
        int upcoming7Count = 0;
        long upcoming7Amount = 0;
        int upcoming30Count = 0;
        long upcoming30Amount = 0;
        Map<String, long[]> byCategory = new LinkedHashMap<>();

        for (Subscription subscription : subscriptions) {
            if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
                continue;
            }
            long monthly = subscription.getMonthlyEquivalentAmount();
            monthlyTotal += monthly;
            String category = SubscriptionCategory.normalize(subscription.getCategory());
            long[] bucket = byCategory.computeIfAbsent(category == null ? UNCATEGORIZED : category, key -> new long[2]);
            bucket[0]++;
            bucket[1] += monthly;

            LocalDate next = subscription.getNextPaymentDate();
            if (next == null || next.isBefore(today)) {
                continue;
            }
            if (nextPaymentDate == null || next.isBefore(nextPaymentDate)) {
                nextPaymentDate = next;
            }
            if (!next.isAfter(within7)) {
                upcoming7Count++;
                upcoming7Amount += subscription.getPaymentAmount();
            }
            if (!next.isAfter(within30)) {
                upcoming30Count++;
                upcoming30Amount += subscription.getPaymentAmount();
            }
        }

        List<CategoryAmount> categories = byCategory.entrySet().stream()
                .map(entry -> new CategoryAmount(
                        entry.getKey(), SubscriptionCategory.labelOf(entry.getKey()),
                        (int) entry.getValue()[0], entry.getValue()[1]))
                .sorted(Comparator.comparingLong(CategoryAmount::monthlyAmount).reversed()
                        .thenComparing(CategoryAmount::category))
                .toList();

        return new SubscriptionSummaryResponse(
                subscriptions.size(),
                countByStatus(subscriptions, SubscriptionStatus.ACTIVE),
                countByStatus(subscriptions, SubscriptionStatus.PAUSED),
                countByStatus(subscriptions, SubscriptionStatus.EXPIRED),
                countByStatus(subscriptions, SubscriptionStatus.CANCELED),
                monthlyTotal,
                monthlyTotal * 12,
                nextPaymentDate,
                new Upcoming(upcoming7Count, upcoming7Amount),
                new Upcoming(upcoming30Count, upcoming30Amount),
                categories
        );
    }

    Subscription findOwned(Long userId, String subscriptionId) {
        return subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(SUBSCRIPTION_NOT_FOUND_MESSAGE));
    }

    private static int countByStatus(List<Subscription> subscriptions, SubscriptionStatus status) {
        return (int) subscriptions.stream().filter(subscription -> subscription.getStatus() == status).count();
    }
}
