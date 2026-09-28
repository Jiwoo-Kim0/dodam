package com.ssafy.dodam.domain.installment.service;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentBurdenResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentDetailResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentScheduleResponse;
import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import com.ssafy.dodam.domain.installment.repository.InstallmentReader;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.FutureYearMonthPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InstallmentQueryService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private final InstallmentReader reader;
    private final FutureYearMonthPeriodPolicy periodPolicy;
    private final InstallmentScheduleCalculator calculator;
    private final InstallmentAvailableFundsReader availableFundsReader;
    private final InstallmentBurdenCalculator burdenCalculator;

    public InstallmentQueryService(
            InstallmentReader reader,
            FutureYearMonthPeriodPolicy periodPolicy,
            InstallmentScheduleCalculator calculator,
            InstallmentAvailableFundsReader availableFundsReader,
            InstallmentBurdenCalculator burdenCalculator
    ) {
        this.reader = reader;
        this.periodPolicy = periodPolicy;
        this.calculator = calculator;
        this.availableFundsReader = availableFundsReader;
        this.burdenCalculator = burdenCalculator;
    }

    public PageResponse<InstallmentResponse> getInstallments(
            Long userId, Long cardId, String status, int page, int size
    ) {
        InstallmentStatus parsedStatus = parseStatus(status);
        if (cardId != null && !reader.ownsCard(userId, cardId)) {
            throw new ResourceNotFoundException("카드를 찾을 수 없습니다.");
        }
        return PageResponse.from(
                reader.readPage(userId, cardId, parsedStatus, PageRequest.of(page, size)),
                InstallmentResponse::from);
    }

    public InstallmentDetailResponse getInstallment(Long userId, Long installmentId) {
        return reader.readDetail(userId, installmentId)
                .map(InstallmentDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("할부를 찾을 수 없습니다."));
    }

    public InstallmentScheduleResponse getSchedule(Long userId, String startMonth, String endMonth) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        YearMonthPeriod period = periodPolicy.resolve(startMonth, endMonth, YearMonth.from(today));
        return calculator.calculate(reader.readOutstanding(userId), period, today);
    }

    public InstallmentScheduleResponse getForecast(Long userId, int months) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        YearMonthPeriod period = periodPolicy.forecast(months, YearMonth.from(today));
        return calculator.calculate(reader.readOutstanding(userId), period, today);
    }

    public InstallmentBurdenResponse getBurden(Long userId, String yearMonth) {
        LocalDate today = LocalDate.now(SERVICE_ZONE);
        // 부담률은 한 달 단위다. 일정 정책과 같은 창(현재 월부터 12개월)의 한 달만 계산한다.
        YearMonth month = periodPolicy.resolveMonth(yearMonth, YearMonth.from(today));
        InstallmentScheduleResponse schedule = calculator.calculate(
                reader.readOutstanding(userId), new YearMonthPeriod(month, month), today);
        return burdenCalculator.calculate(schedule, availableFundsReader.read(userId), today);
    }

    private InstallmentStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return InstallmentStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("할부 상태가 올바르지 않습니다.");
        }
    }
}
