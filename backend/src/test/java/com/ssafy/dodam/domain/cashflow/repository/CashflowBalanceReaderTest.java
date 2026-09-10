package com.ssafy.dodam.domain.cashflow.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** 시작 잔액의 계좌 유형·상태 필터와 소유권 조건을 확인한다. */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:cashflow540balance;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CashflowBalanceReaderTest {

    private static final Long USER_ID = 5400001L;
    private static final Long OTHER_USER_ID = 5400002L;
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired private CashflowBalanceReader reader;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void returnsZeroWhenUserHasNoAccount() {
        assertThat(reader.readStartingBalance(9_999_999L)).isZero();
    }

    @Test
    void sumsOnlyActiveDemandDepositAccounts() {
        insertAccount(USER_ID, "DEMAND_DEPOSIT", "ACTIVE", 1_000_000L);
        insertAccount(USER_ID, "DEMAND_DEPOSIT", "ACTIVE", 500_000L);
        insertAccount(USER_ID, "DEMAND_DEPOSIT", "INACTIVE", 700_000L);
        insertAccount(USER_ID, "DEMAND_DEPOSIT", "CLOSED", 900_000L);
        // 만기 전 출금이 불가하거나(예·적금) 잔액이 곧 사용액인(마이너스) 계좌는 현금이 아니다.
        insertAccount(USER_ID, "SAVINGS", "ACTIVE", 3_000_000L);
        insertAccount(USER_ID, "DEPOSIT", "ACTIVE", 4_000_000L);
        insertAccount(USER_ID, "MINUS", "ACTIVE", -2_000_000L);

        assertThat(reader.readStartingBalance(USER_ID)).isEqualTo(1_500_000L);
    }

    @Test
    void excludesOtherUsersAccounts() {
        insertAccount(OTHER_USER_ID, "DEMAND_DEPOSIT", "ACTIVE", 8_000_000L);

        assertThat(reader.readStartingBalance(USER_ID + 500L)).isZero();
    }

    private void insertAccount(Long userId, String accountType, String status, long balance) {
        // H2 프로필은 엔티티로 스키마를 만든다. `account`에는 매핑된 컬럼만 있고(`account_no_encrypted`,
        // `source_type` 없음) `account_id`도 자동 증가가 아니므로 직접 채운다.
        long accountId = SEQUENCE.incrementAndGet();
        jdbcTemplate.update("""
                INSERT INTO account (account_id, user_id, bank_code, bank_name, account_no_hash,
                                     account_no_masked, account_name, account_type, status, balance,
                                     currency, created_at, updated_at)
                VALUES (?, ?, '001', 'TEST-BANK', ?, '110-***-1234', 'TEST-ACCOUNT', ?, ?, ?,
                        'KRW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, accountId, userId, hash(accountId), accountType, status, balance);
    }

    private String hash(long accountId) {
        return String.format("%064d", accountId);
    }
}
