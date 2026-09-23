package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyAccountBalance(String accountNo, Long accountBalance) {
}
