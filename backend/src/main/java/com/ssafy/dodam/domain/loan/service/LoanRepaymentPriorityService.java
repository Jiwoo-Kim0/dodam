package com.ssafy.dodam.domain.loan.service;

import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;

import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentPriorityItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentPriorityResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanUnavailableResponse;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 대출의 상환 검토 순서를 제공한다.
 *
 * <p>시장 비교 없이 현재 금리만으로 계산하므로 상품 데이터에 의존하지 않는다. 추가 상환예산 입력과
 * 수수료 자료가 없어 권장 상환금액, 확정 절감액, 완납일은 만들지 않는다. 추가 상환 절감액은 명세 17의
 * `POST /api/v1/simulations/loan/prepayment`가, 목표를 반영한 우선순위는 명세 16의
 * `GET /api/v1/recommendations/priorities`가 소유한다.
 */
@Service
@Transactional(readOnly = true)
public class LoanRepaymentPriorityService {
    static final String STRATEGY_HIGHEST_RATE_FIRST = "HIGHEST_RATE_FIRST";
    private static final String REASON_HIGHER_CURRENT_RATE = "HIGHER_CURRENT_RATE";
    private static final String REASON_INVALID_TERMS = "INVALID_TERMS";
    private static final String REASON_INCOMPLETE_LOAN_DATA = "INCOMPLETE_LOAN_DATA";
    private static final String STATUS_COMPLETE = "COMPLETE";

    private final LoanRepository loans;
    private final LoanAnalysisClock clock;

    public LoanRepaymentPriorityService(LoanRepository loans, LoanAnalysisClock clock) {
        this.loans = loans;
        this.clock = clock;
    }

    public LoanRepaymentPriorityResponse repaymentPriority(Long userId) {
        LocalDate today = clock.today();
        List<Loan> outstanding = loans.findAll(ownedBy(userId)).stream()
                .filter(loan -> loan.getStatus().isOutstanding())
                .filter(loan -> loan.getBalance() != null && loan.getBalance() > 0)
                .toList();

        // 금리가 없거나 음수인 대출은 순위를 매길 수 없다. ERD의 chk_loan_interest_rate가 NOT NULL과
        // 0 이상을 보장하므로 MySQL에서는 도달하지 않지만, 제약이 없는 테스트 환경을 위해 분기를 남긴다.
        // 무이자(0%)는 무효가 아니라 정상 값이므로 순위에 포함한다.
        List<Loan> rankable = outstanding.stream().filter(LoanRepaymentPriorityService::hasUsableRate).toList();
        List<LoanUnavailableResponse> unavailableLoans = outstanding.stream()
                .filter(loan -> !hasUsableRate(loan))
                .map(loan -> new LoanUnavailableResponse(loan.getId(), List.of(REASON_INVALID_TERMS)))
                .sorted(Comparator.comparing(LoanUnavailableResponse::loanId))
                .toList();

        List<LoanRepaymentPriorityItemResponse> items = rank(rankable);
        List<String> unavailableReasons = unavailableLoans.isEmpty()
                ? List.of() : List.of(REASON_INCOMPLETE_LOAN_DATA);
        return new LoanRepaymentPriorityResponse(today, assumptions(outstanding), unavailableLoans.isEmpty(),
                unavailableReasons, unavailableLoans, STRATEGY_HIGHEST_RATE_FIRST, rankable.size(), items);
    }

    /**
     * 현재 금리 내림차순 → 잔액 오름차순 → loanId 오름차순으로 정렬한다.
     *
     * <p>loanId가 유일하므로 조회 순서와 무관하게 결과가 하나로 정해진다. 잔액 오름차순은 동률을 결정적으로
     * 해소하기 위한 기준이며 이자 절감 최적화가 아니다.
     */
    private static List<LoanRepaymentPriorityItemResponse> rank(List<Loan> rankable) {
        List<Loan> sorted = rankable.stream()
                .sorted(Comparator.comparing(Loan::getInterestRate, Comparator.reverseOrder())
                        .thenComparing(Loan::getBalance)
                        .thenComparing(Loan::getId))
                .toList();
        List<LoanRepaymentPriorityItemResponse> items = new ArrayList<>();
        for (int index = 0; index < sorted.size(); index++) {
            Loan loan = sorted.get(index);
            items.add(new LoanRepaymentPriorityItemResponse(index + 1, loan.getId(), loan.getAccountName(),
                    loan.getAccountNoMasked(), loan.getInstitutionName(), loan.getBalance(), loan.getInterestRate(),
                    loan.getStatus() == LoanStatus.OVERDUE, STATUS_COMPLETE, List.of(REASON_HIGHER_CURRENT_RATE)));
        }
        return List.copyOf(items);
    }

    private static boolean hasUsableRate(Loan loan) {
        BigDecimal rate = loan.getInterestRate();
        return rate != null && rate.signum() >= 0;
    }

    /**
     * 기존 이자 분석과 같은 가정 코드를 재사용한다. 수수료와 연체 가산금리 자료가 없다는 사실은 순위
     * 계산에도 그대로 적용된다.
     */
    private static List<String> assumptions(List<Loan> outstanding) {
        List<String> assumptions = new ArrayList<>(List.of("EXCLUDES_FEES", "EXCLUDES_GOAL_BUDGET",
                "ORDER_BY_CURRENT_RATE_ONLY"));
        if (outstanding.stream().anyMatch(loan -> loan.getStatus() == LoanStatus.OVERDUE)) {
            assumptions.add("EXCLUDES_OVERDUE_INTEREST_AND_ARREARS");
        }
        return List.copyOf(new TreeSet<>(assumptions));
    }
}
