package com.ssafy.dodam.domain.recommendation.dto.request;

import jakarta.validation.constraints.Size;

/** accept/reject/complete/defer 본문. 명세 16: {@code memo?, reason?}. 공백만이면 null로 저장한다. */
public record RecommendationStatusChangeRequest(@Size(max = 500) String memo, @Size(max = 500) String reason) {
    public static final RecommendationStatusChangeRequest EMPTY = new RecommendationStatusChangeRequest(null, null);

    public String memoOrNull() {
        return blankToNull(memo);
    }

    public String reasonOrNull() {
        return blankToNull(reason);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
