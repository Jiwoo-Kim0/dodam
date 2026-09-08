package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 2.2.1 사용자 계정 생성 / 2.2.2 사용자 계정 조회 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyMember(
        String userId,
        String userName,
        String institutionCode,
        String userKey
) {
}
