package com.ssafy.dodam.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ssafy.dodam.domain.auth.dto.SocialLoginResult;
import com.ssafy.dodam.domain.auth.entity.UserOauthAccount;
import com.ssafy.dodam.domain.auth.entity.UserOauthAccount.ProviderEmail;
import com.ssafy.dodam.domain.auth.repository.UserOauthAccountRepository;
import com.ssafy.dodam.domain.auth.service.SocialAuthService;
import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClient;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClients;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 로컬 일회용 MySQL에서만 실행한다(일반 test는 건너뜀). H2가 검증하지 못하는 것: V34 DDL과 {@link UserOauthAccount} 매핑(`VARCHAR(30)` enum,
 * `CHAR(64)`, `VARBINARY(512)`)의 `validate` 통과, 두 UNIQUE 제약과 provider CHECK가 DB 단계에서 살아 있는가, 소셜 가입 경로가 실제 스키마에서
 * user·user_setting·financial_profile·finance_connection·user_oauth_account를 함께 만드는가.
 *
 * <p>실행 방법: V1~V34가 적용된 `dodam_516_test` 스키마의 MySQL 8.0을 띄우고 `PATTERN_MYSQL_TEST_URL`·`PATTERN_MYSQL_TEST_PASSWORD`를 설정한다
 * (Pattern·Notification MySQL 테스트와 같은 DB를 공유한다).
 */
@EnabledIfEnvironmentVariable(
        named = "PATTERN_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_516_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
class SocialLoginMySqlIntegrationTest {

    private static final long USER_ID = 611_001L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final String EMAIL_PREFIX = "social611-";

    @Autowired JdbcTemplate jdbc;
    @Autowired UserOauthAccountRepository oauthAccounts;
    @Autowired SocialAuthService socialAuthService;
    @Autowired FieldCryptor cryptor;

    @MockitoBean SocialIdentityClients clients;
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
                USER_ID, EMAIL_PREFIX + USER_ID + "@example.com", "소셜 " + USER_ID, Timestamp.valueOf(NOW), Timestamp.valueOf(NOW));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 실제_스키마에서_매핑_유일제약_CHECK를_검증한다() {
        String hash = cryptor.hash("kakao-611-a");
        ProviderEmail email = new ProviderEmail(cryptor.encrypt("dodam@kakao.com"), cryptor.hash("dodam@kakao.com"), "do***@kakao.com");
        UserOauthAccount saved = oauthAccounts.saveAndFlush(UserOauthAccount.link(USER_ID, SocialProvider.KAKAO, hash, email, NOW));

        // 매핑: enum 문자열, CHAR(64), VARBINARY 왕복
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT provider, CHAR_LENGTH(provider_user_id_hash) AS hash_len, provider_email_masked, created_at, updated_at "
                        + "FROM user_oauth_account WHERE oauth_account_id = ?", saved.getId());
        assertThat(row.get("provider")).isEqualTo("KAKAO");
        assertThat(((Number) row.get("hash_len")).intValue()).isEqualTo(64);
        assertThat(row.get("provider_email_masked")).isEqualTo("do***@kakao.com");
        assertThat(row.get("updated_at")).isNull();
        UserOauthAccount reloaded = oauthAccounts.findByProviderAndProviderUserIdHash(SocialProvider.KAKAO, hash).orElseThrow();
        assertThat(cryptor.decrypt(reloaded.getProviderEmailEncrypted())).isEqualTo("dodam@kakao.com");

        // (provider, provider_user_id_hash) UNIQUE
        assertThatThrownBy(() -> oauthAccounts.saveAndFlush(
                UserOauthAccount.link(USER_ID + 1, SocialProvider.KAKAO, hash, null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // (user_id, provider) UNIQUE
        assertThatThrownBy(() -> oauthAccounts.saveAndFlush(
                UserOauthAccount.link(USER_ID, SocialProvider.KAKAO, cryptor.hash("kakao-611-b"), null, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 다른 공급자는 같은 사용자에 연결 가능
        oauthAccounts.saveAndFlush(UserOauthAccount.link(USER_ID, SocialProvider.GOOGLE, cryptor.hash("google-611-a"), null, NOW));
        assertThat(oauthAccounts.findAllByUserIdOrderByIdAsc(USER_ID)).hasSize(2);

        // provider CHECK. MySQL 3819(CHECK 위반)는 Spring이 DataIntegrityViolation으로 번역하지 않아 UncategorizedSQLException으로 온다.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_oauth_account (user_id, provider, provider_user_id_hash, created_at) VALUES (?, 'NAVER', ?, ?)",
                USER_ID, cryptor.hash("naver-611"), Timestamp.valueOf(NOW)))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_user_oauth_account_provider");
    }

    @Test
    void 소셜_가입_경로가_실제_스키마에서_회원_구성_전체를_만든다() {
        SocialIdentityClient google = mock(SocialIdentityClient.class);
        given(clients.get(SocialProvider.GOOGLE)).willReturn(google);
        given(google.verify("id-token")).willReturn(
                new SocialIdentity(SocialProvider.GOOGLE, "sub-611", EMAIL_PREFIX + "new@example.com", true, "구글 도담"));
        // 금융망 장애 상황: userKey를 못 받아도 소셜 로그인은 막지 않고 연결만 FAILED로 남긴다.
        given(ssafyFinanceClient.createMember(anyString())).willThrow(new SsafyUnavailableException("finance down", null));

        SocialLoginResult first = socialAuthService.login(SocialProvider.GOOGLE, "id-token");
        SocialLoginResult second = socialAuthService.login(SocialProvider.GOOGLE, "id-token");

        assertThat(first.newUser()).isTrue();
        assertThat(second.newUser()).isFalse();
        long userId = first.login().user().userId();
        assertThat(second.login().user().userId()).isEqualTo(userId);
        assertThat(jdbc.queryForObject("SELECT password_hash IS NULL FROM `user` WHERE user_id = ?", Boolean.class, userId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_setting WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_profile WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM finance_connection WHERE user_id = ?", String.class, userId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_oauth_account WHERE user_id = ? AND provider = 'GOOGLE'", Integer.class, userId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT updated_at FROM user_oauth_account WHERE user_id = ?", Timestamp.class, userId)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE user_id = ?", Integer.class, userId)).isEqualTo(2);
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM user_oauth_account WHERE user_id IN (SELECT user_id FROM `user` WHERE email LIKE ?)", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM auth_session WHERE user_id IN (SELECT user_id FROM `user` WHERE email LIKE ?)", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM finance_connection WHERE user_id IN (SELECT user_id FROM `user` WHERE email LIKE ?)", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM financial_profile WHERE user_id IN (SELECT user_id FROM `user` WHERE email LIKE ?)", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM user_setting WHERE user_id IN (SELECT user_id FROM `user` WHERE email LIKE ?)", EMAIL_PREFIX + "%");
        jdbc.update("DELETE FROM `user` WHERE email LIKE ?", EMAIL_PREFIX + "%");
    }
}
