package com.ssafy.dodam.domain.finance.service;

import com.ssafy.dodam.domain.finance.dto.FinanceSummaryResponse;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceSummaryReader;
import com.ssafy.dodam.domain.finance.repository.FinanceSummaryReader.AccountTypeSummaryRow;
import com.ssafy.dodam.domain.finance.repository.FinanceSummaryReader.CardSummaryRow;
import com.ssafy.dodam.domain.finance.repository.FinanceSummaryReader.LoanSummaryRow;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 명세 4. Finance Data — 금융 데이터 통합 요약 (S15P21D105-500).
 * 금융망을 호출하지 않고 로컬 DB만 집계하므로 연결 상태와 무관하게 응답한다 (미연결이면 연결 상태로 알 수 있다).
 */
@Service
public class FinanceSummaryService {

    private static final String DEMAND_DEPOSIT = "DEMAND_DEPOSIT";
    private static final String MINUS = "MINUS";
    private static final String DEPOSIT = "DEPOSIT";
    private static final String SAVINGS = "SAVINGS";

    private final UserRepository userRepository;
    private final FinanceConnectionRepository financeConnectionRepository;
    private final FinanceSummaryReader summaryReader;

    public FinanceSummaryService(
            UserRepository userRepository,
            FinanceConnectionRepository financeConnectionRepository,
            FinanceSummaryReader summaryReader
    ) {
        this.userRepository = userRepository;
        this.financeConnectionRepository = financeConnectionRepository;
        this.summaryReader = summaryReader;
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse getSummary(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));

        Optional<FinanceConnection> connection = financeConnectionRepository.findByUserId(userId);
        FinanceSummaryResponse.Connection connectionSummary = new FinanceSummaryResponse.Connection(
                connection.map(FinanceConnection::getStatus).orElse(FinanceConnectionStatus.PENDING),
                connection.map(FinanceConnection::getLastSyncedAt).orElse(null)
        );

        FinanceSummaryResponse.Accounts accounts = summarizeAccounts(summaryReader.readAccountsByType(userId));
        CardSummaryRow cards = summaryReader.readCards(userId);
        LoanSummaryRow loans = summaryReader.readLoans(userId);

        long cashAssets = accounts.demandDepositBalance() + accounts.depositBalance() + accounts.savingsBalance();
        long liabilities = loans.totalBalance() + accounts.minusUsedAmount();

        return new FinanceSummaryResponse(
                connectionSummary,
                accounts,
                new FinanceSummaryResponse.Cards(cards.activeCount(), cards.totalCount(), cards.totalCreditLimit()),
                new FinanceSummaryResponse.Loans(loans.outstandingCount(), loans.overdueCount(), loans.totalBalance()),
                new FinanceSummaryResponse.Totals(cashAssets, liabilities, cashAssets - liabilities)
        );
    }

    private static FinanceSummaryResponse.Accounts summarizeAccounts(List<AccountTypeSummaryRow> rows) {
        long count = 0;
        long demandDeposit = 0;
        long deposit = 0;
        long savings = 0;
        long minusCount = 0;
        long minusUsed = 0;
        for (AccountTypeSummaryRow row : rows) {
            count += row.count();
            switch (row.accountType()) {
                case DEMAND_DEPOSIT -> demandDeposit += row.totalBalance();
                case DEPOSIT -> deposit += row.totalBalance();
                case SAVINGS -> savings += row.totalBalance();
                case MINUS -> {
                    minusCount += row.count();
                    // 마이너스통장 balance는 -used_amount로 저장된다 (ERD 무결성 규칙)
                    minusUsed += Math.abs(row.totalBalance());
                }
                default -> {
                    // ERD CHECK에 없는 값은 합계에 넣지 않고 건수만 센다
                }
            }
        }
        return new FinanceSummaryResponse.Accounts(count, demandDeposit, deposit, savings, minusCount, minusUsed);
    }
}
