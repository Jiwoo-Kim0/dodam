package com.ssafy.dodam.domain.auth.service;

import com.ssafy.dodam.domain.auth.dto.SocialLoginResult;
import com.ssafy.dodam.domain.auth.entity.UserOauthAccount;
import com.ssafy.dodam.domain.auth.entity.UserOauthAccount.ProviderEmail;
import com.ssafy.dodam.domain.auth.repository.UserOauthAccountRepository;
import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClients;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.service.FinanceMemberService;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserSetting;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 소셜 로그인(명세 외 확장, backend/docs/auth-social.md).
 *
 * <p>흐름: 공급자 토큰 검증(트랜잭션 밖) → 연결된 소셜 계정이 있으면 그 회원으로 로그인 → 없고 소유 확인된 이메일이 기존 회원과 같으면
 * 그 회원에 연결 → 아니면 새 회원을 만들고 연결. 세션·토큰 발급은 이메일 로그인과 같은 {@link AuthService#startSession}을 쓴다.
 *
 * <p>신규 회원의 이메일은 공급자가 소유를 확인한 이메일을 쓰고, 없으면 "공급자.해시앞20자@social.dodam.invalid" 대체 이메일을 쓴다.
 * 대체 이메일은 수신 불가한 값이라 금융망 회원 등록에는 쓰지 않는다(연결 상태 FAILED로 두고 Finance Data API로 나중에 연결).
 */
@Service
public class SocialAuthService {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);
    static final String SYNTHETIC_EMAIL_DOMAIN = "social.dodam.invalid";
    static final String DEFAULT_NAME = "도담 회원";

    private final SocialIdentityClients clients;
    private final UserOauthAccountRepository oauthAccounts;
    private final UserRepository users;
    private final UserSettingRepository userSettings;
    private final FinancialProfileRepository financialProfiles;
    private final FinanceConnectionRepository financeConnections;
    private final FinanceMemberService financeMemberService;
    private final FieldCryptor cryptor;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public SocialAuthService(
            SocialIdentityClients clients,
            UserOauthAccountRepository oauthAccounts,
            UserRepository users,
            UserSettingRepository userSettings,
            FinancialProfileRepository financialProfiles,
            FinanceConnectionRepository financeConnections,
            FinanceMemberService financeMemberService,
            FieldCryptor cryptor,
            AuthService authService,
            TransactionTemplate transactionTemplate
    ) {
        this.clients = clients;
        this.oauthAccounts = oauthAccounts;
        this.users = users;
        this.userSettings = userSettings;
        this.financialProfiles = financialProfiles;
        this.financeConnections = financeConnections;
        this.financeMemberService = financeMemberService;
        this.cryptor = cryptor;
        this.authService = authService;
        this.transactionTemplate = transactionTemplate;
    }

    public SocialLoginResult login(SocialProvider provider, String token) {
        SocialIdentity identity = clients.get(provider).verify(token.trim());
        String providerUserIdHash = cryptor.hash(identity.providerUserId());

        Optional<UserOauthAccount> linked = oauthAccounts.findByProviderAndProviderUserIdHash(provider, providerUserIdHash);
        if (linked.isPresent()) {
            return loginLinked(linked.get().getId(), identity);
        }

        Optional<User> sameEmailUser = identity.verifiedEmail().flatMap(users::findByEmailAndDeletedAtIsNull);
        if (sameEmailUser.isPresent()) {
            return linkExisting(sameEmailUser.get().getId(), identity, providerUserIdHash);
        }

        return signupAndLogin(identity, providerUserIdHash);
    }

    /** 이미 연결된 소셜 계정: 회원이 살아 있으면 로그인. 탈퇴했으면 403(이메일 UNIQUE 때문에 재가입도 불가). */
    private SocialLoginResult loginLinked(Long oauthAccountId, SocialIdentity identity) {
        return transactionTemplate.execute(status -> {
            UserOauthAccount account = oauthAccounts.findById(oauthAccountId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID));
            User user = users.findByIdAndDeletedAtIsNull(account.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_WITHDRAWN));
            account.touch(providerEmail(identity), LocalDateTime.now());
            return new SocialLoginResult(authService.startSession(user), false, identity.provider());
        });
    }

    /** 소유 확인된 이메일이 기존 회원과 같다: 그 회원에 소셜 계정을 연결하고 로그인. */
    private SocialLoginResult linkExisting(Long userId, SocialIdentity identity, String providerUserIdHash) {
        return transactionTemplate.execute(status -> {
            User user = users.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_WITHDRAWN));
            if (oauthAccounts.existsByUserIdAndProvider(user.getId(), identity.provider())) {
                throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
            }
            saveLink(user.getId(), identity, providerUserIdHash);
            return new SocialLoginResult(authService.startSession(user), false, identity.provider());
        });
    }

    /** 처음 온 소셜 계정: 회원·기본 설정·재무 프로필·금융망 연결 행을 만들고 연결한다(이메일 회원가입과 같은 구성). */
    private SocialLoginResult signupAndLogin(SocialIdentity identity, String providerUserIdHash) {
        Optional<String> verifiedEmail = identity.verifiedEmail().filter(email -> !users.existsByEmail(email));
        String email = verifiedEmail.orElseGet(() -> syntheticEmail(identity.provider(), providerUserIdHash));
        String name = displayName(identity);
        Optional<String> financeUserKey = verifiedEmail.isPresent() ? registerFinanceMember(email) : Optional.empty();

        return transactionTemplate.execute(status -> {
            User user = User.createSocialUser(email, name);
            try {
                users.saveAndFlush(user);
            } catch (DataIntegrityViolationException exception) {
                // 같은 이메일의 동시 가입. 이메일 회원가입과 같은 코드로 알린다.
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            userSettings.save(UserSetting.defaults(user.getId()));
            financialProfiles.save(FinancialProfile.empty(user.getId()));
            financeConnections.save(financeMemberService.buildConnection(user.getId(), email, financeUserKey));
            saveLink(user.getId(), identity, providerUserIdHash);
            log.info("Social signup: provider={} userId={} syntheticEmail={}",
                    identity.provider(), user.getId(), verifiedEmail.isEmpty());
            return new SocialLoginResult(authService.startSession(user), true, identity.provider());
        });
    }

    private void saveLink(Long userId, SocialIdentity identity, String providerUserIdHash) {
        try {
            oauthAccounts.saveAndFlush(UserOauthAccount.link(
                    userId, identity.provider(), providerUserIdHash, providerEmail(identity), LocalDateTime.now()));
        } catch (DataIntegrityViolationException exception) {
            // 같은 소셜 계정의 동시 로그인 또는 같은 회원·공급자 중복 연결. 재시도하면 연결된 계정으로 로그인된다.
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
    }

    /**
     * 금융망 회원 등록. 다른 서비스가 먼저 등록한 이메일(409)이어도 소셜 로그인은 막지 않고 연결 FAILED로 둔다.
     */
    private Optional<String> registerFinanceMember(String email) {
        try {
            return financeMemberService.registerOrFindUserKey(email);
        } catch (BusinessException exception) {
            log.info("Finance member registration skipped for social signup: {}", exception.getErrorCode());
            return Optional.empty();
        }
    }

    private ProviderEmail providerEmail(SocialIdentity identity) {
        return identity.anyEmail()
                .map(email -> new ProviderEmail(cryptor.encrypt(email), cryptor.hash(email), FieldCryptor.maskEmail(email)))
                .orElse(null);
    }

    static String syntheticEmail(SocialProvider provider, String providerUserIdHash) {
        return provider.syntheticEmailPrefix() + "." + providerUserIdHash.substring(0, 20) + "@" + SYNTHETIC_EMAIL_DOMAIN;
    }

    static String displayName(SocialIdentity identity) {
        String name = identity.name() == null ? "" : identity.name().trim();
        if (name.isEmpty()) {
            return DEFAULT_NAME;
        }
        return name.length() <= 100 ? name : name.substring(0, 100);
    }
}
