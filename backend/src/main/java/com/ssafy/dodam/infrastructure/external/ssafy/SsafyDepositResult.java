package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyDepositResult(String transactionUniqueNo, String transactionDate,
        String transactionTime, Long transactionBalance, Long transactionAfterBalance) {
}
