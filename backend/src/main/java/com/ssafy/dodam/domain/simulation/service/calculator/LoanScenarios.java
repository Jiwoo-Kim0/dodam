package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentSchedule;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentTerms;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.simulation.dto.LoanSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.service.ProductOptionSelector;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 대출 3유형 공통 조립. 기존 {@link LoanRepaymentScheduleCalculator}(월이율, 회차 이자 HALF_UP, 마지막 회차 잔액 보정)를
 * 그대로 쓰되, 그 계산기가 누락값을 빈 일정·0%로 처리하는 동작 앞에 필수 조건 검증을 둔다.
 */
final class LoanScenarios {
    static final String DATA_SOURCE_PRODUCT_DB = "PRODUCT_DB";
    static final String DATA_SOURCE_LOAN_LOCAL = "LOAN_LOCAL_SNAPSHOT";
    static final String BASIS_INITIAL_STATE = "INITIAL_STATE";
    static final String BASIS_ACTION_EVENT = "ACTION_EVENT";
    static final String BASIS_PERIOD_TOTAL = "PERIOD_TOTAL";

    private LoanScenarios() {
    }

    /**
     * 보유 대출이 계산 가능한지. ACTIVE·잔액 양수·금리·만기·다음 상환일이 모두 있어야 한다. OVERDUE는 연체이자·미납 회차를
     * 확정할 수 없어 계산하지 않는다. 기준일 이전 다음 상환일(오래된 스냅샷)과 만기 이후 다음 상환일도 거부한다.
     */
    static void requireComputable(LoanSnapshot loan, LocalDate baseDate) {
        boolean usable = loan.status() == LoanStatus.ACTIVE && loan.balance() > 0
                && loan.interestRate() != null && loan.interestRate().signum() >= 0
                && loan.maturityDate() != null && loan.nextRepaymentDate() != null
                && !loan.nextRepaymentDate().isBefore(baseDate)
                && !loan.nextRepaymentDate().isAfter(loan.maturityDate());
        if (!usable) {
            throw new BusinessException(ErrorCode.SIMULATION_LOAN_DATA_UNAVAILABLE);
        }
    }

    static LoanRepaymentTerms terms(LoanSnapshot loan, long balance) {
        return terms(loan, balance, loan.interestRate(), loan.nextRepaymentDate());
    }

    /**
     * 약정일은 {@code repaymentDay}, 없으면 다음 상환일의 일자다. 재계산 일정이 원래 일정과 같은 날짜를 만들려면
     * 첫 회차가 달라져도(예: 2월 28일부터) 같은 약정일로 이후 회차를 만들어야 하므로 명시적으로 고정한다.
     */
    static LoanRepaymentTerms terms(LoanSnapshot loan, long balance, BigDecimal annualRate, LocalDate firstDate) {
        Integer day = loan.repaymentDay() != null ? loan.repaymentDay() : loan.nextRepaymentDate().getDayOfMonth();
        return new LoanRepaymentTerms(balance, annualRate, loan.repaymentType(), day, firstDate, loan.maturityDate());
    }

    /**
     * 전체 잔여 일정. 360회를 넘으면 잘린 이자를 총액으로 쓸 수 없으므로 실행을 거부한다.
     * 빈 일정은 호출자가 이미 거른 조건(만기·상환일 누락)에서만 나오므로 여기서는 자료 불완전으로 본다.
     */
    static List<LoanRepaymentScheduleItem> schedule(LoanRepaymentScheduleCalculator calculator, LoanRepaymentTerms terms,
            LocalDate asOfDate) {
        LoanRepaymentSchedule schedule = calculator.calculate(terms, asOfDate, LoanRepaymentScheduleCalculator.MAX_SCHEDULE_SIZE);
        if (schedule.truncated()) {
            throw new BusinessException(ErrorCode.SIMULATION_PERIOD_NOT_SUPPORTED);
        }
        if (schedule.items().isEmpty()) {
            throw new BusinessException(ErrorCode.SIMULATION_LOAN_DATA_UNAVAILABLE);
        }
        return schedule.items();
    }

    static long totalInterest(List<LoanRepaymentScheduleItem> items) {
        long total = 0;
        for (LoanRepaymentScheduleItem item : items) {
            total = Math.addExact(total, item.interest());
        }
        return total;
    }

    static long totalAmount(List<LoanRepaymentScheduleItem> items) {
        long total = 0;
        for (LoanRepaymentScheduleItem item : items) {
            total = Math.addExact(total, item.amount());
        }
        return total;
    }

    /** 회차를 월별 상환액으로 합산한다. 한 달에 회차가 둘일 수는 없지만 날짜 보정 결과를 가정하지 않고 합산으로 처리한다. */
    static TreeMap<YearMonth, Long> amountByMonth(List<LoanRepaymentScheduleItem> items) {
        TreeMap<YearMonth, Long> byMonth = new TreeMap<>();
        for (LoanRepaymentScheduleItem item : items) {
            byMonth.merge(YearMonth.from(item.repaymentDate()), item.amount(), Math::addExact);
        }
        return byMonth;
    }

