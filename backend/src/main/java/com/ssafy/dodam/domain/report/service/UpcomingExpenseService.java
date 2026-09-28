package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowEventCollection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent.Direction;
import com.ssafy.dodam.domain.cashflow.service.CashflowEventCollector;
import com.ssafy.dodam.domain.report.dto.UpcomingExpensesResponse;
import com.ssafy.dodam.domain.report.dto.UpcomingExpensesResponse.Item;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 다음달 예정비용(API 20 #7). 확정된 스케줄만 모으고 추정하지 않는다.
 *
 * <p>원본은 현금흐름 예측과 같은 {@link CashflowEventCollector}다. 카드 청구·대출 상환·정기결제·적금 납입을
 * 각자 다시 읽으면 같은 출금이 두 번 잡히거나(구독이 카드 청구서에 이미 들어 있는 경우) 한쪽만 잡힌다.
 * 수집기가 이미 dedupe 키로 중복을 막고, 확정할 수 없는 원본은 금액에 넣지 않고 사유로 남긴다.
 *
 * <p>수입(INFLOW)은 제외한다. 이 응답은 "나갈 돈"이다.
 *
 * <p>이 서비스에는 트랜잭션을 걸지 않는다. 수집기가 자기 읽기 전용 트랜잭션에서 돌아야, 수집이 실패했을 때
 * 그 트랜잭션만 롤백되고 여기서 사유로 바꿔 담을 수 있다. 같은 트랜잭션을 공유하면 예외를 잡아도
 * rollback-only 표시가 남아 응답 커밋이 500으로 터진다.
 */
@Service
public class UpcomingExpenseService {

    private static final Logger log = LoggerFactory.getLogger(UpcomingExpenseService.class);

    /** 수집기의 sourceType을 응답 코드로 옮기는 표. 모르는 원본은 담지 않는다(정체 모를 금액을 합계에 넣지 않는다). */
    private static final Map<String, String> SOURCE_TYPES = Map.of(
            "CARD_BILLING", "CARD",
            "LOAN", "LOAN",
            "LOAN_REPAYMENT", "LOAN",
            "SUBSCRIPTION", "SUBSCRIPTION",
            "SAVINGS_ACCOUNT", "SAVINGS",
            "SAVINGS_PAYMENT", "SAVINGS",
            "MINUS_ACCOUNT", "MINUS_ACCOUNT");

    private static final Map<String, String> LABELS = Map.of(
            "CARD", "카드 결제예정금액",
            "LOAN", "대출 상환",
            "SUBSCRIPTION", "정기결제",
            "SAVINGS", "적금 납입",
            "MINUS_ACCOUNT", "마이너스통장 상환");

    private final CashflowEventCollector events;
    private final ReportPeriodPolicy periods;
    private final ReportClock clock;

    public UpcomingExpenseService(CashflowEventCollector events, ReportPeriodPolicy periods, ReportClock clock) {
        this.events = events;
        this.periods = periods;
        this.clock = clock;
    }

    public UpcomingExpensesResponse upcoming(Long userId, String yearMonth) {
        LocalDate today = clock.today();
        YearMonth month = periods.resolveUpcomingMonth(yearMonth, today);
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        if (monthEnd.isBefore(today)) {
            return UpcomingExpensesResponse.unavailable(month.toString(), monthStart, monthEnd, "PERIOD_ELAPSED");
        }
        // 오늘 이전 구간은 이미 결제됐거나 지나갔다. "예정"에 넣지 않고 부분 집계임을 알린다.
        LocalDate from = monthStart.isBefore(today) ? today : monthStart;
        List<String> warnings = new ArrayList<>();
        if (monthStart.isBefore(today)) {
            warnings.add(UpcomingExpensesResponse.PARTIALLY_ELAPSED);
        }

        CashflowEventCollection collected;
        try {
            collected = events.collect(userId, from, monthEnd);
        } catch (InvalidRequestException conflict) {
            // 같은 예정 이벤트의 날짜·금액이 원본마다 다르다. 확정되지 않은 금액을 합계로 내보내지 않는다.
            log.warn("예정 비용 원본 충돌 userId={} period={}~{}", userId, from, monthEnd, conflict);
            return UpcomingExpensesResponse.unavailable(month.toString(), from, monthEnd, "SOURCE_CONFLICT");
        } catch (RuntimeException failure) {
            // 원본 조회 자체가 실패했다. 리포트 화면 전체를 500으로 떨어뜨리지 않고 이 카드만 비운다.
            log.warn("예정 비용 수집 실패 userId={} period={}~{}", userId, from, monthEnd, failure);
            return UpcomingExpensesResponse.unavailable(month.toString(), from, monthEnd, "SOURCE_UNAVAILABLE");
        }
        warnings.addAll(collected.reasons());

        Map<String, List<CashflowPlannedEvent>> grouped = new LinkedHashMap<>();
        for (CashflowPlannedEvent event : collected.events()) {
            if (event.direction() != Direction.OUTFLOW) {
                continue;
            }
            String sourceType = SOURCE_TYPES.get(event.sourceType());
            if (sourceType == null) {
                continue;
            }
            grouped.computeIfAbsent(sourceType, key -> new ArrayList<>()).add(event);
        }
        List<Item> items = new ArrayList<>();
        long total = 0L;
        for (var entry : grouped.entrySet()) {
            long amount = entry.getValue().stream().mapToLong(CashflowPlannedEvent::amount).sum();
            total += amount;
            items.add(new Item(entry.getKey(), LABELS.getOrDefault(entry.getKey(), entry.getKey()), amount,
                    singleDueDate(entry.getValue())));
        }
        items.sort((left, right) -> Long.compare(right.amount(), left.amount()));
        return new UpcomingExpensesResponse(month.toString(), from, monthEnd, total, items,
                !warnings.isEmpty(), warnings.stream().distinct().sorted().toList(), null);
    }

    /** 그 원본의 예정이 한 건뿐이면 그 날짜, 여러 건이면 null이다(합계만 의미 있다). */
    private static LocalDate singleDueDate(List<CashflowPlannedEvent> events) {
        List<LocalDate> dates = events.stream().map(CashflowPlannedEvent::eventDate).distinct().toList();
        return dates.size() == 1 ? dates.getFirst() : null;
    }
}
