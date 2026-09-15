package com.ssafy.dodam.domain.auth.social;

import com.ssafy.dodam.global.exception.BusinessException;

/**
 * 공급자별 토큰 검증기. 구현은 infrastructure.external.social에 있다.
 */
public interface SocialIdentityClient {

    SocialProvider provider();

    /**
     * 클라이언트가 공급자 SDK로 받은 토큰을 검증하고 신원을 돌려준다.
     *
     * @param token 카카오는 access token, 구글은 ID token
     * @throws BusinessException SOCIAL_TOKEN_INVALID(만료·위조·다른 앱의 토큰), SOCIAL_PROVIDER_UNAVAILABLE(공급자 장애·미설정)
     */
    SocialIdentity verify(String token);
}
