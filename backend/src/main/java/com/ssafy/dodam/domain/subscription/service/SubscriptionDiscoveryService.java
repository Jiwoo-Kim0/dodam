package com.ssafy.dodam.domain.subscription.service;

import com.ssafy.dodam.domain.subscription.dto.response.RecurringPaymentCandidateResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionCategoryResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionServiceResponse;
import com.ssafy.dodam.domain.subscription.entity.BillingCycle;
import com.ssafy.dodam.domain.subscription.entity.Subscription;
import com.ssafy.dodam.domain.subscription.entity.SubscriptionCategory;
import com.ssafy.dodam.domain.subscription.entity.SubscriptionStatus;
import com.ssafy.dodam.domain.subscription.repository.JdbcRecurringPaymentReader;
import com.ssafy.dodam.domain.subscription.repository.RecurringPaymentRow;
import com.ssafy.dodam.domain.subscription.repository.SubscriptionRepository;
import com.ssafy.dodam.domain.subscription.service.SubscriptionServiceCatalog.CatalogEntry;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 7 미등록 반복결제 탐지·서비스 카탈로그·카테고리(S15P21D105-513).
 *
 * <h2>반복결제 탐지 규칙</h2>
 * <ol>
 *   <li>최근 `lookbackMonths`개월(기본 6, 1~12)의 EXPENSE·숨김 아님·가맹점명 있는 거래를 읽는다. NON_SPENDING(저축·이체)은 뺀다</li>
 *   <li>가맹점명(공백·대소문자 무시)과 금액이 같은 거래를 한 묶음으로 본다. 금액이 다르면(요금제 변경 등) 다른 묶음이다</li>
 *   <li>묶음이 구독 성격이어야 한다: 거래 카테고리(가장 많이 붙은 것)가 {@code SUBSCRIPTION}이거나 가맹점명이 서비스 카탈로그와 맞아야 한다.
 *       보험료·관리비·통신비·학원비처럼 매달 같은 금액이 같은 이름으로 나가는 고정비는 아래 주기 조건을 그대로 만족하므로 이 단계에서 걸러야 한다.
 *       카테고리가 붙지 않은 카드 거래(예: 미분류 "유튜브 프리미엄")는 카탈로그 매칭으로 건진다</li>
 *   <li>서로 다른 결제일이 `minOccurrences`건(기본 3, 2~12) 이상이고, 연속 결제 사이 평균 간격이 주기 범위(주 5~9일, 월 25~36일,
 *       연 350~380일)에 들어야 후보다. 간격이 불규칙하면 뺀다</li>
 *   <li>이미 등록된 정기결제(CANCELED 제외)와 이름이 같으면 뺀다. 취소한 뒤에도 결제가 계속되면 다시 후보로 보인다</li>
 * </ol>
 * 결과는 결제 횟수 → 금액 → 가맹점명 순으로 정렬한다.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionDiscoveryService {

    public static final int DEFAULT_MIN_OCCURRENCES = 3;
    public static final int DEFAULT_LOOKBACK_MONTHS = 6;
    /** 거래 카테고리 코드(`transaction_category.code`) 중 구독 결제로 보는 것. */
    static final String SUBSCRIPTION_CATEGORY_CODE = "SUBSCRIPTION";

    private final JdbcRecurringPaymentReader recurringPaymentReader;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionServiceCatalog catalog;
    private final SubscriptionClock clock;

    public SubscriptionDiscoveryService(
            JdbcRecurringPaymentReader recurringPaymentReader,
            SubscriptionRepository subscriptionRepository,
            SubscriptionServiceCatalog catalog,
            SubscriptionClock clock
    ) {
        this.recurringPaymentReader = recurringPaymentReader;
        this.subscriptionRepository = subscriptionRepository;
        this.catalog = catalog;
        this.clock = clock;
    }

    public List<RecurringPaymentCandidateResponse> getCandidates(Long userId, Integer minOccurrences, Integer lookbackMonths) {
        int minimum = minOccurrences == null ? DEFAULT_MIN_OCCURRENCES : minOccurrences;
        int months = lookbackMonths == null ? DEFAULT_LOOKBACK_MONTHS : lookbackMonths;
        LocalDate today = clock.today();
        LocalDate startDate = today.minusMonths(months).plusDays(1);

        Set<String> registeredNames = subscriptionRepository.findAllByUserId(userId).stream()
                .filter(subscription -> subscription.getStatus() != SubscriptionStatus.CANCELED)
                .map(Subscription::getName)
                .map(SubscriptionDiscoveryService::normalizeName)
                .collect(Collectors.toSet());

        Map<GroupKey, List<RecurringPaymentRow>> groups = new LinkedHashMap<>();
        for (RecurringPaymentRow row : recurringPaymentReader.read(userId, startDate, today)) {
            groups.computeIfAbsent(new GroupKey(normalizeName(row.merchantName()), row.amount()), key -> new ArrayList<>()).add(row);
        }

        List<RecurringPaymentCandidateResponse> candidates = new ArrayList<>();
        for (Map.Entry<GroupKey, List<RecurringPaymentRow>> entry : groups.entrySet()) {
            if (registeredNames.contains(entry.getKey().merchant())) {
                continue;
            }
            toCandidate(entry.getValue(), minimum, today).ifPresent(candidates::add);
        }
        candidates.sort(Comparator.comparingInt(RecurringPaymentCandidateResponse::occurrences).reversed()
                .thenComparing(Comparator.comparingLong(RecurringPaymentCandidateResponse::amount).reversed())
                .thenComparing(RecurringPaymentCandidateResponse::merchantName));
        return candidates;
    }

    private Optional<RecurringPaymentCandidateResponse> toCandidate(List<RecurringPaymentRow> rows, int minimum, LocalDate today) {
        RecurringPaymentRow sample = rows.get(0);
        String categoryCode = mode(rows.stream().map(RecurringPaymentRow::categoryCode).filter(Objects::nonNull).toList());
        Optional<CatalogEntry> matched = catalog.match(sample.merchantName());
        if (!isSubscriptionLike(categoryCode, matched)) {
            return Optional.empty();
        }

        List<LocalDate> dates = rows.stream().map(RecurringPaymentRow::transactionDate).distinct().sorted().toList();
        if (dates.size() < minimum) {
            return Optional.empty();
        }
        long totalGap = 0;
        for (int i = 1; i < dates.size(); i++) {
            totalGap += ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
        }
        double averageInterval = (double) totalGap / (dates.size() - 1);
        BillingCycle cycle = BillingCycle.estimate(averageInterval);
        if (cycle == null) {
            return Optional.empty();
        }

        Integer suggestedDay = cycle == BillingCycle.WEEKLY ? null : mode(dates.stream().map(LocalDate::getDayOfMonth).toList());
        LocalDate lastDate = dates.get(dates.size() - 1);
        LocalDate expectedNext = cycle.next(lastDate, suggestedDay);
        while (expectedNext.isBefore(today)) {
            expectedNext = cycle.next(expectedNext, suggestedDay);
        }
        Long cardId = mode(rows.stream().map(RecurringPaymentRow::cardId).filter(Objects::nonNull).toList());

        return Optional.of(new RecurringPaymentCandidateResponse(
                sample.merchantName().strip(),
                sample.amount(),
                dates.size(),
                dates.get(0),
                lastDate,
                Math.round(averageInterval * 10) / 10.0,
                cycle.name(),
                suggestedDay,
                expectedNext,
                cardId,
                categoryCode,
                matched.map(entry -> entry.category().name()).orElse(null),
                matched.map(CatalogEntry::serviceCode).orElse(null),
                rows.stream()
                        .sorted(Comparator.comparing(RecurringPaymentRow::transactionDate).reversed()
                                .thenComparing(RecurringPaymentRow::transactionId))
                        .map(RecurringPaymentRow::transactionId)
                        .toList()
        ));
    }

    public PageResponse<SubscriptionServiceResponse> getServices(String category, int page, int size) {
        List<CatalogEntry> entries = catalog.entries(category);
        Pageable pageable = PageRequest.of(page, size);
        int from = (int) Math.min(pageable.getOffset(), entries.size());
        int to = Math.min(from + size, entries.size());
        return PageResponse.from(new PageImpl<>(entries.subList(from, to), pageable, entries.size()),
                SubscriptionServiceResponse::from);
    }

    /** 정의된 카테고리 전체를 선언 순으로 주고, 각 카테고리의 본인 정기결제 수(CANCELED 제외)를 붙인다. */
    public List<SubscriptionCategoryResponse> getCategories(Long userId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Subscription subscription : subscriptionRepository.findAllByUserId(userId)) {
            if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
                continue;
            }
            String category = SubscriptionCategory.normalize(subscription.getCategory());
            counts.merge(category == null ? SubscriptionCategory.ETC.name() : category, 1, Integer::sum);
        }
        List<SubscriptionCategoryResponse> responses = new ArrayList<>();
        for (SubscriptionCategory category : SubscriptionCategory.values()) {
            responses.add(new SubscriptionCategoryResponse(
                    category.name(), category.label(), counts.getOrDefault(category.name(), 0)));
        }
        return responses;
    }

    /**
     * 구독 성격 판정. 카테고리 `SUBSCRIPTION`이거나 카탈로그와 맞으면 구독으로 본다. INSURANCE·HOUSING 같은 제외 목록 방식은
     * 헬스장·학원·통신비·대출 상환·정기 배송이 전부 같은 주기 조건을 만족해 목록이 계속 늘어나므로 쓰지 않는다.
     */
    static boolean isSubscriptionLike(String categoryCode, Optional<CatalogEntry> matched) {
        return SUBSCRIPTION_CATEGORY_CODE.equalsIgnoreCase(categoryCode) || matched.isPresent();
    }

    static String normalizeName(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    /** 가장 많이 나온 값. 같은 횟수면 먼저 나온 값. 비어 있으면 null. */
    private static <T> T mode(List<T> values) {
        if (values.isEmpty()) {
            return null;
        }
        Map<T, Long> counts = values.stream().collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private record GroupKey(String merchant, long amount) {
    }
}
