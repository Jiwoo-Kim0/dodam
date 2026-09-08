package com.ssafy.dodam.global.security;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserIdResolver {

    public Long resolve(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증 사용자 정보를 확인할 수 없습니다.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Number number) {
            return validate(number.longValue());
        }

        Long principalUserId = invokeUserIdAccessor(principal);
        if (principalUserId != null) {
            return validate(principalUserId);
        }

        try {
            return validate(Long.parseLong(authentication.getName()));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("인증 정보에 사용자 ID가 없습니다.", exception);
        }
    }

    private Long invokeUserIdAccessor(Object principal) {
        if (principal == null) {
            return null;
        }
        try {
            Method accessor = principal.getClass().getMethod("userId");
            Object value = accessor.invoke(principal);
            return value instanceof Number number ? number.longValue() : null;
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("인증 사용자 ID를 읽을 수 없습니다.", exception);
        }
    }

    private Long validate(long userId) {
        if (userId <= 0) {
            throw new IllegalStateException("인증 사용자 ID가 올바르지 않습니다.");
        }
        return userId;
    }
}
