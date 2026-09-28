package com.ssafy.dodam.domain.installment.dto.response;

import com.ssafy.dodam.domain.installment.entity.InstallmentExclusionReason;

public record InstallmentExclusionResponse(InstallmentExclusionReason reason, long count) {
}
