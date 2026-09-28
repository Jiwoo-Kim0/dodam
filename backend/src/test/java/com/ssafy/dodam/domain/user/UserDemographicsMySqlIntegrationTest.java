package com.ssafy.dodam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.service.CreditRatingService;
import com.ssafy.dodam.domain.user.dto.UpdateUserRequest;
import com.ssafy.dodam.domain.user.dto.UserResponse;
import com.ssafy.dodam.domain.user.entity.Gender;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.service.UserService;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRating;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 로컬 일회용 MySQL에서만 실행한다(일반 test는 건너뜀). H2가 검증하지 못하는 것(629, V39): `user.birth_date DATE`·
 * `gender VARCHAR(10)`·`credit_rating VARCHAR(5)`·`credit_rating_updated_at DATETIME`과 {@link User} 매핑의 `validate` 통과,
 * `chk_user_gender` CHECK가 DB 단계에서 살아 있는가, DATE·DATETIME이 실제 스키마에서 그대로 왕복하는가,
 * 신용등급 기록 경로({@link CreditRatingService#refresh})가 실제 스키마에 쓰는가.
 *
 * <p>실행 방법: V1~V39가 적용된 `dodam_516_test` 스키마의 MySQL 8.0을 띄우고 `PATTERN_MYSQL_TEST_URL`·`PATTERN_MYSQL_TEST_PASSWORD`를 설정한다
 * (Pattern·Notification·소셜 로그인 MySQL 테스트와 같은 DB를 공유한다).
 */
@EnabledIfEnvironmentVariable(
        named = "PATTERN_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_516_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
class UserDemographicsMySqlIntegrationTest {

    private static final long USER_ID = 629_001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 14, 30);
    private static final LocalDate BIRTH_DATE = LocalDate.of(1996, 3, 15);
    private static final String EMAIL_PREFIX = "demo629-";
    private static final String USER_KEY = "629-user-key";

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired UserService userService;
    @Autowired CreditRatingService creditRatingService;
    @Autowired FinanceConnectionRepository financeConnections;
    @Autowired FieldCryptor cryptor;
    @MockitoBean SsafyFinanceClient ssafyFinanceClient;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("PATTERN_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PATTERN_MYSQL_TEST_PASSWORD", ""));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.pattern.model-location", () -> "");
    }

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("INSERT INTO `user` (user_id, email, password_hash, name, role, created_at, updated_at) VALUES (?, ?, 'unused', ?, 'USER', ?, ?)",
                USER_ID, EMAIL_PREFIX + USER_ID + "@example.com", "도담 " + USER_ID, Timestamp.valueOf(NOW), Timestamp.valueOf(NOW));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 실제_스키마에서_생년월일_성별_매핑과_CHECK를_검증한다() {
        // V39 컬럼은 NULL로 시작한다(기존 회원·seed와 같은 상태)
        Map<String, Object> before = jdbc.queryForMap(
                "SELECT birth_date, gender, credit_rating, credit_rating_updated_at FROM `user` WHERE user_id = ?", USER_ID);
        assertThat(before.values()).containsOnlyNulls();

        UserResponse updated = userService.updateMe(USER_ID, new UpdateUserRequest(null, BIRTH_DATE, Gender.FEMALE));
        assertThat(updated.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(updated.gender()).isEqualTo(Gender.FEMALE);
        assertThat(updated.age()).isNotNull();

        // DATE·enum 문자열 왕복
        Map<String, Object> row = jdbc.queryForMap("SELECT birth_date, gender FROM `user` WHERE user_id = ?", USER_ID);
        assertThat(((Date) row.get("birth_date")).toLocalDate()).isEqualTo(BIRTH_DATE);
        assertThat(row.get("gender")).isEqualTo("FEMALE");

        // gender CHECK. MySQL 3819(CHECK 위반)는 Spring이 DataIntegrityViolation으로 번역하지 않아 UncategorizedSQLException으로 온다.
        assertThatThrownBy(() -> jdbc.update("UPDATE `user` SET gender = 'OTHER' WHERE user_id = ?", USER_ID))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_user_gender");
        // NULL은 CHECK를 통과한다(미입력 회원)
        jdbc.update("UPDATE `user` SET gender = NULL WHERE user_id = ?", USER_ID);
        assertThat(users.findById(USER_ID).orElseThrow().getGender()).isNull();
    }

    @Test
    void 신용등급_기록_경로가_실제_스키마에_쓴다() {
        financeConnections.save(FinanceConnection.connected(
                USER_ID,
                cryptor.encrypt(EMAIL_PREFIX + USER_ID + "@example.com"),
                cryptor.hash(EMAIL_PREFIX + USER_ID + "@example.com"),
                "de***@example.com",
                cryptor.encrypt(USER_KEY),
                cryptor.hash(USER_KEY),
                NOW
        ));
        given(ssafyFinanceClient.inquireMyCreditRating(anyString()))
                .willReturn(new SsafyCreditRating("B", 3_500_000L, 12_000_000L, 15_500_000L));

        assertThat(creditRatingService.refresh(USER_ID, USER_KEY)).contains("B");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT credit_rating, credit_rating_updated_at FROM `user` WHERE user_id = ?", USER_ID);
        assertThat(row.get("credit_rating")).isEqualTo("B");
        assertThat(row.get("credit_rating_updated_at")).isNotNull();
        assertThat(userService.getMe(USER_ID).creditRating()).isEqualTo("B");

        // 등급명이 비어 있으면 이전 값을 유지한다
        given(ssafyFinanceClient.inquireMyCreditRating(anyString()))
                .willReturn(new SsafyCreditRating(" ", 0L, 0L, 0L));
        assertThat(creditRatingService.refresh(USER_ID, USER_KEY)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT credit_rating FROM `user` WHERE user_id = ?", String.class, USER_ID)).isEqualTo("B");
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM finance_connection WHERE user_id IN (SELECT user_id FROM `user` WHERE email LIKE ?)", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM `user` WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }
}
