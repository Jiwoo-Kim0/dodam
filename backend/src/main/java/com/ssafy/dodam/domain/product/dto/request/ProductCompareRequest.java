package com.ssafy.dodam.domain.product.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ProductCompareRequest(
        @NotNull
        @Size(min = 2, max = 5)
        List<@NotNull @Positive Long> productIds
) {
}
