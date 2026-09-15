package com.ssafy.dodam.domain.auth.social;

import java.util.Locale;
import java.util.Optional;

/**
 * 공급자가 검증해 준 소셜 사용자 신원.
 *
 * @param provider       공급자
 * @param providerUserId 공급자 안에서의 사용자 ID 원문(카카오 id, 구글 sub). DB에는 해시로만 저장한다.
 * @param email          공급자가 준 이메일. 동의하지 않았거나 없으면 null
 * @param emailVerified  공급자가 이메일 소유를 확인했는지. false면 기존 계정 자동 연결에 쓰지 않는다
 * @param name           표시 이름(카카오 닉네임, 구글 name). 없으면 null
 */
public record SocialIdentity(
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String name
) {

    public SocialIdentity {
        if (provider == null) {
            throw new IllegalArgumentException("provider는 필수입니다.");
        }
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new IllegalArgumentException("providerUserId는 필수입니다.");
        }
        email = normalize(email);
    }

    /** 소유가 확인된 이메일만 돌려준다(소문자 정규화). 기존 계정 자동 연결과 신규 계정 이메일에 쓴다. */
    public Optional<String> verifiedEmail() {
        return emailVerified && email != null ? Optional.of(email) : Optional.empty();
    }

    public Optional<String> anyEmail() {
        return Optional.ofNullable(email);
    }

    private static String normalize(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() || trimmed.indexOf('@') <= 0 || trimmed.length() > 255 ? null : trimmed;
    }
}
