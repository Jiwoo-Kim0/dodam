package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/**
 * 동년배 비교 축(632, `GET /patterns/me/peer-comparison?dimension=`).
 *
 * <p>표본 부족 시 확장 순서: AGE_BAND_GENDER → AGE_BAND → ALL, CLUSTER_AGE_BAND → AGE_BAND → ALL, GENDER → ALL, AGE_BAND → ALL.
 * ALL은 요청할 수 없고 최종 fallback으로만 나온다.
 */
public enum PeerDimension {
    /** 같은 나이대 */
    AGE_BAND,
    /** 같은 성별 */
    GENDER,
    /** 같은 나이대·같은 성별(기본) */
    AGE_BAND_GENDER,
    /** 내 군집 안에서 같은 나이대 */
    CLUSTER_AGE_BAND,
    /** 전체 모집단(fallback 전용) */
    ALL;

    public static final PeerDimension DEFAULT = AGE_BAND_GENDER;

    public static PeerDimension parseOrDefault(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        try {
            PeerDimension parsed = valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (parsed == ALL) {
                throw new InvalidRequestException("dimension은 AGE_BAND, GENDER, AGE_BAND_GENDER, CLUSTER_AGE_BAND 중 하나여야 합니다.");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("dimension은 AGE_BAND, GENDER, AGE_BAND_GENDER, CLUSTER_AGE_BAND 중 하나여야 합니다.");
        }
    }

    /** 표본이 부족할 때 넘어갈 다음 축. ALL이면 null(더 넓힐 곳이 없다). */
    public PeerDimension broader() {
        return switch (this) {
            case AGE_BAND_GENDER, CLUSTER_AGE_BAND -> AGE_BAND;
            case AGE_BAND, GENDER -> ALL;
            case ALL -> null;
        };
    }

    public boolean usesAgeBand() {
        return this == AGE_BAND || this == AGE_BAND_GENDER || this == CLUSTER_AGE_BAND;
    }

    public boolean usesGender() {
        return this == GENDER || this == AGE_BAND_GENDER;
    }

    public boolean usesCluster() {
        return this == CLUSTER_AGE_BAND;
    }
}
