package com.ssafy.dodam.domain.auth.service;

import com.ssafy.dodam.domain.auth.dto.AuthUserResponse;
import com.ssafy.dodam.domain.auth.dto.LoginRequest;
import com.ssafy.dodam.domain.auth.dto.LoginResponse;
import com.ssafy.dodam.domain.auth.dto.SessionResponse;
import com.ssafy.dodam.domain.auth.dto.SignupRequest;
import com.ssafy.dodam.domain.auth.dto.SignupResponse;
import com.ssafy.dodam.domain.auth.entity.AuthSession;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.security.AuthPrincipal;
import com.ssafy.dodam.global.security.jwt.IssuedAccessToken;
import com.ssafy.dodam.global.security.jwt.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenFactory refreshTokenFactory;

    public AuthService(
            UserRepository userRepository,
            AuthSessionRepository authSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            RefreshTokenFactory refreshTokenFactory
    ) {
        this.userRepository = userRepository;
        this.authSessionRepository = authSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenFactory = refreshTokenFactory;
    }

    /**
     * 자체 회원 가입. SSAFY 금융망 사용자 생성은 연동 방식이 확정되면 별도 단계로 붙인다.
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.createLocalUser(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim()
        );
        try {
            // 동시 가입 요청이 existsByEmail 검사를 동시에 통과한 경우 UNIQUE 제약으로 걸러낸다.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return SignupResponse.from(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizeEmail(request.email()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!user.hasPassword() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String refreshToken = refreshTokenFactory.generate();
        AuthSession session = authSessionRepository.save(AuthSession.issue(
                user.getId(),
                refreshTokenFactory.hash(refreshToken),
                tokenProvider.refreshTokenExpiresAt()
        ));
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

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
