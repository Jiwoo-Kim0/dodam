package com.ssafy.dodam.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ApiResponse<T>(
        boolean success,
        @JsonInclude(JsonInclude.Include.ALWAYS) T data,
        @JsonInclude(JsonInclude.Include.ALWAYS) String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<FieldErrorDetail> errors
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, null, message, code, null);
    }

    public static <T> ApiResponse<T> validationFailure(
            String code,
            String message,
            List<FieldErrorDetail> errors
    ) {
        return new ApiResponse<>(false, null, message, code, List.copyOf(errors));
    }
}
