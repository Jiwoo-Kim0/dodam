package com.ssafy.dodam.domain.installment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse.AvailableAmountBasis;
import com.ssafy.dodam.domain.loan.dto.response.LoanSummaryResponse;
import com.ssafy.dodam.domain.loan.service.LoanQueryService;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstallmentAvailableFundsReaderTest {

    private final FinancialProfileRepository profiles = mock(FinancialProfileRepository.class);
    private final SavingsQueryRepository savings = mock(SavingsQueryRepository.class);
    private final LoanQueryService loans = mock(LoanQueryService.class);
    private final InstallmentAvailableFundsReader reader =
            new InstallmentAvailableFundsReader(profiles, savings, loans);

    @Test
    void sumsActiveSavingsAndLoanRepaymentWithProfileFigures() {
        FinancialProfile profile = FinancialProfile.empty(1L);
        profile.update(1_000_000L, null, null, 300_000L, null);
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        when(savings.findAllOwned(1L)).thenReturn(List.of(
                savings(1, "ACTIVE", 100_000L),
                savings(2, "ACTIVE", 50_000L),
                savings(3, "CLOSED", 999_999L)));
        when(loans.getSummary(1L)).thenReturn(summary(70_000));

        AvailableAmountBasis basis = reader.read(1L);

        assertThat(basis.monthlyIncome()).isEqualTo(1_000_000);
        assertThat(basis.fixedExpense()).isEqualTo(300_000);
        assertThat(basis.monthlySaving()).isEqualTo(150_000);
        assertThat(basis.monthlyLoanRepayment()).isEqualTo(70_000);
        assertThat(basis.notes()).isEmpty();
    }

    @Test
    void notesMissingProfileAndSavingsAmountsInsteadOfGuessing() {
        when(profiles.findById(anyLong())).thenReturn(Optional.empty());
        when(savings.findAllOwned(9L)).thenReturn(List.of(
                savings(1, "ACTIVE", null),
                savings(2, "ACTIVE", 20_000L)));
        when(loans.getSummary(9L)).thenReturn(summary(0));

        AvailableAmountBasis basis = reader.read(9L);

        assertThat(basis.monthlyIncome()).isNull();
        assertThat(basis.fixedExpense()).isZero();
        assertThat(basis.monthlySaving()).isEqualTo(20_000);
        assertThat(basis.notes()).containsExactly(
                AvailableAmountBasis.INCOME_UNKNOWN,
                AvailableAmountBasis.FIXED_EXPENSE_UNKNOWN,
                AvailableAmountBasis.SAVING_MONTHLY_AMOUNT_MISSING);
    }

    private SavingsAccountRow savings(long id, String status, Long monthlyAmount) {
        return new SavingsAccountRow(id, "적금", "***", "004", "KB", status, 0, monthlyAmount != null,
                BigDecimal.ONE, monthlyAmount, 10, null, null, null, null);
    }

    private LoanSummaryResponse summary(long monthlyRepayment) {
        return new LoanSummaryResponse(0, 0, 0, monthlyRepayment, null, null, List.of());
    }
}
