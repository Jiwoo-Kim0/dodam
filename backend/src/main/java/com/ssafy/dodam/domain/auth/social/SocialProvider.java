package com.ssafy.dodam.domain.auth.social;

import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.Locale;

/**
 * 소셜 로그인 공급자. user_oauth_account.provider(VARCHAR(30))에 이름 그대로 저장한다.
 */
public enum SocialProvider {
    /** 카카오 로그인. 클라이언트가 받은 access token을 서버가 사용자 정보 API로 검증한다. */
    KAKAO,
    /** Google 로그인. 클라이언트가 받은 ID token(JWT)을 서버가 Google 공개키로 검증한다. */
    GOOGLE;

    /**
     * 경로 변수(kakao, google — 대소문자 무관)를 공급자로 바꾼다. 모르는 값은 400 SOCIAL_PROVIDER_NOT_SUPPORTED.
     */
    public static SocialProvider fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_NOT_SUPPORTED);
        }
    }

    /** 공급자 이메일이 없을 때 쓰는 대체 이메일의 로컬파트 접두어. */
    public String syntheticEmailPrefix() {
        return name().toLowerCase(Locale.ROOT);
    }
}
