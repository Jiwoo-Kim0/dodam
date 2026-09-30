package com.ssafy.dodam.domain.transaction.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.transaction.repository.TransactionReader.TransactionFilter;
import com.ssafy.dodam.global.policy.QueryPeriod;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcTransactionReaderPaginationTest {

    private final NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
    private final JdbcTransactionReader reader = new JdbcTransactionReader(jdbc);
    private final TransactionFilter filter = new TransactionFilter(
            new QueryPeriod(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), null, null, false);

    @Test
    void partialPageInfersExactTotalWithoutCountQuery() {
        TransactionRow row = new TransactionRow("TX-1", null, null, null, null, null,
                LocalDate.of(2026, 9, 1), null, "EXPENSE", 1000, null, null, null, null,
                null, null, false, "SEED", null, null);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));

        var result = reader.read(1L, filter, PageRequest.of(2, 100));

        assertThat(result.getTotalElements()).isEqualTo(201);
        assertThat(result.isLast()).isTrue();
        verify(jdbc, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    @Test
    void emptyOutOfRangePageStillCountsExactTotal() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(5L);

        var result = reader.read(1L, filter, PageRequest.of(3, 2));

        assertThat(result.getTotalElements()).isEqualTo(5);
        verify(jdbc).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }
}
