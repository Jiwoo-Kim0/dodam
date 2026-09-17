package com.ssafy.dodam.domain.auth.service;

import com.ssafy.dodam.domain.auth.dto.AuthUserResponse;
import com.ssafy.dodam.domain.auth.dto.LoginRequest;
import com.ssafy.dodam.domain.auth.dto.LoginResponse;
import com.ssafy.dodam.domain.auth.dto.RefreshRequest;
import com.ssafy.dodam.domain.auth.dto.SessionDeleteResponse;
import com.ssafy.dodam.domain.auth.dto.SessionResponse;
import com.ssafy.dodam.domain.auth.dto.SignupRequest;
import com.ssafy.dodam.domain.auth.dto.SignupResponse;
import com.ssafy.dodam.domain.auth.entity.AuthSession;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.service.FinanceMemberService;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserSetting;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.security.AuthPrincipal;
import com.ssafy.dodam.global.security.jwt.IssuedAccessToken;
import com.ssafy.dodam.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final FinanceConnectionRepository financeConnectionRepository;
    private final AuthSessionRepository authSessionRepository;
    private final FinanceMemberService financeMemberService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenFactory refreshTokenFactory;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
            UserRepository userRepository,
            UserSettingRepository userSettingRepository,
            FinancialProfileRepository financialProfileRepository,
            FinanceConnectionRepository financeConnectionRepository,
            AuthSessionRepository authSessionRepository,
            FinanceMemberService financeMemberService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenFactory refreshTokenFactory,
            TransactionTemplate transactionTemplate
    ) {
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.financeConnectionRepository = financeConnectionRepository;
        this.authSessionRepository = authSessionRepository;
        this.financeMemberService = financeMemberService;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenFactory = refreshTokenFactory;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 회원가입 = 자체 회원 + SSAFY 금융망 사용자.
     *
     * 금융망 호출은 트랜잭션 밖에서 먼저 하고, 그 결과를 들고 한 트랜잭션에서
     * user / user_setting / financial_profile / finance_connection을 함께 만든다.
     * 금융망 계정이 먼저 만들어지고 로컬 저장이 실패해도, 다음 가입 시 E4002→조회로 userKey를 회수하므로 안전하다.
     */
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Optional<String> financeUserKey = financeMemberService.registerOrFindUserKey(email);
        String passwordHash = passwordEncoder.encode(request.password());
        String name = request.name().trim();

        return transactionTemplate.execute(status -> {
            User user = User.createLocalUser(email, passwordHash, name, request.birthDate(), request.gender());
            try {
                // 동시 가입 요청이 existsByEmail 검사를 동시에 통과한 경우 UNIQUE 제약으로 걸러낸다.
                userRepository.saveAndFlush(user);
            } catch (DataIntegrityViolationException exception) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            userSettingRepository.save(UserSetting.defaults(user.getId()));
            financialProfileRepository.save(FinancialProfile.empty(user.getId()));
            FinanceConnection connection = financeConnectionRepository.save(
                    financeMemberService.buildConnection(user.getId(), email, financeUserKey)
            );
            return SignupResponse.of(user, connection.getStatus());
        });
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return startSession(user);
    }

    /**
     * 신원 확인이 끝난 회원에게 새 세션을 만들고 토큰을 발급한다. 이메일·비밀번호 로그인과 소셜 로그인({@link SocialAuthService})이 공유한다.
     */
    @Transactional
    public LoginResponse startSession(User user) {
        String refreshToken = refreshTokenFactory.generate();
        AuthSession session = authSessionRepository.save(AuthSession.issue(
                user.getId(),
                refreshTokenFactory.hash(refreshToken),
                tokenProvider.refreshTokenExpiresAt()
        ));
        return issueTokens(user, session, refreshToken);
    }

    /**
     * Access Token 재발급. Refresh Token은 매번 회전(rotation)하며 이전 값은 즉시 무효가 된다.
     * 세션의 절대 만료 시각은 연장하지 않는다 — 최대 14일마다 다시 로그인해야 한다.
     */
    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        LocalDateTime now = LocalDateTime.now();
        AuthSession session = authSessionRepository
                .findByRefreshTokenHash(refreshTokenFactory.hash(request.refreshToken().trim()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!session.isActiveAt(now)) {
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(session.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));

        String newRefreshToken = refreshTokenFactory.generate();
        session.rotate(refreshTokenFactory.hash(newRefreshToken), now);
        return issueTokens(user, session, newRefreshToken);
    }

    /**
     * 현재 세션만 폐기한다. 이미 폐기됐거나 없는 세션이면 그대로 성공 처리(멱등).
     */
    @Transactional
    public void logout(AuthPrincipal principal) {
        authSessionRepository.findByIdAndUserId(principal.sessionId(), principal.userId())
                .ifPresent(session -> session.revoke(LocalDateTime.now()));
    }

    /**
     * 세션 삭제. sessionId를 지정하면 그 세션만(본인 소유 확인), 지정하지 않으면 본인의 활성 세션 전체를 폐기한다.
     * 감사 목적상 행을 지우지 않고 revoked_at을 기록한다.
     */
    @Transactional
    public SessionDeleteResponse deleteSessions(AuthPrincipal principal, Long sessionId) {
        LocalDateTime now = LocalDateTime.now();
        List<AuthSession> targets;
        if (sessionId != null) {
            AuthSession session = authSessionRepository.findByIdAndUserId(sessionId, principal.userId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
            targets = List.of(session);
        } else {
            targets = authSessionRepository.findAllByUserIdAndRevokedAtIsNull(principal.userId());
        }

        int revoked = 0;
        for (AuthSession session : targets) {
            if (session.isActiveAt(now)) {
                session.revoke(now);
                revoked++;
            }
        }
        return new SessionDeleteResponse(revoked);
    }

    /**
     * Access Token은 만료 전까지 DB를 다시 보지 않으므로, 세션 조회에서는 세션 행이 아직 살아 있는지 실제로 확인한다.
     */
    @Transactional(readOnly = true)
    public SessionResponse getSession(AuthPrincipal principal) {
        User user = userRepository.findByIdAndDeletedAtIsNull(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
        AuthSession session = authSessionRepository.findByIdAndUserId(principal.sessionId(), user.getId())
                .filter(found -> found.isActiveAt(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
        return SessionResponse.of(user, session);
    }

    private LoginResponse issueTokens(User user, AuthSession session, String refreshToken) {
        IssuedAccessToken accessToken = tokenProvider.issueAccessToken(
                user.getId(),
                session.getId(),
                user.getRole()
        );
        return new LoginResponse(
                LoginResponse.TOKEN_TYPE_BEARER,
                accessToken.token(),
                accessToken.expiresAt(),
                refreshToken,
                session.getExpiresAt(),
                AuthUserResponse.from(user)
        );
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
