package com.ssafy.dodam.domain.card.dto.response;

import java.time.LocalDate;
import java.util.List;

public record CardPaymentSchedulesResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalScheduledAmount,
        List<Schedule> schedules
) {

    public CardPaymentSchedulesResponse {
        schedules = List.copyOf(schedules);
    }

    public record Schedule(
            String cardNoMasked,
            String cardName,
            String billingMonth,
            LocalDate billingDate,
            long scheduledAmount,
            String status
    ) {
    }
}
