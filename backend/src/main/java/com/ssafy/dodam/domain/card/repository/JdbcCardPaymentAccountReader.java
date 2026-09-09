package com.ssafy.dodam.domain.card.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardPaymentAccountReader implements CardPaymentAccountReader {

    private static final String SQL = """
            SELECT account_id, bank_code, bank_name, account_name,
                   account_no_masked, account_type
            FROM account
            WHERE user_id = :userId
              AND account_no_hash = :accountNoHash
              AND account_type = 'DEMAND_DEPOSIT'
              AND status = 'ACTIVE'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCardPaymentAccountReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<CardPaymentAccountRow> findActiveDemandDeposit(
            Long userId,
            String accountNoHash
    ) {
        List<CardPaymentAccountRow> rows = jdbcTemplate.query(
                SQL,
                Map.of("userId", userId, "accountNoHash", accountNoHash),
                (resultSet, rowNumber) -> new CardPaymentAccountRow(
                        resultSet.getLong("account_id"),
                        resultSet.getString("bank_code"),
                        resultSet.getString("bank_name"),
                        resultSet.getString("account_name"),
                        resultSet.getString("account_no_masked"),
                        resultSet.getString("account_type")
                )
        );
        return rows.stream().findFirst();
    }
}
