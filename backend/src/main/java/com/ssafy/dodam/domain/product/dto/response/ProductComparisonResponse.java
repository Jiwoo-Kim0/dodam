package com.ssafy.dodam.domain.product.dto.response;

import java.util.List;

public record ProductComparisonResponse<T>(
        List<T> products
) {

    public ProductComparisonResponse {
        products = List.copyOf(products);
    }
}
