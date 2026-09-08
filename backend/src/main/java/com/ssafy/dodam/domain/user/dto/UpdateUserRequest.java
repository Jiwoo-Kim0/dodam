package com.ssafy.dodam.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 수정 허용 프로필 필드. 이메일은 금융망 계정 키라 바꿀 수 없고, 비밀번호 변경은 별도 API로 다룬다.
 */
public record UpdateUserRequest(
        @Size(min = 1, max = 100, message = "이름은 1자 이상 100자 이하여야 합니다.")
        @Pattern(regexp = "^(?!\\s*$).+", message = "이름은 공백만으로 이루어질 수 없습니다.")
        String name
) {
}
