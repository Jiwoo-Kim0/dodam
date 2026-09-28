package com.ssafy.dodam.global.response;

public record FieldErrorDetail(
        String field,
        String reason
) {
}
