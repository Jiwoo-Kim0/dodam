package com.ssafy.dodam.domain.loan.service;

import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.hasLoanType;
import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.hasStatus;
import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;

import com.ssafy.dodam.domain.loan.dto.response.LoanDetailResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentScheduleResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanSummaryResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanTypeSummaryResponse;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.LoanType;
import com.ssafy.dodam.domain.loan.repository.LoanRepaymentReader;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class LoanQueryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    // 상환 중인 대출을 먼저 보여주고, 같은 상태에서는 금리가 높은 대출을 위로 올린다.
    // 고금리 부채를 먼저 확인하려는 기획 의도에 맞춘 정렬이다.
    private static final Sort LOAN_SORT = Sort.by(
            Sort.Order.asc("status"),
            Sort.Order.desc("interestRate"),
            Sort.Order.asc("id")
    );

    private static final int RATE_SCALE = 4;

    private final LoanRepository loanRepository;
    private final LoanRepaymentReader loanRepaymentReader;
    private final LoanRepaymentScheduleCalculator loanRepaymentScheduleCalculator;
    private final SensitiveNumberHasher sensitiveNumberHasher;
    private final QueryPeriodPolicy queryPeriodPolicy;

    public LoanQueryService(
            LoanRepository loanRepository,
            LoanRepaymentReader loanRepaymentReader,
            LoanRepaymentScheduleCalculator loanRepaymentScheduleCalculator,
            SensitiveNumberHasher sensitiveNumberHasher,
            QueryPeriodPolicy queryPeriodPolicy
    ) {
        this.loanRepository = loanRepository;
        this.loanRepaymentReader = loanRepaymentReader;
        this.loanRepaymentScheduleCalculator = loanRepaymentScheduleCalculator;
        this.sensitiveNumberHasher = sensitiveNumberHasher;
        this.queryPeriodPolicy = queryPeriodPolicy;
    }

    public List<LoanResponse> getLoans(Long userId, String status, String loanType) {
        return findLoans(userId, status, loanType).stream()
                .map(LoanResponse::from)
                .toList();
    }

    public LoanSummaryResponse getSummary(Long userId) {
        LocalDate asOfDate = LocalDate.now(SERVICE_ZONE);
        List<Loan> loans = findLoans(userId, null, null).stream()
                .filter(loan -> loan.getStatus().isOutstanding())
                .toList();

        long totalOriginalPrincipal = 0;
        long totalBalance = 0;
        long monthlyRepayment = 0;
        BigDecimal weightedRateSum = BigDecimal.ZERO;
        LocalDate nextRepaymentDate = null;
        Map<LoanType, LoanTypeAccumulator> loanTypes = new EnumMap<>(LoanType.class);

        for (Loan loan : loans) {
            long balance = loan.getBalance() == null ? 0L : loan.getBalance();
            totalOriginalPrincipal += loan.getOriginalPrincipal() == null
                    ? 0L
                    : loan.getOriginalPrincipal();
            totalBalance += balance;
            weightedRateSum = weightedRateSum.add(
                    loan.getInterestRate().multiply(BigDecimal.valueOf(balance))
            );

            LoanRepaymentSchedule schedule = loanRepaymentScheduleCalculator
                    .calculate(loan, asOfDate);
            monthlyRepayment += schedule.nextAmount();
            nextRepaymentDate = earlier(nextRepaymentDate, loan.getNextRepaymentDate());

            loanTypes.computeIfAbsent(loan.getLoanType(), LoanTypeAccumulator::new).add(balance);
        }

        List<LoanTypeSummaryResponse> loanTypeSummaries = new ArrayList<>(loanTypes.size());
        loanTypes.values().forEach(accumulator -> loanTypeSummaries.add(accumulator.toResponse()));

        return new LoanSummaryResponse(
                loans.size(),
                totalOriginalPrincipal,
                totalBalance,
                monthlyRepayment,
                weightedAverageRate(weightedRateSum, totalBalance),
                nextRepaymentDate,
                loanTypeSummaries
        );
    }

    public LoanDetailResponse getLoan(Long userId, String accountNo) {
        return LoanDetailResponse.from(findLoan(userId, accountNo));
    }

    public PageResponse<LoanRepaymentResponse> getRepayments(
            Long userId,
            String accountNo,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        Loan loan = findLoan(userId, accountNo);
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate);
        Pageable pageable = PageRequest.of(page, size);

        return PageResponse.from(
                loanRepaymentReader.read(loan.getId(), period, pageable),
                LoanRepaymentResponse::from
        );
    }

    public LoanRepaymentScheduleResponse getRepaymentSchedule(Long userId, String accountNo) {
        Loan loan = findLoan(userId, accountNo);
        return LoanRepaymentScheduleResponse.from(
                loan,
                loanRepaymentScheduleCalculator.calculate(loan, LocalDate.now(SERVICE_ZONE))
        );
    }

    private List<Loan> findLoans(Long userId, String status, String loanType) {
        return loanRepository.findAll(
                ownedBy(userId)
                        .and(hasStatus(parseStatus(status)))
                        .and(hasLoanType(parseLoanType(loanType))),
                LOAN_SORT
        );
    }

    /**
     * 타인 대출과 존재하지 않는 대출을 구분하지 않는다(리소스 존재 여부 노출 방지).
     */
    private Loan findLoan(Long userId, String accountNo) {
        return loanRepository
                .findByUserIdAndAccountNoHash(userId, sensitiveNumberHasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException("대출을 찾을 수 없습니다."));
    }

    /**
     * 잔액 가중평균 금리. 잔액 합계가 0이면 대표 금리를 정할 수 없어 null이다.
     */
    private static BigDecimal weightedAverageRate(BigDecimal weightedRateSum, long totalBalance) {
        if (totalBalance <= 0) {
            return null;
        }
        return weightedRateSum.divide(
                BigDecimal.valueOf(totalBalance),
                RATE_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private static LocalDate earlier(LocalDate current, LocalDate candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static LoanStatus parseStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return null;
        }
        try {
            return LoanStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("지원하지 않는 대출 상태입니다.");
        }
    }

    private static LoanType parseLoanType(String loanType) {
        String normalized = normalize(loanType);
        if (normalized == null) {
            return null;
        }
        try {
            return LoanType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("지원하지 않는 대출 유형입니다.");
        }
    }

    private static final class LoanTypeAccumulator {

        private final LoanType loanType;
        private long loanCount;
        private long balance;

        private LoanTypeAccumulator(LoanType loanType) {
            this.loanType = loanType;
        }

        private void add(long balance) {
            this.loanCount++;
            this.balance += balance;
        }

        private LoanTypeSummaryResponse toResponse() {
            return new LoanTypeSummaryResponse(loanType.name(), loanCount, balance);
        }
    }
}
