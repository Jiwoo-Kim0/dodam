package com.ssafy.dodam.domain.installment.repository;

import com.ssafy.dodam.domain.installment.entity.InstallmentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInstallmentReader implements InstallmentReader {

    private static final String COLUMNS = """
            SELECT i.installment_id, i.card_id, i.merchant_name, i.total_amount, i.months,
                   i.remaining_principal, i.remaining_months, i.monthly_amount, i.fee_rate,
                   i.purchase_date, i.first_payment_date, i.next_payment_date, i.status
            """;
    private static final String OWNED_FROM = """
            FROM installment i
            JOIN card c ON c.card_id = i.card_id
            WHERE c.user_id = :userId
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcInstallmentReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean ownsCard(Long userId, Long cardId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM card WHERE user_id = :userId AND card_id = :cardId",
                Map.of("userId", userId, "cardId", cardId), Long.class);
        return count != null && count > 0;
    }

    @Override
    public Page<InstallmentRow> readPage(
            Long userId, Long cardId, InstallmentStatus status, Pageable pageable
    ) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("userId", userId);
        String filter = OWNED_FROM;
        if (cardId != null) {
            filter += " AND i.card_id = :cardId";
            parameters.put("cardId", cardId);
        }
        if (status != null) {
            filter += " AND i.status = :status";
            parameters.put("status", status.name());
        }
        long total = Objects.requireNonNullElse(
                jdbcTemplate.queryForObject("SELECT COUNT(*) " + filter, parameters, Long.class), 0L);
        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        parameters.put("limit", pageable.getPageSize());
        parameters.put("offset", pageable.getOffset());
        List<InstallmentRow> rows = jdbcTemplate.query(
                COLUMNS + filter + " ORDER BY i.purchase_date DESC, i.installment_id DESC"
                        + " LIMIT :limit OFFSET :offset",
                parameters, JdbcInstallmentReader::mapRow);
        return new PageImpl<>(rows, pageable, total);
    }

    @Override
    public Optional<InstallmentRow> readDetail(Long userId, Long installmentId) {
        return jdbcTemplate.query(
                COLUMNS + OWNED_FROM + " AND i.installment_id = :installmentId",
                Map.of("userId", userId, "installmentId", installmentId),
                JdbcInstallmentReader::mapRow).stream().findFirst();
    }

    @Override
    public List<InstallmentRow> readOutstanding(Long userId) {
        // 기간을 판별할 수 없는 연체·누락 건도 읽어 합계의 불완전 여부를 알린다.
        return jdbcTemplate.query(
                COLUMNS + OWNED_FROM + " AND i.status IN ('ACTIVE', 'OVERDUE')",
                Map.of("userId", userId), JdbcInstallmentReader::mapRow);
    }

    private static InstallmentRow mapRow(ResultSet rs, int rowNumber) throws SQLException {
        return new InstallmentRow(
                rs.getLong("installment_id"), rs.getLong("card_id"),
                rs.getString("merchant_name"), rs.getLong("total_amount"), rs.getInt("months"),
                rs.getObject("remaining_principal", Long.class), rs.getInt("remaining_months"),
                rs.getLong("monthly_amount"), rs.getBigDecimal("fee_rate"),
                rs.getObject("purchase_date", LocalDate.class),
                rs.getObject("first_payment_date", LocalDate.class),
                rs.getObject("next_payment_date", LocalDate.class),
                InstallmentStatus.valueOf(rs.getString("status")));
    }
}