    /**
     * {@code MONTHLY_REPAYMENT_yyyy_MM} 전후 항목. 기준일 다음 달부터 만기 월까지, 최대 120개월. 회차가 없는 달은 0이다.
     */
    static List<Item> monthlyRepaymentItems(LocalDate baseDate, List<LoanRepaymentScheduleItem> before,
            List<LoanRepaymentScheduleItem> after, LocalDate fullTermEnd) {
        TreeMap<YearMonth, Long> beforeByMonth = amountByMonth(before);
        TreeMap<YearMonth, Long> afterByMonth = amountByMonth(after);
        YearMonth base = YearMonth.from(baseDate);
        long months = Math.min(ChronoUnit.MONTHS.between(base, YearMonth.from(fullTermEnd)), SimulationInput.MAX_DURATION_MONTHS);
        List<Item> items = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            YearMonth month = base.plusMonths(i);
            items.add(Item.amount("MONTHLY_REPAYMENT_" + month.toString().replace('-', '_'), month + " 월 상환액",
                    beforeByMonth.getOrDefault(month, 0L), afterByMonth.getOrDefault(month, 0L)));
        }
        return items;
    }

    /** 회차별 현금 변화(기존 상환 − 변경 상환). 같은 날짜의 두 일정을 합산한 뒤 비교한다. */
    static List<ScenarioCashflow.Event> repaymentDeltaEvents(List<LoanRepaymentScheduleItem> before, List<LoanRepaymentScheduleItem> after) {
        TreeMap<LocalDate, Long> byDate = new TreeMap<>();
        for (LoanRepaymentScheduleItem item : before) {
            byDate.merge(item.repaymentDate(), item.amount(), Math::addExact);
        }
        for (LoanRepaymentScheduleItem item : after) {
            byDate.merge(item.repaymentDate(), Math.negateExact(item.amount()), Math::addExact);
        }
        List<ScenarioCashflow.Event> events = new ArrayList<>();
        byDate.forEach((date, delta) -> events.add(new ScenarioCashflow.Event(date, delta)));
        return events;
    }

    static Map<String, Object> loanDetails(LoanSnapshot loan) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("loanId", loan.loanId());
        details.put("accountName", loan.accountName());
        details.put("institutionName", loan.institutionName());
        details.put("loanType", loan.loanType() == null ? null : loan.loanType().name());
        details.put("repaymentType", loan.repaymentType() == null ? null : loan.repaymentType().name());
        details.put("interestRate", loan.interestRate());
        details.put("balance", loan.balance());
        details.put("repaymentDay", loan.repaymentDay());
        details.put("nextRepaymentDate", loan.nextRepaymentDate());
        details.put("maturityDate", loan.maturityDate());
        details.put("financialProductId", loan.financialProductId());
        details.put("productLoanType", loan.productLoanType());
        return details;
    }

    static Map<String, Object> productDetails(ProductSnapshot product, ProductOptionSelector.Selection selection) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("productId", product.productId());
        details.put("productName", product.productName());
        details.put("companyName", product.companyName());
        details.put("productType", product.productType().name());
        details.put(product.productType() == ProductType.LOAN ? "loanType" : "savingType",
                product.productType() == ProductType.LOAN ? product.loanType() : product.savingType());
        details.put("maxLimit", product.maxLimit());
        details.put("syncedAt", product.syncedAt());
        ProductSnapshot.Option option = selection.option();
        Map<String, Object> optionDetails = new LinkedHashMap<>();
        optionDetails.put("optionId", option.optionId());
        optionDetails.put("term", option.term());
        optionDetails.put("rateType", option.rateType());
        if (product.productType() == ProductType.LOAN) {
            optionDetails.put("repaymentType", option.repaymentType());
            optionDetails.put("minRate", option.minRate());
            optionDetails.put("maxRate", option.maxRate());
            optionDetails.put("limitAmount", option.limitAmount());
        } else {
            optionDetails.put("rate", option.rate());
        }
        details.put("option", optionDetails);
        details.put("appliedRate", selection.appliedRate());
        if (product.productType() == ProductType.LOAN) {
            details.put("effectiveLimit", selection.effectiveLimit());
        }
        return details;
    }

    static Map<String, Object> timing(String key, LocalDate effectiveDate, String basis) {
        Map<String, Object> timing = new LinkedHashMap<>();
        timing.put("key", key);
        timing.put("effectiveDate", effectiveDate);
        timing.put("basis", basis);
        return timing;
    }

    static Map<String, Object> periodTiming(String key, LocalDate from, LocalDate to) {
        Map<String, Object> timing = timing(key, from, BASIS_PERIOD_TOTAL);
        timing.put("fromDate", from);
        timing.put("toDate", to);
        return timing;
    }
}
