package com.ssafy.dodam.domain.depositsaving.service;

import com.ssafy.dodam.domain.deposit.dto.response.DepositMaturityForecastResponse;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountReader;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountRow;
import com.ssafy.dodam.domain.deposit.service.DepositForecastService;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingMaturityForecastResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsForecastResponse;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.domain.savings.service.SavingsForecastCalculator;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * accountId 기준 만기 예상금액 (S15P21D105-678).
 *
 * <p>기존 {@code /api/v1/deposits/{accountNo}/maturity-forecast}·{@code /api/v1/savings/{accountNo}/maturity-forecast}는
 * 계좌번호 원문을 경로로 받는데, FE는 목록 응답의 {@code accountId}·마스킹 번호만 갖고 원문을 보관하지 않는다.
 * 그래서 목록에서 바로 이어지는 조회 경로를 하나 더 두되 계산은 예금·적금 서비스의 것을 그대로 쓴다.
 * 두 원문 경로는 유지한다.
 *
 * <p>없는 계좌, 타인 계좌, 예·적금이 아닌 계좌는 모두 같은 {@code RESOURCE_NOT_FOUND}다. 셋을 구분하면 응답만으로
 * 타인 계좌 ID의 존재를 확인할 수 있게 된다.
 */
@Service
@Transactional(readOnly = true)
public class DepositSavingForecastService {
    private static final String SAVINGS = "SAVINGS";

    private final DepositAccountReader deposits;
    private final DepositForecastService depositForecasts;
    private final SavingsQueryRepository savings;
    private final SavingsForecastCalculator savingsForecasts;
    private final DepositSavingClock clock;

    public DepositSavingForecastService(
            DepositAccountReader deposits, DepositForecastService depositForecasts,
            SavingsQueryRepository savings, SavingsForecastCalculator savingsForecasts,
            DepositSavingClock clock
    ) {
        this.deposits = deposits;
        this.depositForecasts = depositForecasts;
        this.savings = savings;
        this.savingsForecasts = savingsForecasts;
        this.clock = clock;
    }

    public DepositSavingMaturityForecastResponse maturityForecast(Long userId, Long accountId) {
        // 한 요청 안에서 두 도메인의 기준일이 어긋나지 않도록 여기서 한 번만 읽어 내려보낸다.
        LocalDate today = clock.today();
        Optional<DepositAccountRow> deposit = deposits.findOwnedDepositById(userId, accountId);
        if (deposit.isPresent()) {
            return fromDeposit(depositForecasts.maturityForecast(deposit.get(), today), deposit.get());
        }
        SavingsAccountRow account = savings.findOwnedById(userId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("예적금 계좌를 찾을 수 없습니다."));
        SavingsForecastResponse forecast = savingsForecasts.calculate(
                account, savings.findPayments(userId), today, false);
        return fromSavings(forecast, account, today);
    }

    private static DepositSavingMaturityForecastResponse fromDeposit(
            DepositMaturityForecastResponse forecast, DepositAccountRow account
    ) {
        boolean complete = "COMPLETE".equals(forecast.status());
        return new DepositSavingMaturityForecastResponse(
                forecast.asOfDate(), account.accountId(), account.accountName(), account.accountNoMasked(),
                account.bankCode(), DepositAccountReader.DEPOSIT, forecast.status(),
                forecast.unavailableReasons(), forecast.assumptions(),
                account.balance(), account.interestRate(), account.openingDate(), account.expiryDate(),
                forecast.daysUntilMaturity(),
                complete ? account.balance() : null, forecast.grossInterest(), forecast.tax(),
                forecast.netInterest(), forecast.estimatedPayoutAmount());
    }

    private static DepositSavingMaturityForecastResponse fromSavings(
            SavingsForecastResponse forecast, SavingsAccountRow account, LocalDate today
    ) {
        // 적금 응답의 paidPrincipal은 계산 불가일 때 null일 수 있어 원금은 계좌 잔액(납입 원금 합 규약)으로 답한다.
        return new DepositSavingMaturityForecastResponse(
                forecast.asOfDate(), account.accountId(), account.accountName(), account.accountNoMasked(),
                account.bankCode(), SAVINGS, forecast.status(), forecast.reasons(), forecast.assumptions(),
                account.balance(), account.interestRate(), account.openingDate(), account.expiryDate(),
                account.expiryDate() == null ? null : ChronoUnit.DAYS.between(today, account.expiryDate()),
                forecast.expectedPrincipal(), forecast.expectedInterest(), forecast.tax(),
                forecast.expectedNetInterest(), forecast.expectedAmount());
    }
}
