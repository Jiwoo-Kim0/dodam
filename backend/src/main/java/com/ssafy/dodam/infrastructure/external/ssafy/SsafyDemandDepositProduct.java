package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyDemandDepositProduct(String accountTypeUniqueNo, String bankCode,
        String bankName, String accountTypeName, String accountTypeDescription) {
}
