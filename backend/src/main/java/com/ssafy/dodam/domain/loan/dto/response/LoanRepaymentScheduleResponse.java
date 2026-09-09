package com.ssafy.dodam.domain.loan.dto.response;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentSchedule;
import java.util.List;

/**
 * 향후 상환일정. 만기까지의 회차를 계산해 반환하며 상한을 넘으면 `truncated`가 true다.
 *
 * <p>만기일이나 상환일이 없어 회차를 특정할 수 없는 대출은 빈 목록을 반환한다.
 */
public record LoanRepaymentScheduleResponse(
        String accountNoMasked,
        String repaymentType,
        int scheduleCount,
        long totalPrincipal,
        long totalInterest,
        boolean truncated,
        List<LoanRepaymentScheduleItemResponse> schedules
) {

    public LoanRepaymentScheduleResponse {
        schedules = List.copyOf(schedules);
    }

    public static LoanRepaymentScheduleResponse from(Loan loan, LoanRepaymentSchedule schedule) {
        return new LoanRepaymentScheduleResponse(
                loan.getAccountNoMasked(),
                loan.getRepaymentType().name(),
                schedule.items().size(),
                schedule.totalPrincipal(),
                schedule.totalInterest(),
                schedule.truncated(),
                schedule.items().stream()
                        .map(LoanRepaymentScheduleItemResponse::from)
                        .toList()
        );
    }
}
