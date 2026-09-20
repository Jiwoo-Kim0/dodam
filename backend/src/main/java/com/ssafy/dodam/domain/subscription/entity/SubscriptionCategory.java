package com.ssafy.dodam.domain.subscription.entity;

import java.util.Locale;

/**
 * 정기결제 카테고리 코드와 표시 이름. `subscription.category`는 VARCHAR(50)으로 저장되고 금융망·seed가 넣은 값이
 * 여기에 없을 수도 있어 엔티티는 문자열로 들고, 이 enum은 카테고리 목록·서비스 카탈로그·필터 정규화에만 쓴다.
 * 시연 seed(DODAM_SUBSCRIPTION_SEED.sql)는 MUSIC / VIDEO / CLOUD / BOOK을, 시연 계정용 DODAM_DEMO_SUBSCRIPTION_SEED.sql은
 * 카탈로그 카테고리(VIDEO / MUSIC / BOOK / AI / SHOPPING / DELIVERY)를 쓴다.
 */
public enum SubscriptionCategory {
    VIDEO("영상"),
    MUSIC("음악"),
    BOOK("도서"),
    CLOUD("클라우드"),
    AI("AI 서비스"),
    SHOPPING("쇼핑 멤버십"),
    DELIVERY("배달 멤버십"),
    GAME("게임"),
    EDUCATION("교육"),
    FITNESS("운동·건강"),
    TELECOM("통신"),
    NEWS("뉴스·정보"),
    ETC("기타");

    private final String label;

    SubscriptionCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 저장된 코드의 표시 이름. 정의되지 않은 코드는 코드 그대로 돌려준다. */
    public static String labelOf(String code) {
        if (code == null) {
            return null;
        }
        for (SubscriptionCategory category : values()) {
            if (category.name().equalsIgnoreCase(code.strip())) {
                return category.label;
            }
        }
        return code;
    }

    /** 필터·저장용 정규화(대문자, 공백 제거). null·빈 값은 null. */
    public static String normalize(String code) {
        return code == null || code.isBlank() ? null : code.strip().toUpperCase(Locale.ROOT);
    }
}
