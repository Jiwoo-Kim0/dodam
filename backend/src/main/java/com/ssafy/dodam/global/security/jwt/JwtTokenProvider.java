package com.ssafy.dodam.global.security.jwt;

import com.ssafy.dodam.domain.user.entity.UserRole;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.security.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * HS256 Access Token 발급·검증.
 *
 * Claim 구성: sub=userId, sid=authSession.session_id, role=USER/ADMIN.
 * sid를 넣는 이유는 로그인 상태 조회·로그아웃이 "어느 세션인지"를 알아야 하기 때문이다.
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_SESSION_ID = "sid";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final String issuer;
    private final Duration accessTokenValidity;
    private final Duration refreshTokenValidity;

    public JwtTokenProvider(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.secret().getBytes(StandardCharsets.UTF_8)
        );
        this.parser = Jwts.parser()
                .verifyWith(this.signingKey)
                .requireIssuer(properties.issuer())
                .build();
        this.issuer = properties.issuer();
        this.accessTokenValidity = properties.accessTokenValidity();
        this.refreshTokenValidity = properties.refreshTokenValidity();
    }

    public IssuedAccessToken issueAccessToken(Long userId, Long sessionId, UserRole role) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenValidity);

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim(CLAIM_SESSION_ID, sessionId)
                .claim(CLAIM_ROLE, role.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedAccessToken(token, toLocalDateTime(expiresAt));
    }

    /**
     * Refresh Token의 만료 시각. Refresh Token 자체는 JWT가 아니라 임의 난수이며
     * 해시만 auth_session에 저장한다.
     */
    public LocalDateTime refreshTokenExpiresAt() {
        return toLocalDateTime(Instant.now().plus(refreshTokenValidity));
    }

    /**
     * @throws BusinessException 만료(TOKEN_EXPIRED) 또는 서명·형식 오류(INVALID_TOKEN)
     */
    public AuthPrincipal parseAccessToken(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Long sessionId = claims.get(CLAIM_SESSION_ID, Long.class);
            String role = claims.get(CLAIM_ROLE, String.class);
            if (sessionId == null || role == null || claims.getSubject() == null) {
                throw new BusinessException(ErrorCode.INVALID_TOKEN);
            }
            return new AuthPrincipal(
                    Long.valueOf(claims.getSubject()),
                    sessionId,
                    UserRole.valueOf(role)
            );
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
