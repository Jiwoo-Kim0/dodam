package com.ssafy.dodam.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 민감 식별자 해시 정책 설정.
 *
 * <p>ERD 공통 정책에 따라 계좌번호·카드번호는 암호화 키와 분리된 HMAC 키로 해시하며,
 * 키는 DB와 소스코드 밖의 Secret으로 관리한다.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityHashProperties(String numberHashKey) {
}
