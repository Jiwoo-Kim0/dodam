package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyTransferEntry(String transactionUniqueNo, String transactionDate,
        String transactionTime, String transactionType, Long transactionBalance,
        Long transactionAfterBalance, String transactionSummary) {
}
