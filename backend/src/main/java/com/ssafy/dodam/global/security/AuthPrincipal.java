package com.ssafy.dodam.global.security;

import com.ssafy.dodam.domain.user.entity.UserRole;

/**
 * Access Token에서 복원한 인증 주체. 컨트롤러는 @AuthenticationPrincipal로 받아 쓴다.
 */
public record AuthPrincipal(
        Long userId,
        Long sessionId,
        UserRole role
) {
}
