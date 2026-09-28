package com.ssafy.dodam.domain.deposit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 예금 계좌 조회 경계.
 *
 * <p>535와 같은 이유로 Account 소관 엔티티를 중복 생성하지 않고 필요한 컬럼만 JDBC로 읽는다.
 *
 * <p><b>소유권은 조회 조건에 넣는다.</b> user_id와 account_no_hash와 account_type을 함께 걸어 타인 계좌,
 * 없는 계좌, 예금이 아닌 계좌가 모두 결과 0건이 되게 한다. 조회 후 소유자를 비교하는 방식은 쓰지 않는다.
 * 세 경우를 구분하지 않아야 계좌 존재 여부가 응답으로 새지 않는다.
 */
@Repository
public class DepositAccountReader {
    /** 예금 도메인이 다루는 유일한 account_type. */
    public static final String DEPOSIT = "DEPOSIT";

    private static final String SELECT_DEPOSIT = """
            SELECT a.account_id, a.account_name, a.bank_code, a.bank_name, a.account_no_masked,
                   a.status, a.balance, d.interest_rate, d.financial_product_id,
                   d.opening_date, d.expiry_date, d.account_id AS detail_id
            FROM account a
            LEFT JOIN deposit_saving d
              ON d.account_id = a.account_id AND d.user_id = a.user_id
                 AND d.account_type = a.account_type
            WHERE a.user_id = :userId AND a.account_type = '""" + DEPOSIT + "'";
    private static final String BY_HASH = " AND a.account_no_hash = :accountNoHash";
    private static final String BY_ID = " AND a.account_id = :accountId";

    private final NamedParameterJdbcTemplate jdbc;

    public DepositAccountReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DepositAccountRow> findOwnedDeposit(Long userId, String accountNoHash) {
        return findOwned(SELECT_DEPOSIT + BY_HASH,
                new MapSqlParameterSource("userId", userId).addValue("accountNoHash", accountNoHash));
    }

    /**
     * 678: 계좌번호 원문이 없는 FE(목록의 accountId만 보유)를 위한 조회. 소유권 조건은 해시 조회와 같다.
     */
    public Optional<DepositAccountRow> findOwnedDepositById(Long userId, Long accountId) {
        return findOwned(SELECT_DEPOSIT + BY_ID,
                new MapSqlParameterSource("userId", userId).addValue("accountId", accountId));
    }

    private Optional<DepositAccountRow> findOwned(String sql, MapSqlParameterSource params) {
        List<DepositAccountRow> rows = jdbc.query(
                sql,
                params,
                (rs, rowNum) -> new DepositAccountRow(
                        rs.getLong("account_id"), rs.getString("account_name"),
                        rs.getString("bank_code"), rs.getString("bank_name"),
                        rs.getString("account_no_masked"), rs.getString("status"),
                        rs.getLong("balance"), rs.getBigDecimal("interest_rate"),
                        rs.getObject("financial_product_id", Long.class),
                        rs.getObject("opening_date", LocalDate.class),
                        rs.getObject("expiry_date", LocalDate.class),
                        rs.getObject("detail_id") != null));
        // uk_account_user_account_hash가 (user_id, account_no_hash)를, PK가 account_id를 유일하게 만들고 상세는 1:1이다.
        return rows.stream().findFirst();
    }
}
