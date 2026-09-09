package com.ssafy.dodam.domain.installment.dto.response;

import java.time.LocalDate;
import java.util.List;

public record InstallmentScheduleResponse(
        LocalDate asOfDate,
        String startMonth,
        String endMonth,
        boolean complete,
        long excludedInstallmentCount,
        List<InstallmentExclusionResponse> exclusions,
        List<InstallmentMonthResponse> months
) {
    public InstallmentScheduleResponse {
        exclusions = List.copyOf(exclusions);
        months = List.copyOf(months);
    }
}
