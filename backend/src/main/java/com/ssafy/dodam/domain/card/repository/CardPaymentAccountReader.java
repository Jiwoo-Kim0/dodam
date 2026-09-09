package com.ssafy.dodam.domain.card.repository;

import java.util.Optional;

public interface CardPaymentAccountReader {

    Optional<CardPaymentAccountRow> findActiveDemandDeposit(Long userId, String accountNoHash);
}
