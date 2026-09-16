package com.ssafy.dodam.domain.installment.service;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse.AvailableAmountBasis;
import com.ssafy.dodam.domain.loan.service.LoanQueryService;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 할부 부담률의 분모인 월 예상 가용금액을 읽는다.
 *
 * <p>기획안이 가용금액을 "월 소득에서 고정지출·저축·대출상환을 뺀 금액"으로 일관되게 정의하므로 그 네 항목을
 * 각 도메인의 현재 값으로 모은다. 모두 월 단위 값이라 대상 월이 현재 월이든 미래 월이든 같은 값을 쓰며,
 * 기준일부터 월말까지로 안분하지 않는다(안분 근거가 기획안에 없다). 할부 결제액은 빼지 않는다 — 분자와 겹친다.
 *
 * <p>월 소득이 없으면 가용금액을 만들지 않는다. 고정지출은 없으면 0으로 두고, 약정액이 없는 정상 적금은 0으로
 * 세되 각각 {@code notes}에 남겨 화면이 근거 부족을 알 수 있게 한다.
 */
@Component
@Transactional(readOnly = true)
public class InstallmentAvailableFundsReader {

    private final FinancialProfileRepository profiles;
    private final SavingsQueryRepository savings;
    private final LoanQueryService loans;

    public InstallmentAvailableFundsReader(
            FinancialProfileRepository profiles, SavingsQueryRepository savings, LoanQueryService loans
    ) {
        this.profiles = profiles;
        this.savings = savings;
        this.loans = loans;
    }

    public AvailableAmountBasis read(Long userId) {
        List<String> notes = new ArrayList<>();
        FinancialProfile profile = profiles.findById(userId).orElse(null);
        Long income = profile == null ? null : profile.getMonthlyIncome();
        if (income == null) {
            notes.add(AvailableAmountBasis.INCOME_UNKNOWN);
        }
        Long fixedExpense = profile == null ? null : profile.getFixedExpense();
        if (fixedExpense == null) {
            notes.add(AvailableAmountBasis.FIXED_EXPENSE_UNKNOWN);
        }

        long monthlySaving = 0;
        boolean savingAmountMissing = false;
        for (SavingsAccountRow account : savings.findAllOwned(userId)) {
            if (!"ACTIVE".equals(account.status())) {
                continue;
            }
            if (account.monthlyAmount() == null) {
                savingAmountMissing = true;
                continue;
            }
            monthlySaving = Math.addExact(monthlySaving, account.monthlyAmount());
        }
        if (savingAmountMissing) {
            notes.add(AvailableAmountBasis.SAVING_MONTHLY_AMOUNT_MISSING);
        }

        long monthlyLoanRepayment = loans.getSummary(userId).monthlyRepayment();

        return new AvailableAmountBasis(
                income, fixedExpense == null ? 0L : fixedExpense, monthlySaving, monthlyLoanRepayment, notes);
    }
}
