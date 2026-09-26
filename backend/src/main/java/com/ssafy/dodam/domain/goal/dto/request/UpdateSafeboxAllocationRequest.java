package com.ssafy.dodam.domain.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateSafeboxAllocationRequest(@NotNull @PositiveOrZero Long amount) {
}
