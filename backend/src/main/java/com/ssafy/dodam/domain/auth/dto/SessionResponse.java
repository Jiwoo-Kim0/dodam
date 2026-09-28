package com.ssafy.dodam.domain.auth.dto;

import com.ssafy.dodam.domain.auth.entity.AuthSession;
import com.ssafy.dodam.domain.user.entity.User;
import java.time.LocalDateTime;

/**
 * 로그인 상태 조회 응답. 이 응답을 받았다는 것 자체가 Access Token이 유효하고 세션이 살아 있다는 뜻이다.
 */
public record SessionResponse(
        AuthUserResponse user,
        Long sessionId,
        LocalDateTime sessionCreatedAt,
        LocalDateTime lastAccessedAt,
        LocalDateTime sessionExpiresAt
) {

    public static SessionResponse of(User user, AuthSession session) {
        return new SessionResponse(
                AuthUserResponse.from(user),
                session.getId(),
                session.getCreatedAt(),
                session.getLastAccessedAt(),
                session.getExpiresAt()
        );
    }
}
